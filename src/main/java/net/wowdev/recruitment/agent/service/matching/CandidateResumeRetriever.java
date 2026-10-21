package net.wowdev.recruitment.agent.service.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.wowdev.recruitment.agent.entity.JobOffer;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class CandidateResumeRetriever {

  private final VectorStore vectorStore;
  private final CandidateMatchingProperties matchingProperties;
  private final CandidateMatchingPipelineProperties pipelineProperties;
  private final ObjectMapper objectMapper;

  public CandidateResumeRetriever(
      VectorStore vectorStore,
      CandidateMatchingProperties matchingProperties,
      CandidateMatchingPipelineProperties pipelineProperties) {
    this.vectorStore = vectorStore;
    this.matchingProperties = matchingProperties;
    this.pipelineProperties = pipelineProperties;
    this.objectMapper = new ObjectMapper();
  }

  public List<Document> retrieve(JobOffer offer) {
    SearchRequest request =
        SearchRequest.builder()
            .query(retrievalQuestion(offer))
            .topK(pipelineProperties.getRetrievalTopK())
            .similarityThreshold(matchingProperties.getSimilarityThreshold())
            .build();
    List<Document> results = vectorStore.similaritySearch(request);
    return results == null ? List.of() : List.copyOf(results);
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
