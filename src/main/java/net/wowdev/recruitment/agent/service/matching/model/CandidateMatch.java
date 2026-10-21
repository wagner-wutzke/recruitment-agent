package net.wowdev.recruitment.agent.service.matching.model;

import java.util.List;

public record CandidateMatch(
    String candidateName,
    String candidateEmail,
    int rankingPosition,
    double matchingProbability,
    List<String> matchedSkills,
    List<String> relevantWorkExperiences) {}
