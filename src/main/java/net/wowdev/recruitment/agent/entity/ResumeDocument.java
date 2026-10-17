package net.wowdev.recruitment.agent.entity;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "resumes")
public class ResumeDocument {

  @Id private String candidateEmail;

  private String candidateName;
  private String fileName;
  private Instant uploadDatetime;
  private String documentId;

  protected ResumeDocument() {}

  public ResumeDocument(
      String candidateEmail,
      String candidateName,
      String fileName,
      Instant uploadDatetime,
      String documentId) {
    this.candidateEmail = candidateEmail;
    this.candidateName = candidateName;
    this.fileName = fileName;
    this.uploadDatetime = uploadDatetime;
    this.documentId = documentId;
  }

  public ResumeDocument(
      String candidateEmail,
      String candidateName,
      String fileName,
      Instant uploadDatetime,
      UUID documentId) {
    this(candidateEmail, candidateName, fileName, uploadDatetime, documentId.toString());
  }

  public String getCandidateEmail() {
    return candidateEmail;
  }

  public String getCandidateName() {
    return candidateName;
  }

  public String getFileName() {
    return fileName;
  }

  public Instant getUploadDatetime() {
    return uploadDatetime;
  }

  public String getDocumentId() {
    return documentId;
  }
}
