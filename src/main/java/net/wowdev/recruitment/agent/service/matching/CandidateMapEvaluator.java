package net.wowdev.recruitment.agent.service.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResponse;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceBackedClaim;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceCitation;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class CandidateMapEvaluator {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final ChatClient chatClient;
  private final CandidateMatchingPipelineProperties pipelineProperties;
  private final ObjectMapper objectMapper;

  @Value("${candidate.matching.map-prompt-file}")
  private Resource mapPrompt;

  public CandidateMapEvaluator(
      ChatClient chatClient, CandidateMatchingPipelineProperties pipelineProperties) {
    this.chatClient = chatClient;
    this.pipelineProperties = pipelineProperties;
    this.objectMapper = new ObjectMapper();
  }

  public List<CandidateMapResult> evaluate(
      JobOffer offer, List<CandidateResumeEvidence> candidates) {
    if (candidates.isEmpty()) {
      return List.of();
    }
    List<List<CandidateResumeEvidence>> batches = partition(candidates);
    List<CompletableFuture<List<CandidateMapResult>>> futures;
    Semaphore concurrencyLimit = new Semaphore(pipelineProperties.getMaxConcurrency());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      futures =
          batches.stream()
              .map(
                  batch ->
                      CompletableFuture.supplyAsync(
                          () -> evaluateBatchWithinLimit(offer, batch, concurrencyLimit), executor))
              .toList();
      try {
        List<CandidateMapResult> results = new ArrayList<>();
        for (CompletableFuture<List<CandidateMapResult>> future : futures) {
          results.addAll(future.join());
        }
        return List.copyOf(results);
      } catch (CompletionException exception) {
        futures.forEach(future -> future.cancel(true));
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        throw new IllegalStateException("Candidate map evaluation failed.", cause);
      }
    }
  }

  private List<CandidateMapResult> evaluateBatchWithinLimit(
      JobOffer offer, List<CandidateResumeEvidence> batch, Semaphore concurrencyLimit) {
    boolean acquired = false;
    try {
      concurrencyLimit.acquire();
      acquired = true;
      return evaluateBatch(offer, batch);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Candidate map evaluation was interrupted.", exception);
    } finally {
      if (acquired) {
        concurrencyLimit.release();
      }
    }
  }

  List<List<CandidateResumeEvidence>> partition(List<CandidateResumeEvidence> candidates) {
    int batchSize = pipelineProperties.getMapBatchSize();
    List<List<CandidateResumeEvidence>> batches = new ArrayList<>();
    for (int start = 0; start < candidates.size(); start += batchSize) {
      batches.add(
          List.copyOf(candidates.subList(start, Math.min(start + batchSize, candidates.size()))));
    }
    return List.copyOf(batches);
  }

  List<CandidateMapResult> evaluateBatch(JobOffer offer, List<CandidateResumeEvidence> candidates) {
    CandidateMapResponse response =
        chatClient
            .prompt()
            .user(mapRequest(offer, candidates))
            .call()
            .entity(CandidateMapResponse.class);
    return validateAndSanitizeResponse(response, candidates);
  }

  private String mapRequest(JobOffer offer, List<CandidateResumeEvidence> candidates) {
    try {
      String candidatesContext = prettifyCandidatesContext(candidates);
      return mapPrompt.getContentAsString(StandardCharsets.UTF_8)
          + "\n# # # # JOB CRITERIA JSON # # # # \n"
          + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(offer)
          + "\n# # # # CANDIDATE EVIDENCE JSON # # # # \n"
          + candidatesContext;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not prepare candidate map request.", exception);
    }
  }

  private @NonNull String prettifyCandidatesContext(List<CandidateResumeEvidence> candidates)
      throws JsonProcessingException {
    String candidatesContext = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
        candidates);
    candidatesContext = candidatesContext.replace(" \\n\\n", "\n");
    candidatesContext = candidatesContext.replace(" \\n", "");
    candidatesContext = candidatesContext.replace("{\"chunkId", "\n{\"chunkId");
    return candidatesContext;
  }

  List<CandidateMapResult> validateAndSanitizeResponse(
      CandidateMapResponse response, List<CandidateResumeEvidence> candidates) {
    if (response == null || response.candidates() == null) {
      throw invalidMapResponse("missing candidates response envelope");
    }
    Map<String, CandidateResumeEvidence> expected = new HashMap<>();
    candidates.forEach(candidate -> expected.put(normalize(candidate.candidateEmail()), candidate));
    Set<String> returned = new HashSet<>();
    List<CandidateMapResult> sanitized = new ArrayList<>();
    for (CandidateMapResult result : response.candidates()) {
      if (result == null
          || result.candidateEmail() == null
          || result.mapScore() == null
          || result.mapScore() < 0
          || result.mapScore() > 100
          || result.matchedSkills() == null
          || result.relevantWorkExperiences() == null
          || result.scoreEvidence() == null) {
        throw invalidMapResponse("result is missing a required field or has an invalid score");
      }
      String candidateKey = normalize(result.candidateEmail());
      CandidateResumeEvidence candidate = expected.get(candidateKey);
      if (candidate == null || !returned.add(candidateKey)) {
        throw invalidMapResponse("candidate identity is outside the batch or duplicated");
      }
      if (result.scoreEvidence().isEmpty() || !validCitations(result.scoreEvidence(), candidate)) {
        throw invalidMapResponse(
            "score evidence is missing or does not quote the candidate's chunks");
      }
      sanitized.add(
          new CandidateMapResult(
              result.candidateEmail(),
              result.mapScore(),
              supportedClaims(result.matchedSkills(), candidate),
              supportedClaims(result.relevantWorkExperiences(), candidate),
              result.scoreEvidence()));
    }
    if (returned.size() != expected.size()) {
      throw invalidMapResponse("response does not contain exactly one result per candidate");
    }
    return List.copyOf(sanitized);
  }

  void validateResponse(CandidateMapResponse response, List<CandidateResumeEvidence> candidates) {
    validateAndSanitizeResponse(response, candidates);
  }

  private List<EvidenceBackedClaim> supportedClaims(
      List<EvidenceBackedClaim> claims, CandidateResumeEvidence candidate) {
    return claims.stream()
        .filter(
            claim ->
                claim != null
                    && nonBlank(claim.text())
                    && validCitation(
                        new EvidenceCitation(claim.chunkId(), claim.quote()), candidate))
        .toList();
  }

  private boolean validCitations(
      List<EvidenceCitation> citations, CandidateResumeEvidence candidate) {
    return citations.stream().allMatch(citation -> validCitation(citation, candidate));
  }

  private boolean validCitation(EvidenceCitation citation, CandidateResumeEvidence candidate) {
    if (citation == null || !nonBlank(citation.chunkId()) || !nonBlank(citation.quote()))
      return false;
    String normalizedQuote = normalizeWhitespace(citation.quote());
    return candidate.chunks().stream()
        .anyMatch(
            chunk ->
                chunk.chunkId().equals(citation.chunkId())
                    && normalizeWhitespace(chunk.text()).contains(normalizedQuote));
  }

  private String normalizeWhitespace(String text) {
    return WHITESPACE.matcher(text).replaceAll(" ").strip();
  }

  private boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private IllegalStateException invalidMapResponse(String reason) {
    return new IllegalStateException(
        "Candidate map evaluation returned invalid data: " + reason + ".");
  }
}
