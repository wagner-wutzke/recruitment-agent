package net.wowdev.recruitment.agent.service.matching.model;

import java.util.List;

public record CandidateResumeEvidence(
    String candidateEmail,
    String candidateName,
    String resumeDocumentId,
    List<ResumeEvidenceChunk> chunks) {

  public CandidateResumeEvidence {
    chunks = List.copyOf(chunks);
  }
}
