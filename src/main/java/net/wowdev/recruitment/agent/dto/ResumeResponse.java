package net.wowdev.recruitment.agent.dto;

import java.time.Instant;
import net.wowdev.recruitment.agent.entity.ResumeDocument;

public record ResumeResponse(
    String candidateName,
    String candidateEmail,
    String fileName,
    Instant uploadDatetime,
    String documentId) {

  public static ResumeResponse from(ResumeDocument resume) {
    return new ResumeResponse(
        resume.getCandidateName(),
        resume.getCandidateEmail(),
        resume.getFileName(),
        resume.getUploadDatetime(),
        resume.getDocumentId());
  }
}
