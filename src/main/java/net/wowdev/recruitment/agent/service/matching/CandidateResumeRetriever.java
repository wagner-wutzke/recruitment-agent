package net.wowdev.recruitment.agent.service.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import net.wowdev.recruitment.agent.service.resume.ResumeIndexingProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CandidateResumeRetriever {

  private final VectorStore vectorStore;
  private final CandidateMatchingProperties matchingProperties;
  private final CandidateMatchingPipelineProperties pipelineProperties;
  private final ResumeIndexingProperties resumeIndexingProperties;
  private final ResumeRepository resumeRepository;
  private final ObjectMapper objectMapper;

  public CandidateResumeRetriever(
      VectorStore vectorStore,
      CandidateMatchingProperties matchingProperties,
      CandidateMatchingPipelineProperties pipelineProperties,
      ResumeIndexingProperties resumeIndexingProperties,
      ResumeRepository resumeRepository) {
    this.vectorStore = vectorStore;
    this.matchingProperties = matchingProperties;
    this.pipelineProperties = pipelineProperties;
    this.resumeIndexingProperties = resumeIndexingProperties;
    this.resumeRepository = resumeRepository;
    this.objectMapper = new ObjectMapper();
  }

  public List<Document> retrieve(JobOffer offer) {
    String query = retrievalQuestion(offer);
    int retrievalLimit = pipelineProperties.getRetrievalTopK();
    Filter.Expression metadataFilter = offerMetadataFilter(offer);
    List<Document> seedResults;
    if (metadataFilter == null) {
      seedResults = mergeSearchResults(search(query, retrievalLimit, null), null, retrievalLimit);
    } else {
      List<Document> constrainedResults;
      try {
        constrainedResults = search(query, retrievalLimit, metadataFilter);
      } catch (RuntimeException exception) {
        log.warn("Metadata-constrained resume search failed; retrying without metadata filters.");
        constrainedResults = List.of();
      }
      seedResults = mergeSearchResults(constrainedResults, null, retrievalLimit);
      if (seedResults.size() < retrievalLimit) {
        seedResults =
            mergeSearchResults(seedResults, search(query, retrievalLimit, null), retrievalLimit);
      }
    }
    if (seedResults == null || seedResults.isEmpty()) {
      return List.of();
    }

    List<CandidateVersion> selectedCandidates = selectCurrentCandidates(seedResults);
    Map<String, Document> evidenceById = new LinkedHashMap<>();
    for (CandidateVersion candidate : selectedCandidates) {
      SearchRequest completeCandidateEvidenceRequest =
          SearchRequest.builder()
              .query(query)
              .topK(resumeIndexingProperties.getMaxNumChunks())
              .similarityThresholdAll()
              .filterExpression(candidateVersionFilter(candidate))
              .build();
      List<Document> candidateChunks =
          vectorStore.similaritySearch(completeCandidateEvidenceRequest);
      if (candidateChunks != null) {
        candidateChunks.stream()
            .filter(document -> belongsToCandidateVersion(document, candidate))
            .forEach(document -> evidenceById.putIfAbsent(document.getId(), document));
      }
    }
    return List.copyOf(evidenceById.values());
  }

  private List<Document> search(String query, int topK, Filter.Expression filter) {
    SearchRequest.Builder request =
        SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(matchingProperties.getSimilarityThreshold());
    if (filter != null) {
      request.filterExpression(filter);
    }
    return vectorStore.similaritySearch(request.build());
  }

  private Filter.Expression offerMetadataFilter(JobOffer offer) {
    List<Filter.Expression> criteria = new ArrayList<>();
    addStringCriterion(criteria, "positionFilterValue", offer.jobName());
    addStringCriterion(criteria, "allocationFilterValue", offer.allocation());
    if (criteria.isEmpty()) {
      return null;
    }
    if (criteria.size() == 1) {
      return criteria.getFirst();
    }
    return new Filter.Expression(Filter.ExpressionType.AND, criteria.get(0), criteria.get(1));
  }

  private void addStringCriterion(
      List<Filter.Expression> criteria, String metadataKey, String offerValue) {
    if (offerValue == null || offerValue.isBlank()) {
      return;
    }
    criteria.add(
        new Filter.Expression(
            Filter.ExpressionType.EQ,
            new Filter.Key(metadataKey),
            new Filter.Value(normalizeFilterValue(offerValue))));
  }

  private String normalizeFilterValue(String value) {
    return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private List<Document> mergeSearchResults(
      List<Document> preferred, List<Document> fallback, int limit) {
    List<Document> merged = new ArrayList<>(limit);
    Set<String> seenIds = new HashSet<>();
    appendUnique(merged, seenIds, preferred, limit);
    appendUnique(merged, seenIds, fallback, limit);
    return List.copyOf(merged);
  }

  private void appendUnique(
      List<Document> destination, Set<String> seenIds, List<Document> source, int limit) {
    if (source == null) {
      return;
    }
    for (Document document : source) {
      if (destination.size() >= limit) {
        return;
      }
      if (document == null) {
        continue;
      }
      String id = document.getId();
      if (id == null || id.isBlank() || seenIds.add(id)) {
        destination.add(document);
      }
    }
  }

  private List<CandidateVersion> selectCurrentCandidates(List<Document> documents) {
    Map<String, CandidateVersion> candidates = new LinkedHashMap<>();
    Set<String> emailLookupValues = new LinkedHashSet<>();
    for (int rank = 0; rank < documents.size(); rank++) {
      Document document = documents.get(rank);
      if (document == null) {
        continue;
      }
      Object email = document.getMetadata().get("candidateEmail");
      Object documentId = document.getMetadata().get("documentId");
      if (email instanceof String candidateEmail
          && !candidateEmail.isBlank()
          && documentId instanceof String resumeDocumentId
          && !resumeDocumentId.isBlank()) {
        String displayEmail = candidateEmail.trim();
        String normalizedEmail = normalizeEmail(displayEmail);
        CandidateVersion candidate =
            new CandidateVersion(displayEmail, resumeDocumentId.trim(), document.getScore(), rank);
        candidates.merge(candidate.key(), candidate, CandidateVersion::stronger);
        emailLookupValues.add(displayEmail);
      }
    }

    if (candidates.isEmpty()) {
      return List.of();
    }

    Iterable<ResumeDocument> storedResumes = resumeRepository.findAllById(emailLookupValues);
    Map<String, ResumeDocument> currentResumeByEmail = new HashMap<>();
    for (ResumeDocument resume : storedResumes) {
      if (resume != null && resume.getCandidateEmail() != null) {
        currentResumeByEmail.put(normalizeEmail(resume.getCandidateEmail()), resume);
      }
    }

    return candidates.values().stream()
        .filter(
            candidate -> {
              ResumeDocument current =
                  currentResumeByEmail.get(normalizeEmail(candidate.candidateEmail()));
              return current != null && candidate.documentId().equals(current.getDocumentId());
            })
        .sorted(
            Comparator.comparing(
                    CandidateVersion::bestScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(
                    candidate -> candidate.bestScore() == null ? candidate.bestRank() : 0)
                .thenComparing(candidate -> normalizeEmail(candidate.candidateEmail()))
                .thenComparing(CandidateVersion::documentId))
        .limit(pipelineProperties.getMaxCandidates())
        .toList();
  }

  private Filter.Expression candidateVersionFilter(CandidateVersion candidate) {
    return new Filter.Expression(
        Filter.ExpressionType.AND,
        new Filter.Expression(
            Filter.ExpressionType.EQ,
            new Filter.Key("candidateEmail"),
            new Filter.Value(candidate.candidateEmail())),
        new Filter.Expression(
            Filter.ExpressionType.EQ,
            new Filter.Key("documentId"),
            new Filter.Value(candidate.documentId())));
  }

  private boolean belongsToCandidateVersion(Document document, CandidateVersion candidate) {
    if (document == null || document.getId() == null || document.getId().isBlank()) {
      return false;
    }
    Object email = document.getMetadata().get("candidateEmail");
    Object documentId = document.getMetadata().get("documentId");
    return email instanceof String candidateEmail
        && normalizeEmail(candidateEmail).equals(normalizeEmail(candidate.candidateEmail()))
        && documentId instanceof String resumeDocumentId
        && candidate.documentId().equals(resumeDocumentId.trim());
  }

  private record CandidateVersion(
      String candidateEmail, String documentId, Double bestScore, int bestRank) {
    private String key() {
      return normalizeEmail(candidateEmail) + "\u0000" + documentId;
    }

    private static CandidateVersion stronger(CandidateVersion left, CandidateVersion right) {
      if (left.bestScore() == null) {
        return right.bestScore() == null && left.bestRank() <= right.bestRank() ? left : right;
      }
      if (right.bestScore() == null || left.bestScore() > right.bestScore()) {
        return left;
      }
      if (right.bestScore() > left.bestScore()) {
        return right;
      }
      return left.bestRank() <= right.bestRank() ? left : right;
    }
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  String retrievalQuestion(JobOffer offer) {
    Map<String, Object> criteria = new LinkedHashMap<>();
    criteria.put("position", offer.jobName());
    criteria.put("description", offer.jobDescription());
    criteria.put("requiredQualifications", offer.requiredQualifications());
    criteria.put("responsibilities", offer.responsibilities());
    criteria.put("niceToHave", offer.niceToHave());
    criteria.put("location", offer.location());
    criteria.put("workingMode", offer.allocation());
    try {
      return "Find candidate resumes relevant to this job offer: "
          + objectMapper.writeValueAsString(criteria);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not prepare candidate retrieval question.", exception);
    }
  }
}
