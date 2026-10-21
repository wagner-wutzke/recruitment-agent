package net.wowdev.recruitment.agent.service.matching.model;

import java.util.List;

public record RankedCandidate(
    String candidateName,
    String candidateEmail,
    Double matchingProbability,
    List<String> matchedSkills,
    List<String> relevantWorkExperiences) {}
