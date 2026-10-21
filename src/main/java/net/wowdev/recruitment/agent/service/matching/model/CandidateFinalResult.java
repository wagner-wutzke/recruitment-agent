package net.wowdev.recruitment.agent.service.matching.model;

import java.util.List;

public record CandidateFinalResult(
    String candidateEmail,
    Double matchingProbability,
    List<EvidenceBackedClaim> matchedSkills,
    List<EvidenceBackedClaim> relevantWorkExperiences,
    List<EvidenceCitation> probabilityEvidence) {}
