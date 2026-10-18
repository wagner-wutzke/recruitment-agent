package net.wowdev.recruitment.agent.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.springframework.stereotype.Service;

@Service
public class CandidateMatchingService {

  private final ResumeRepository resumeRepository;
  private final CandidateRanker candidateRanker;

  public CandidateMatchingService(
      ResumeRepository resumeRepository, CandidateRanker candidateRanker) {
    this.resumeRepository = resumeRepository;
    this.candidateRanker = candidateRanker;
  }

  public List<CandidateMatch> findCandidates(String jobOfferId) {
    JobOffer jobOffer = candidateRanker.loadJobOffer(jobOfferId);
    List<RankedCandidateEmail> rankings = candidateRanker.rank(jobOffer);
    if (rankings == null) {
      throw new IllegalStateException("Candidate matching returned an invalid ranking response.");
    }
    return resolveRankings(rankings);
  }

  private List<CandidateMatch> resolveRankings(List<RankedCandidateEmail> rankings) {
    Set<String> resolvedEmails = new HashSet<>();
    List<CandidateMatch> matches = new ArrayList<>();
    for (RankedCandidateEmail ranking : rankings) {
      if (ranking == null
          || ranking.candidateEmail() == null
          || ranking.candidateEmail().isBlank()) {
        throw new IllegalStateException(
            "Candidate matching returned an invalid candidate. No email provided.");
      }
      validateProbability(ranking.matchingProbability());
      if (!resolvedEmails.add(ranking.candidateEmail())) {
        continue;
      }
      resumeRepository
          .findById(ranking.candidateEmail())
          .map(resume -> toCandidateMatch(resume, ranking.matchingProbability()))
          .ifPresent(matches::add);
    }
    matches.sort(Comparator.comparingDouble(CandidateMatch::matchingProbability).reversed());
    return assignRankingPositions(matches);
  }

  private void validateProbability(double probability) {
    if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
      throw new IllegalStateException("Candidate matching returned an invalid match probability.");
    }
  }

  private List<CandidateMatch> assignRankingPositions(List<CandidateMatch> matches) {
    List<CandidateMatch> positionedMatches = new ArrayList<>();
    for (int index = 0; index < matches.size(); index++) {
      CandidateMatch match = matches.get(index);
      positionedMatches.add(
          new CandidateMatch(
              match.candidateName(),
              match.candidateEmail(),
              index + 1,
              match.matchingProbability()));
    }
    return List.copyOf(positionedMatches);
  }

  private CandidateMatch toCandidateMatch(ResumeDocument resume, double probability) {
    return new CandidateMatch(
        resume.getCandidateName(), resume.getCandidateEmail(), 0, probability);
  }
}
