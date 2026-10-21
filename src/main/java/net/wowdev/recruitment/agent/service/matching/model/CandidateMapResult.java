package net.wowdev.recruitment.agent.service.matching.model;

import java.util.List;

public record CandidateMapResult(
    String candidateEmail,
    Integer mapScore,
    List<EvidenceBackedClaim> matchedSkills,
    List<EvidenceBackedClaim> relevantWorkExperiences,
    List<EvidenceCitation> scoreEvidence) {}
