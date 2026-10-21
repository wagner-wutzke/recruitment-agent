package net.wowdev.recruitment.agent.service.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateFinalResponse;
import net.wowdev.recruitment.agent.service.matching.model.CandidateFinalResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceBackedClaim;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceCitation;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class CandidateFinalReducer {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final ChatClient chatClient;
  private final CandidateMatchingPipelineProperties pipelineProperties;
  private final ObjectMapper objectMapper;

  @Value("${candidate.matching.final-ranking-prompt-file}")
  private Resource finalPrompt;

  public CandidateFinalReducer(
      ChatClient chatClient, CandidateMatchingPipelineProperties pipelineProperties) {
    this.chatClient = chatClient;
    this.pipelineProperties = pipelineProperties;
    this.objectMapper = new ObjectMapper();
  }

  public List<RankedCandidate> reduce(
      JobOffer offer,
      List<CandidateResumeEvidence> candidates,
      List<CandidateMapResult> mapResults) {
    List<CandidateResumeEvidence> shortlist = shortlist(candidates, mapResults);
    if (shortlist.isEmpty()) {
      return List.of();
    }

    CandidateFinalResponse response =
        chatClient
            .prompt()
            .user(finalRequest(offer, shortlist))
            .call()
            .entity(CandidateFinalResponse.class);
    validate(response, shortlist);

    Map<String, CandidateResumeEvidence> evidenceByEmail =
        shortlist.stream()
            .collect(Collectors.toMap(candidate -> normalize(candidate.candidateEmail()), c -> c));
    List<RankedCandidate> ranked = new ArrayList<>();
    for (CandidateFinalResult result : response.candidates()) {
      CandidateResumeEvidence candidate = evidenceByEmail.get(normalize(result.candidateEmail()));
      ranked.add(
          new RankedCandidate(
              candidate.candidateName(),
              candidate.candidateEmail(),
              result.matchingProbability(),
              result.matchedSkills().stream().map(EvidenceBackedClaim::text).toList(),
              result.relevantWorkExperiences().stream().map(EvidenceBackedClaim::text).toList()));
    }
    ranked.sort(
        Comparator.comparingDouble(RankedCandidate::matchingProbability)
            .reversed()
            .thenComparing(candidate -> normalize(candidate.candidateEmail())));
    return List.copyOf(ranked.subList(0, Math.min(ranked.size(), pipelineProperties.getResultLimit())));
  }

  List<CandidateResumeEvidence> shortlist(
      List<CandidateResumeEvidence> candidates, List<CandidateMapResult> mapResults) {
    Map<String, CandidateMapResult> resultByEmail = new HashMap<>();
    for (CandidateMapResult result : mapResults) {
      if (result == null
          || result.candidateEmail() == null
          || result.mapScore() == null
          || result.mapScore() < 0
          || result.mapScore() > 100
          || resultByEmail.putIfAbsent(normalize(result.candidateEmail()), result) != null) {
        throw invalidFinalResponse("map results are missing, duplicated, malformed, or outside the candidate pool");
      }
    }
    if (candidates.size() != resultByEmail.size()) {
      throw invalidFinalResponse("map results do not cover the candidate pool exactly");
    }
    for (CandidateResumeEvidence candidate : candidates) {
      if (!resultByEmail.containsKey(normalize(candidate.candidateEmail()))) {
        throw invalidFinalResponse("a candidate is missing a map result");
      }
    }

    List<CandidateResumeEvidence> sorted = new ArrayList<>(candidates);
    sorted.sort(
        Comparator.<CandidateResumeEvidence>comparingInt(
                candidate -> resultByEmail.get(normalize(candidate.candidateEmail())).mapScore())
            .reversed()
            .thenComparing(candidate -> normalize(candidate.candidateEmail())));
    return List.copyOf(
        sorted.subList(0, Math.min(sorted.size(), pipelineProperties.getShortlistSize())));
  }

  private String finalRequest(JobOffer offer, List<CandidateResumeEvidence> shortlist) {
    try {
      return finalPrompt.getContentAsString(StandardCharsets.UTF_8)
          + "\nJOB CRITERIA JSON:\n"
          + objectMapper.writeValueAsString(offer)
          + "\nSHORTLIST CANDIDATE EVIDENCE JSON:\n"
          + objectMapper.writeValueAsString(shortlist);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not prepare final candidate ranking request.", exception);
    }
  }

  void validate(CandidateFinalResponse response, List<CandidateResumeEvidence> shortlist) {
    if (response == null || response.candidates() == null) {
      throw invalidFinalResponse("missing candidates response envelope");
    }
    Map<String, CandidateResumeEvidence> allowed =
        shortlist.stream()
            .collect(Collectors.toMap(candidate -> normalize(candidate.candidateEmail()), c -> c));
    Set<String> returned = new HashSet<>();
    for (CandidateFinalResult result : response.candidates()) {
      if (result == null
          || result.candidateEmail() == null
          || result.matchingProbability() == null
          || !Double.isFinite(result.matchingProbability())
          || result.matchingProbability() < 0.0
          || result.matchingProbability() > 1.0
          || result.matchedSkills() == null
          || result.relevantWorkExperiences() == null
          || result.probabilityEvidence() == null) {
        throw invalidFinalResponse("result is missing a required field or has an invalid probability");
      }
      String email = normalize(result.candidateEmail());
      CandidateResumeEvidence candidate = allowed.get(email);
      if (candidate == null || !returned.add(email)) {
        throw invalidFinalResponse("candidate identity is outside the shortlist or duplicated");
      }
      if (result.probabilityEvidence().isEmpty()
          || !validCitations(result.probabilityEvidence(), candidate)
          || !validClaims(result.matchedSkills(), candidate)
          || !validClaims(result.relevantWorkExperiences(), candidate)) {
        throw invalidFinalResponse("claims or probability evidence do not quote the candidate's chunks");
      }
    }
  }

  private boolean validClaims(List<EvidenceBackedClaim> claims, CandidateResumeEvidence candidate) {
    return claims.stream().allMatch(claim -> claim != null && nonBlank(claim.text()) && validCitation(
        new EvidenceCitation(claim.chunkId(), claim.quote()), candidate));
  }

  private boolean validCitations(List<EvidenceCitation> citations, CandidateResumeEvidence candidate) {
    return citations.stream().allMatch(citation -> validCitation(citation, candidate));
  }

  private boolean validCitation(EvidenceCitation citation, CandidateResumeEvidence candidate) {
    if (citation == null || !nonBlank(citation.chunkId()) || !nonBlank(citation.quote())) return false;
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

  private boolean nonBlank(String value) { return value != null && !value.isBlank(); }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private IllegalStateException invalidFinalResponse(String reason) {
    return new IllegalStateException("Final candidate ranking returned invalid data: " + reason + ".");
  }
}
