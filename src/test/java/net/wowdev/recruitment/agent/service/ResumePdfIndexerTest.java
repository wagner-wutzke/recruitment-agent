package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class ResumePdfIndexerTest {

  private final VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
  private final ResumePdfIndexer indexer =
      new ResumePdfIndexer(vectorStore, new ResumeIndexingProperties());

  private static byte[] pdfWithText(String text) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText(text);
        content.endText();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] emptyPdf() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      document.save(output);
      return output.toByteArray();
    }
  }

  @Test
  void extractsSplitsAndIndexesPdfWithTrackingMetadata() throws IOException {
    UUID documentId = UUID.randomUUID();
    List<String> ids =
        indexer.index(
            pdfWithText("Resume text for local indexing candidate@example.com"),
            documentId,
            "Ada_Lovelace.pdf");

    org.mockito.ArgumentCaptor<List<Document>> documents =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(documents.capture());
    assertFalse(ids.isEmpty());
    assertEquals(
        "candidate@example.com",
        documents.getValue().getFirst().getMetadata().get("candidateEmail"));
    assertEquals(
        documentId.toString(), documents.getValue().getFirst().getMetadata().get("documentId"));
    assertEquals("Ada_Lovelace.pdf", documents.getValue().getFirst().getMetadata().get("fileName"));
  }

  @Test
  void extractsFirstEmailInPdfTextOrder() throws IOException {
    ResumePdfIndexer.PreparedResume prepared =
        indexer.prepare(
            pdfWithText("Candidate: first@example.com Reference: other@example.com"),
            UUID.randomUUID(),
            "Candidate.pdf");

    assertEquals("first@example.com", prepared.candidateEmail());
    assertEquals(
        "first@example.com", prepared.documents().getFirst().getMetadata().get("candidateEmail"));
  }

  @Test
  void rejectsPdfWithoutValidEmailAddress() throws IOException {
    assertThrows(
        InvalidResumePdfException.class,
        () ->
            indexer.prepare(
                pdfWithText("Resume text without contact address"),
                UUID.randomUUID(),
                "resume.pdf"));
    verifyNoInteractions(vectorStore);
  }

  @Test
  void rejectsInvalidPdfWithoutIndexing() {
    assertThrows(
        InvalidResumePdfException.class,
        () -> indexer.index("not a PDF".getBytes(), UUID.randomUUID(), "resume.pdf"));

    verifyNoInteractions(vectorStore);
  }

  @Test
  void rejectsPdfWithoutExtractableText() throws IOException {
    assertThrows(
        InvalidResumePdfException.class,
        () -> indexer.index(emptyPdf(), UUID.randomUUID(), "resume.pdf"));

    verifyNoInteractions(vectorStore);
  }

  @Test
  void rejectsMissingPdf() {
    assertThrows(
        InvalidResumePdfException.class,
        () -> indexer.index(new byte[0], UUID.randomUUID(), "resume.pdf"));

    verifyNoInteractions(vectorStore);
  }

  @Test
  void rejectsNullPdf() {
    assertThrows(
        InvalidResumePdfException.class,
        () -> indexer.index(null, UUID.randomUUID(), "resume.pdf"));

    verifyNoInteractions(vectorStore);
  }
}
