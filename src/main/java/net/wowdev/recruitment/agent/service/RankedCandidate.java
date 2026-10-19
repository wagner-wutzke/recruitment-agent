package net.wowdev.recruitment.agent.service;

import java.util.List;

public record RankedCandidate(
    String candidateName,
    String candidateEmail,
    Double matchingProbability,
    List<String> matchedSkills,
    List<String> relevantWorkExperiences) {}
