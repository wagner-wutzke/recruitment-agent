package net.wowdev.recruitment.agent.service;

public record CandidateMatch(
    String candidateName, String candidateEmail, int rankingPosition, double matchingProbability) {}
