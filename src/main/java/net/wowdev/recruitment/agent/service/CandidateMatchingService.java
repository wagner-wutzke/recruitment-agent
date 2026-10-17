package net.wowdev.recruitment.agent.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class CandidateMatchingService {

  private static final String CANDIDATE_EMAIL_METADATA = "candidateEmail";

  private final JobOfferRepository jobOfferRepository;
  private final ResumeRepository resumeRepository;
  private final VectorStore vectorStore;
  private final CandidateMatchingProperties candidateMatchingProperties;
  private final CandidateRanker candidateRanker;

  public CandidateMatchingService(
      JobOfferRepository jobOfferRepository,
      ResumeRepository resumeRepository,
      VectorStore vectorStore,
      CandidateMatchingProperties candidateMatchingProperties,
      CandidateRanker candidateRanker) {
    this.jobOfferRepository = jobOfferRepository;
    this.resumeRepository = resumeRepository;
    this.vectorStore = vectorStore;
    this.candidateMatchingProperties = candidateMatchingProperties;
    this.candidateRanker = candidateRanker;
  }

  public List<CandidateMatch> findCandidates(String jobOfferId) {
    JobOffer jobOffer =
        jobOfferRepository
            .findById(jobOfferId)
            .orElseThrow(() -> new NoSuchElementException("Job offer was not found."));
    String query = searchQuery(jobOffer);
    List<Document> matches =
        vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .similarityThreshold(candidateMatchingProperties.getSimilarityThreshold())
                .build());
    return rankAndResolve(jobOffer, matches);
  }

  private String searchQuery(JobOffer jobOffer) {
    return String.join(
        "\n",
        valueOrEmpty(jobOffer.jobName()),
        valueOrEmpty(jobOffer.jobDescription()),
        valueOrEmpty(jobOffer.requiredQualifications()),
        valueOrEmpty(jobOffer.responsibilities()),
        valueOrEmpty(jobOffer.niceToHave()));
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private List<CandidateMatch> rankAndResolve(JobOffer offer, List<Document> matches) {
    if (matches == null || matches.isEmpty()) {
      return List.of();
    }

    Map<String, List<String>> evidenceByEmail = new LinkedHashMap<>();
    for (Document match : matches) {
      String candidateEmail = candidateEmail(match);
      if (candidateEmail == null) {
        continue;
      }
      evidenceByEmail.computeIfAbsent(candidateEmail, ignored -> new ArrayList<>()).add(match.getText());
    }
    if (evidenceByEmail.isEmpty()) {
      return List.of();
    }

    Map<String, String> emailByReference = new LinkedHashMap<>();
    Map<String, List<String>> evidenceByReference = new LinkedHashMap<>();
    int index = 1;
    for (Map.Entry<String, List<String>> entry : evidenceByEmail.entrySet()) {
      String reference = "candidate-" + index++;
      emailByReference.put(reference, entry.getKey());
      evidenceByReference.put(reference, entry.getValue());
    }

    List<String> rankedReferences = candidateRanker.rank(offer, evidenceByReference);

    Set<String> resolvedEmails = new HashSet<>();
    List<CandidateMatch> rankedCandidates = new ArrayList<>();
    for (String reference : rankedReferences) {
      String email = emailByReference.get(reference);
      if (email == null || !resolvedEmails.add(email)) {
        continue;
      }
      resumeRepository.findById(email).map(this::toCandidateMatch).ifPresent(rankedCandidates::add);
    }
    return List.copyOf(rankedCandidates);
  }

  private String candidateEmail(Document document) {
    Object value = document.getMetadata().get(CANDIDATE_EMAIL_METADATA);
    if (!(value instanceof String candidateEmail) || candidateEmail.isBlank()) {
      return null;
    }
    return candidateEmail;
  }

  private CandidateMatch toCandidateMatch(ResumeDocument resume) {
    return new CandidateMatch(resume.getCandidateName(), resume.getCandidateEmail());
  }
}
