package net.wowdev.recruitment.agent.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.mongodb.authentication-database=admin",
      "spring.mongodb.host=localhost",
      "spring.mongodb.port=27017",
      "spring.mongodb.database=resume_repository_test",
      "spring.mongodb.username=admin",
      "spring.mongodb.password=secret_password",
      "spring.ai.openai.api-key=test-only-not-a-real-openai-key"
    })
class ResumeRepositoryTest {

  private static final String EMAIL = "candidate@example.test";

  @Autowired private ResumeRepository resumeRepository;

  @AfterEach
  void cleanUp() {
    resumeRepository.deleteById(EMAIL);
  }

  @Test
  void persistsResumeMetadataUsingCandidateEmailAsMongoId() {
    String documentId = UUID.randomUUID().toString();
    Instant uploadedAt = Instant.parse("2026-10-16T14:30:00Z");
    ResumeDocument resume =
        new ResumeDocument(EMAIL, "Candidate One", "resume.pdf", uploadedAt, documentId);

    resumeRepository.save(resume);

    ResumeDocument saved = resumeRepository.findById(EMAIL).orElseThrow();
    assertEquals(EMAIL, saved.getCandidateEmail());
    assertEquals("Candidate One", saved.getCandidateName());
    assertEquals("resume.pdf", saved.getFileName());
    assertEquals(uploadedAt, saved.getUploadDatetime());
    assertEquals(documentId, saved.getDocumentId());
    assertEquals(1, resumeRepository.count());
  }

  @Test
  void savingSameCandidateEmailReplacesExistingMongoDocument() {
    resumeRepository.save(
        new ResumeDocument(
            EMAIL,
            "Candidate One",
            "old.pdf",
            Instant.parse("2026-10-15T14:30:00Z"),
            UUID.randomUUID().toString()));
    String replacementDocumentId = UUID.randomUUID().toString();

    resumeRepository.save(
        new ResumeDocument(
            EMAIL,
            "Candidate Updated",
            "new.pdf",
            Instant.parse("2026-10-16T14:30:00Z"),
            replacementDocumentId));

    assertTrue(resumeRepository.findById(EMAIL).isPresent());
    ResumeDocument updated = resumeRepository.findById(EMAIL).orElseThrow();
    assertEquals("Candidate Updated", updated.getCandidateName());
    assertEquals("new.pdf", updated.getFileName());
    assertEquals(replacementDocumentId, updated.getDocumentId());
    assertEquals(1, resumeRepository.count());
  }

  @Test
  void findsCurrentResumeUsingCaseInsensitiveCandidateEmail() {
    String documentId = UUID.randomUUID().toString();
    resumeRepository.save(
        new ResumeDocument(
            EMAIL,
            "Candidate One",
            "resume.pdf",
            Instant.parse("2026-10-16T14:30:00Z"),
            documentId));

    ResumeDocument found = resumeRepository.findByCandidateEmailIgnoreCase(EMAIL.toUpperCase()).orElseThrow();

    assertEquals(EMAIL, found.getCandidateEmail());
    assertEquals(documentId, found.getDocumentId());
  }
}
