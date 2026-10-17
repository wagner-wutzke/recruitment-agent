package net.wowdev.recruitment.agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

  public CandidateMatchingService(
      JobOfferRepository jobOfferRepository,
      ResumeRepository resumeRepository,
      VectorStore vectorStore) {
    this.jobOfferRepository = jobOfferRepository;
    this.resumeRepository = resumeRepository;
    this.vectorStore = vectorStore;
  }

  public List<CandidateMatch> findCandidates(String jobOfferId) {
    JobOffer jobOffer =
        jobOfferRepository
            .findById(jobOfferId)
            .orElseThrow(() -> new NoSuchElementException("Job offer was not found."));
    String query = searchQuery(jobOffer);
    List<Document> matches =
        vectorStore.similaritySearch(SearchRequest.builder().query(query).build());
    return resolveCandidates(matches);
  }

  private String searchQuery(JobOffer jobOffer) {
    return String.join(
        "\n",
        valueOrEmpty(jobOffer.jobDescription()),
        valueOrEmpty(jobOffer.requiredQualifications()));
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private List<CandidateMatch> resolveCandidates(List<Document> matches) {
    if (matches == null || matches.isEmpty()) {
      return List.of();
    }

    Map<String, CandidateMatch> candidates = new LinkedHashMap<>();
    for (Document match : matches) {
      String candidateEmail = candidateEmail(match);
      if (candidateEmail == null || candidates.containsKey(candidateEmail)) {
        continue;
      }
      resumeRepository
          .findById(candidateEmail)
          .map(this::toCandidateMatch)
          .ifPresent(candidate -> candidates.put(candidateEmail, candidate));
    }
    return List.copyOf(candidates.values());
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
