package net.wowdev.recruitment.agent.service.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.resume.ResumeIndexingProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

@Service
public class CandidateResumeRetriever {

  private final VectorStore vectorStore;
  private final CandidateMatchingProperties matchingProperties;
  private final CandidateMatchingPipelineProperties pipelineProperties;
  private final ResumeIndexingProperties resumeIndexingProperties;
  private final ObjectMapper objectMapper;

  public CandidateResumeRetriever(
      VectorStore vectorStore,
      CandidateMatchingProperties matchingProperties,
      CandidateMatchingPipelineProperties pipelineProperties,
      ResumeIndexingProperties resumeIndexingProperties) {
    this.vectorStore = vectorStore;
    this.matchingProperties = matchingProperties;
    this.pipelineProperties = pipelineProperties;
    this.resumeIndexingProperties = resumeIndexingProperties;
    this.objectMapper = new ObjectMapper();
  }

  public List<Document> retrieve(JobOffer offer) {
    String query = retrievalQuestion(offer);
    SearchRequest request =
        SearchRequest.builder()
            .query(query)
            .topK(pipelineProperties.getRetrievalTopK())
            .similarityThreshold(matchingProperties.getSimilarityThreshold())
            .build();
    List<Document> seedResults = vectorStore.similaritySearch(request);
    if (seedResults == null || seedResults.isEmpty()) {
      return List.of();
    }

    Map<String, Document> evidenceById = new LinkedHashMap<>();
    seedResults.forEach(document -> evidenceById.putIfAbsent(document.getId(), document));
    Map<String, CandidateVersion> discoveredVersions = discoverCandidateVersions(seedResults);
    for (CandidateVersion candidate : discoveredVersions.values()) {
      SearchRequest completeCandidateEvidenceRequest =
          SearchRequest.builder()
              .query(query)
              .topK(resumeIndexingProperties.getMaxNumChunks())
              .similarityThresholdAll()
              .filterExpression(candidateVersionFilter(candidate))
              .build();
      List<Document> candidateChunks = vectorStore.similaritySearch(completeCandidateEvidenceRequest);
      if (candidateChunks != null) {
        candidateChunks.forEach(document -> evidenceById.putIfAbsent(document.getId(), document));
      }
    }
    return List.copyOf(evidenceById.values());
  }

  private Map<String, CandidateVersion> discoverCandidateVersions(List<Document> documents) {
    Map<String, CandidateVersion> candidates = new LinkedHashMap<>();
    for (Document document : documents) {
      Object email = document.getMetadata().get("candidateEmail");
      Object documentId = document.getMetadata().get("documentId");
      if (email instanceof String candidateEmail
          && !candidateEmail.isBlank()
          && documentId instanceof String resumeDocumentId
          && !resumeDocumentId.isBlank()) {
        CandidateVersion candidate = new CandidateVersion(candidateEmail.trim(), resumeDocumentId.trim());
        candidates.putIfAbsent(candidate.key(), candidate);
      }
    }
    return candidates;
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

  private record CandidateVersion(String candidateEmail, String documentId) {
    private String key() {
      return candidateEmail.toLowerCase(java.util.Locale.ROOT) + "\u0000" + documentId;
    }
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
