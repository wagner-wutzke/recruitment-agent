package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.ResumeEvidenceChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class CandidateEvidenceAssemblerTest {

  private static final String EMAIL = "candidate@example.com";
  private static final String DOCUMENT_ID = "resume-current";

  @Test
  void groupsCurrentChunksByCandidateAndUsesStoredIdentity() {
    ResumeRepository repository = mock(ResumeRepository.class);
    ResumeDocument currentResume = resume(EMAIL, DOCUMENT_ID);
    when(repository.findByCandidateEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(currentResume));
    CandidateEvidenceAssembler assembler = new CandidateEvidenceAssembler(repository, new CandidateMatchingPipelineProperties());

    List<CandidateResumeEvidence> result =
        assembler.assemble(
            List.of(
                chunk("chunk-1", EMAIL.toUpperCase(), DOCUMENT_ID, "Java services"),
                chunk("chunk-2", EMAIL, DOCUMENT_ID, "Cloud experience"),
                chunk("chunk-1", EMAIL, DOCUMENT_ID, "duplicate chunk"),
                chunk("chunk-3", EMAIL, DOCUMENT_ID, "Java services")));

    assertEquals(1, result.size());
    CandidateResumeEvidence candidate = result.getFirst();
    assertEquals(EMAIL, candidate.candidateEmail());
    assertEquals("Stored Candidate", candidate.candidateName());
    assertEquals(DOCUMENT_ID, candidate.resumeDocumentId());
    assertEquals(
        List.of(
            new ResumeEvidenceChunk("chunk-1", "Java services"),
            new ResumeEvidenceChunk("chunk-2", "Cloud experience")),
        candidate.chunks());
    verify(repository).findByCandidateEmailIgnoreCase(EMAIL);
  }

  @Test
  void excludesMissingMetadataStaleVersionsUnknownResumesAndBlankText() {
    ResumeRepository repository = mock(ResumeRepository.class);
    when(repository.findByCandidateEmailIgnoreCase(EMAIL))
        .thenReturn(Optional.of(resume(EMAIL, DOCUMENT_ID)), Optional.empty());
    CandidateEvidenceAssembler assembler = new CandidateEvidenceAssembler(repository, new CandidateMatchingPipelineProperties());

    List<CandidateResumeEvidence> result =
        assembler.assemble(
            List.of(
                new Document("missing-metadata", "Text", Map.of()),
                chunk("old-chunk", EMAIL, "old-version", "Old resume text"),
                chunk("unknown-chunk", "unknown@example.com", DOCUMENT_ID, "Unknown resume"),
                chunk("blank-chunk", EMAIL, DOCUMENT_ID, " ")));

    assertEquals(0, result.size());
  }

  @Test
  void diagnosesStaleOnlyAndRejectsMalformedMetadataAndNullDocumentFields() {
    ResumeRepository repository = mock(ResumeRepository.class);
    when(repository.findByCandidateEmailIgnoreCase(EMAIL))
        .thenReturn(Optional.of(resume(EMAIL, DOCUMENT_ID)));
    CandidateEvidenceAssembler assembler = new CandidateEvidenceAssembler(repository, new CandidateMatchingPipelineProperties());
    Document noDocumentVersion =
        new Document("no-version", "Text", Map.of("candidateEmail", EMAIL));
    Document blankEmail =
        new Document(
            "blank-email", "Text", Map.of("candidateEmail", " ", "documentId", DOCUMENT_ID));
    Document nonStringEmail =
        new Document("non-string-email", "Text", Map.of("candidateEmail", 12, "documentId", DOCUMENT_ID));
    Document nullId = mock(Document.class);
    when(nullId.getMetadata()).thenReturn(Map.of("candidateEmail", EMAIL, "documentId", DOCUMENT_ID));
    when(nullId.getId()).thenReturn(null);
    Document nullText = mock(Document.class);
    when(nullText.getMetadata()).thenReturn(Map.of("candidateEmail", EMAIL, "documentId", DOCUMENT_ID));
    when(nullText.getId()).thenReturn("null-text");
    when(nullText.getText()).thenReturn(null);
    Document stale = chunk("stale-only", EMAIL, "old-version", "Old version");

    assertEquals(
        0,
        assembler
            .assemble(List.of(noDocumentVersion, blankEmail, nonStringEmail, nullText))
            .size());
    assertEquals(0, assembler.assemble(List.of(stale)).size());
  }

  @Test
  void rejectsCandidateEvidenceAboveConfiguredCharacterLimit() {
    ResumeRepository repository = mock(ResumeRepository.class);
    when(repository.findByCandidateEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(resume(EMAIL, DOCUMENT_ID)));
    CandidateMatchingPipelineProperties properties = new CandidateMatchingPipelineProperties();
    properties.setMaxEvidenceCharsPerCandidate(5);
    CandidateEvidenceAssembler assembler = new CandidateEvidenceAssembler(repository, properties);

    assertThrows(IllegalStateException.class, () -> assembler.assemble(
        List.of(chunk("chunk-1", EMAIL, DOCUMENT_ID, "six chars"))));
  }

  @Test
  void acceptsACompleteTwentyThousandCharacterResumeWithinDefaultBudget() {
    ResumeRepository repository = mock(ResumeRepository.class);
    when(repository.findByCandidateEmailIgnoreCase(EMAIL))
        .thenReturn(Optional.of(resume(EMAIL, DOCUMENT_ID)));
    CandidateEvidenceAssembler assembler =
        new CandidateEvidenceAssembler(repository, new CandidateMatchingPipelineProperties());

    List<CandidateResumeEvidence> candidates =
        assembler.assemble(
            List.of(chunk("chunk-1", EMAIL, DOCUMENT_ID, "x".repeat(20000))));

    assertEquals(20000, candidates.getFirst().chunks().getFirst().text().length());
  }

  private static ResumeDocument resume(String email, String documentId) {
    return new ResumeDocument(email, "Stored Candidate", "Stored_Candidate.pdf", Instant.EPOCH,
        documentId);
  }

  private static Document chunk(String id, String email, String documentId, String text) {
    return new Document(
        id, text, Map.of("candidateEmail", email, "documentId", documentId));
  }
}
