package net.wowdev.recruitment.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCandidateRanker implements CandidateRanker {

  private static final String CANDIDATE_EMAIL_METADATA = "candidateEmail";

  private final ChatClient chatClient;
  private final JobOfferRepository jobOfferRepository;
  private final VectorStore vectorStore;
  private final CandidateMatchingProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private Advisor ragAdvisor;

  public OpenAiCandidateRanker(
      ChatClient chatClient,
      JobOfferRepository jobOfferRepository,
      VectorStore vectorStore,
      CandidateMatchingProperties properties) {
    this.chatClient = chatClient;
    this.jobOfferRepository = jobOfferRepository;
    this.vectorStore = vectorStore;
    this.properties = properties;

    ragAdvisor =
        QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(
                SearchRequest.builder()
                    .similarityThreshold(0.45)
                    .topK(3)
                    .build())
            .build();
  }

  @Override
  public JobOffer loadJobOffer(String jobOfferId) {
    return jobOfferRepository
        .findById(jobOfferId)
        .orElseThrow(() -> new NoSuchElementException("Job offer was not found."));
  }

  @Override
  public List<RankedCandidateEmail> rank(JobOffer offer) {
    //    RetrievedEvidence retrievedEvidence = retrieveEvidence(offer);
    //    if (retrievedEvidence.evidenceByReference().isEmpty()) {
    //      return List.of();
    //    }
    //    List<RankedCandidateReference> references =
    //        requestRanking(offer, retrievedEvidence.evidenceByReference());
    //    return resolveReferences(references, retrievedEvidence.emailByReference());
    return requestRanking(offer);
  }

  private RetrievedEvidence retrieveEvidence(JobOffer offer) {
    List<Document> matches =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(searchQuery(offer))
                .similarityThreshold(properties.getSimilarityThreshold())
                .build());
    Map<String, List<String>> evidenceByReference = new LinkedHashMap<>();
    Map<String, String> emailByReference = new LinkedHashMap<>();
    Map<String, String> referenceByEmail = new LinkedHashMap<>();
    for (Document match : matches) {
      String email = candidateEmail(match);
      if (email == null) {
        continue;
      }
      String reference = referenceByEmail.get(email);
      if (reference == null) {
        reference = "candidate-" + (referenceByEmail.size() + 1);
        referenceByEmail.put(email, reference);
        emailByReference.put(reference, email);
      }
      evidenceByReference
          .computeIfAbsent(reference, ignored -> new ArrayList<>())
          .add(match.getText());
    }
    return new RetrievedEvidence(evidenceByReference, emailByReference);
  }

  private List<RankedCandidateEmail> requestRanking(JobOffer offer) {
    CandidateRankingResponse response =
        chatClient
            .prompt()
            .user(rankingPrompt(offer))
            .advisors(ragAdvisor)
            .call()
            .entity(CandidateRankingResponse.class);
    if (response == null || response.rankedCandidates() == null) {
      throw new IllegalStateException("Candidate matching returned an invalid ranking response.");
    }
    return response.rankedCandidates();
  }

  private List<RankedCandidateEmail> resolveReferences(
      List<RankedCandidateReference> rankings, Map<String, String> emailByReference) {
    Set<String> resolvedEmails = new HashSet<>();
    List<RankedCandidateEmail> accepted = new ArrayList<>();
    for (RankedCandidateReference ranking : rankings) {
      if (ranking == null) {
        throw new IllegalStateException("Candidate matching returned an invalid ranking response.");
      }
      validateProbability(ranking.matchingProbability());
      String email = emailByReference.get(ranking.candidateReference());
      if (email == null || !resolvedEmails.add(email)) {
        continue;
      }
      accepted.add(new RankedCandidateEmail(email, ranking.matchingProbability()));
    }
    return List.copyOf(accepted);
  }

  private void validateProbability(Double probability) {
    if (probability == null
        || !Double.isFinite(probability)
        || probability < 0
        || probability > 1) {
      throw new IllegalStateException("Candidate matching returned an invalid match probability.");
    }
  }

  private String candidateEmail(Document document) {
    Object value = document.getMetadata().get(CANDIDATE_EMAIL_METADATA);
    if (!(value instanceof String email) || email.isBlank()) {
      return null;
    }
    return email;
  }

  private String searchQuery(JobOffer offer) {
    return String.join(
        "\n",
        valueOrEmpty(offer.jobName()),
        valueOrEmpty(offer.jobDescription()),
        valueOrEmpty(offer.requiredQualifications()),
        valueOrEmpty(offer.responsibilities()),
        valueOrEmpty(offer.niceToHave()));
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
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
      return "Compare semantic meaning and skills in the job criteria against the existing "
          + "candidates. Return only matching candidates, ordered by relevance. "
          + "Consider position, technical skills, location, working mode and professional "
          + "experiences. Do not infer unsupported facts."
          + "\nJOB CRITERIA JSON:\n"
          + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(criteria);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not prepare candidate matching prompt.", exception);
    }
  }

  private record RetrievedEvidence(
      Map<String, List<String>> evidenceByReference, Map<String, String> emailByReference) {}
}
