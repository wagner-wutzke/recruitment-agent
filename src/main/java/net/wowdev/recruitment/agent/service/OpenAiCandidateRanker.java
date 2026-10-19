package net.wowdev.recruitment.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OpenAiCandidateRanker implements CandidateRanker {

  private final ChatClient chatClient;
  private final JobOfferRepository jobOfferRepository;
  private final VectorStore vectorStore;
  private final CandidateMatchingProperties matchingProperties;
  private final RetrievalAugmentationAdvisor retrievalAdvisor;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${candidate.matching.user-prompt-file}")
  private Resource userPrompt;

  public OpenAiCandidateRanker(
      ChatClient chatClient,
      JobOfferRepository jobOfferRepository,
      VectorStore vectorStore,
      CandidateMatchingProperties matchingProperties) {
    this.chatClient = chatClient;
    this.jobOfferRepository = jobOfferRepository;
    this.vectorStore = vectorStore;
    this.matchingProperties = matchingProperties;
    this.retrievalAdvisor = createRetrievalAdvisor();
  }

  @Override
  public JobOffer loadJobOffer(String jobOfferId) {
    return jobOfferRepository
        .findById(jobOfferId)
        .orElseThrow(() -> new NoSuchElementException("Job offer was not found."));
  }

  @Override
  public List<RankedCandidate> rank(JobOffer offer) {
    return requestRanking(offer);
  }

  private List<RankedCandidate> requestRanking(JobOffer offer) {
    CandidateRankingResponse response;
    response =
        chatClient
            .prompt()
            .advisors(retrievalAdvisor)
            .user(rankingPrompt(offer))
            .call()
            .entity(CandidateRankingResponse.class);
    if (response == null || response.rankedCandidates() == null) {
      throw new IllegalStateException("Candidate matching returned an invalid ranking response.");
    }
    for (RankedCandidate candidate : response.rankedCandidates()) {
      if (candidate == null
          || candidate.candidateName() == null
          || candidate.candidateName().isBlank()
          || candidate.candidateEmail() == null
          || candidate.candidateEmail().isBlank()) {
        throw new IllegalStateException(
            "Candidate matching returned an invalid candidate identity.");
      }
      validateProbability(candidate.matchingProbability());
    }
    return List.copyOf(response.rankedCandidates());
  }

  private RetrievalAugmentationAdvisor createRetrievalAdvisor() {
    return RetrievalAugmentationAdvisor.builder()
        .documentRetriever(
            VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(matchingProperties.getSimilarityThreshold())
                .topK(3)
                .build())
        .build();
  }

  private void validateProbability(Double probability) {
    if (probability == null
        || !Double.isFinite(probability)
        || probability < 0.0
        || probability > 1.0) {
      throw new IllegalStateException("Candidate matching returned an invalid match probability.");
    }
  }

  private String rankingPrompt(JobOffer offer) {
    Map<String, Object> criteria = new LinkedHashMap<>();
    criteria.put("position", offer.jobName());
    criteria.put("description", offer.jobDescription());
    criteria.put("requiredQualifications", offer.requiredQualifications());
    criteria.put("responsibilities", offer.responsibilities());
    criteria.put("niceToHave", offer.niceToHave());
    criteria.put("location", offer.location());
    criteria.put("workingMode", offer.allocation());
    try {
      String criteriaString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(criteria);
      String userPrompt = this.userPrompt.getContentAsString(StandardCharsets.UTF_8);
      return userPrompt + "\nJOB CRITERIA JSON:\n" + criteriaString;
    } catch (IOException e) {
      throw new IllegalStateException("Could not prepare candidate matching prompt.", e);
    }
  }
}
