package net.wowdev.recruitment.agent.dto;

import net.wowdev.recruitment.agent.service.CandidateMatch;

public record CandidateMatchResponse(String candidateName, String candidateEmail) {

  public static CandidateMatchResponse from(CandidateMatch candidateMatch) {
    return new CandidateMatchResponse(
        candidateMatch.candidateName(), candidateMatch.candidateEmail());
  }
}
