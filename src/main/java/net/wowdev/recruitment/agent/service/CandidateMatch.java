package net.wowdev.recruitment.agent.service;

import java.util.List;

public record CandidateMatch(
    String candidateName,
    String candidateEmail,
    int rankingPosition,
    double matchingProbability,
    List<String> matchedSkills,
    List<String> relevantWorkExperiences) {}
