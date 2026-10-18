package net.wowdev.recruitment.agent.service;

public record RankedCandidate(
    String candidateName, String candidateEmail, Double matchingProbability) {}
