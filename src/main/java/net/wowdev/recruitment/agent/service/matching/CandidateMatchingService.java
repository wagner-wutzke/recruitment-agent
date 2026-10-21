package net.wowdev.recruitment.agent.service.matching;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMatch;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import org.springframework.stereotype.Service;

@Service
public class CandidateMatchingService {

  private final CandidateRanker candidateRanker;

  public CandidateMatchingService(CandidateRanker candidateRanker) {
    this.candidateRanker = candidateRanker;
  }

  public List<CandidateMatch> findCandidates(String jobOfferId) {
    JobOffer jobOffer = candidateRanker.loadJobOffer(jobOfferId);
    List<RankedCandidate> rankings = candidateRanker.rank(jobOffer);
    if (rankings == null) {
      throw new IllegalStateException("Candidate matching returned an invalid ranking response.");
    }
    return resolveRankings(rankings);
  }

  private List<CandidateMatch> resolveRankings(List<RankedCandidate> rankings) {
    List<RankedCandidate> validRankings = new ArrayList<>();
    for (RankedCandidate ranking : rankings) {
      if (ranking == null
          || ranking.candidateName() == null
          || ranking.candidateName().isBlank()
          || ranking.candidateEmail() == null
          || ranking.candidateEmail().isBlank()
          || ranking.matchedSkills() == null
          || ranking.relevantWorkExperiences() == null) {
        throw new IllegalStateException("Candidate matching returned an invalid candidate result.");
      }
      validateProbability(ranking.matchingProbability());
      validRankings.add(ranking);
    }

    validRankings.sort(Comparator.comparingDouble(RankedCandidate::matchingProbability).reversed());
    Set<String> seenEmails = new HashSet<>();
    List<CandidateMatch> matches = new ArrayList<>();
    for (RankedCandidate ranking : validRankings) {
      String normalizedEmail = ranking.candidateEmail().trim().toLowerCase(Locale.ROOT);
      if (seenEmails.add(normalizedEmail)) {
        matches.add(
            new CandidateMatch(
                ranking.candidateName(),
                ranking.candidateEmail(),
                0,
                ranking.matchingProbability(),
                ranking.matchedSkills(),
                ranking.relevantWorkExperiences()));
      }
    }
    return assignRankingPositions(matches);
  }

  private void validateProbability(Double probability) {
    if (probability == null
        || !Double.isFinite(probability)
        || probability < 0
        || probability > 1) {
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
              match.matchingProbability(),
              match.matchedSkills(),
              match.relevantWorkExperiences()));
    }
    return List.copyOf(positionedMatches);
  }
}
