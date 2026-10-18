package net.wowdev.recruitment.agent.dto;

import net.wowdev.recruitment.agent.service.CandidateMatch;

public record CandidateMatchResponse(
    String candidateName, String candidateEmail, int rankingPosition, double matchingProbability) {

  public static CandidateMatchResponse from(CandidateMatch candidateMatch) {
    return new CandidateMatchResponse(
        candidateMatch.candidateName(),
        candidateMatch.candidateEmail(),
        candidateMatch.rankingPosition(),
        candidateMatch.matchingProbability());
  }
}
