package net.wowdev.recruitment.agent.service.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.wowdev.recruitment.agent.service.resume.model.ResumeMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

class ResumePdfIndexerTest {

  private final VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
  private final ResumeMetadataExtractor metadataExtractor =
      org.mockito.Mockito.mock(ResumeMetadataExtractor.class);
  private final ResumePdfIndexer indexer =
      new ResumePdfIndexer(vectorStore, new ResumeIndexingProperties(), metadataExtractor);

  @BeforeEach
  void resetMocks() {
    org.mockito.Mockito.reset(vectorStore, metadataExtractor);
  }

  private static byte[] pdfWithText(String text) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        for (int start = 0; start < text.length(); start += 80) {
          if (start > 0) {
            content.newLineAtOffset(0, -14);
          }
          content.showText(text.substring(start, Math.min(start + 80, text.length())));
        }
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
    org.mockito.Mockito.verify(metadataExtractor)
        .extract(org.mockito.ArgumentMatchers.contains("candidate@example.com"));
  }

  @Test
  void attachesExtractedMetadataToEveryChunkAndPreservesSourceText() throws IOException {
    String repeatedExperience = "Java backend systems and distributed services. ".repeat(50);
    String resumeText =
        "Candidate Ada Lovelace, Java Spring Engineer with skills Java and Spring. "
            + repeatedExperience
            + " Canada hybrid work, open to relocation, expected salary CAD 120000. "
            + "candidate@example.com";
    ResumeMetadata metadata =
        new ResumeMetadata(
            "Ada Lovelace",
            "Canada",
            "Java Spring Engineer",
            List.of("Java", "Spring"),
            "Java backend systems and distributed services.",
            "hybrid work",
            "open to relocation",
            "CAD 120000");
    when(metadataExtractor.extract(org.mockito.ArgumentMatchers.anyString())).thenReturn(metadata);

    ResumePdfIndexer.PreparedResume prepared =
        indexer.prepare(pdfWithText(resumeText), UUID.randomUUID(), "Ada_Lovelace.pdf");

    assertFalse(prepared.documents().isEmpty());
    assertTrue(
        prepared.documents().stream()
            .allMatch(chunk -> "Canada".equals(chunk.getMetadata().get("country"))));
    assertTrue(
        prepared.documents().stream()
            .allMatch(chunk -> "[\"Java\",\"Spring\"]".equals(chunk.getMetadata().get("skills"))));
    assertTrue(
        prepared.documents().stream()
            .allMatch(chunk -> chunk.getMetadata().containsKey("candidateEmail")));
    assertTrue(
        prepared.documents().stream()
            .allMatch(chunk -> chunk.getMetadata().containsKey("documentId")));
    assertTrue(
        prepared.documents().stream()
            .allMatch(chunk -> chunk.getMetadata().containsKey("fileName")));
    String indexedText = prepared.documents().stream().map(Document::getText).reduce("", String::concat);
    assertTrue(indexedText.contains("distributed services"));
    assertTrue(indexedText.contains("CAD 120000"));
    org.mockito.Mockito.verify(metadataExtractor).extract(org.mockito.ArgumentMatchers.contains("Canada"));
  }

  @Test
  void extractionLimitDoesNotTruncateTextUsedForIndexing() throws IOException {
    ResumeIndexingProperties properties = new ResumeIndexingProperties();
    properties.setMetadataExtractionMaxChars(25);
    ChatClient chatClient = org.mockito.Mockito.mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
    org.mockito.Mockito.when(chatClient.prompt()).thenReturn(request);
    org.mockito.Mockito.when(request.options(org.mockito.ArgumentMatchers.any(
            org.springframework.ai.chat.prompt.ChatOptions.Builder.class)))
        .thenReturn(request);
    org.mockito.Mockito.when(request.system(org.mockito.ArgumentMatchers.anyString())).thenReturn(request);
    org.mockito.Mockito.when(request.user(org.mockito.ArgumentMatchers.anyString())).thenReturn(request);
    org.mockito.Mockito.when(request.call()).thenReturn(response);
    org.mockito.Mockito.when(response.entity(ResumeMetadata.class)).thenReturn(ResumeMetadata.empty());
    ResumeMetadataExtractor boundedExtractor =
        new ResumeMetadataExtractor(
            chatClient,
            properties,
            new org.springframework.core.io.ClassPathResource(
                "prompts/resume-metadata-extraction.txt"));
    ResumePdfIndexer limitedIndexer = new ResumePdfIndexer(vectorStore, properties, boundedExtractor);
    String resumeText =
        "candidate@example.com " + "backend engineering experience ".repeat(60) + "TAIL_MARKER";

    ResumePdfIndexer.PreparedResume prepared =
        limitedIndexer.prepare(pdfWithText(resumeText), UUID.randomUUID(), "Candidate.pdf");

    org.mockito.ArgumentCaptor<String> extractionRequest =
        org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(request).user(extractionRequest.capture());
    String sentResumeText = extractionRequest.getValue().substring(extractionRequest.getValue().indexOf('\n') + 1);
    assertEquals(25, sentResumeText.length());
    assertTrue(sentResumeText.startsWith("candidate@example.com"));
    String indexedText = prepared.documents().stream().map(Document::getText).reduce("", String::concat);
    assertTrue(indexedText.contains("TAIL_MARKER"));
  }

  @Test
  void continuesIndexingWithTrackingMetadataWhenModelExtractionFails() throws IOException {
    ChatClient chatClient = org.mockito.Mockito.mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request =
        org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
    org.mockito.Mockito.when(chatClient.prompt()).thenReturn(request);
    org.mockito.Mockito.when(request.options(org.mockito.ArgumentMatchers.any(
            org.springframework.ai.chat.prompt.ChatOptions.Builder.class)))
        .thenReturn(request);
    org.mockito.Mockito.when(request.system(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(request);
    org.mockito.Mockito.when(request.user(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(request);
    org.mockito.Mockito.when(request.call())
        .thenThrow(new IllegalStateException("model unavailable"));
    ResumeMetadataExtractor failingExtractor =
        new ResumeMetadataExtractor(
            chatClient,
            new ResumeIndexingProperties(),
            new org.springframework.core.io.ClassPathResource(
                "prompts/resume-metadata-extraction.txt"));
    ResumePdfIndexer resilientIndexer =
        new ResumePdfIndexer(vectorStore, new ResumeIndexingProperties(), failingExtractor);

    ResumePdfIndexer.PreparedResume prepared =
        resilientIndexer.prepare(
            pdfWithText("Software Engineer candidate@example.com Java experience"),
            UUID.randomUUID(),
            "Candidate.pdf");

    assertFalse(prepared.documents().isEmpty());
    assertEquals(
        "candidate@example.com",
        prepared.documents().getFirst().getMetadata().get("candidateEmail"));
    assertEquals("Candidate.pdf", prepared.documents().getFirst().getMetadata().get("fileName"));
    assertFalse(prepared.documents().getFirst().getMetadata().containsKey("country"));
    assertTrue(prepared.documents().getFirst().getText().contains("Software Engineer"));
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
  void normalizesRepeatedWhitespaceBeforeIndexing() throws IOException {
    List<String> ids =
        indexer.index(
            pdfWithText("Candidate:   first@example.com     Java   developer"),
            UUID.randomUUID(),
            "Candidate.pdf");

    org.mockito.ArgumentCaptor<List<Document>> documents =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(documents.capture());
    assertFalse(ids.isEmpty());
    String indexedText = documents.getValue().stream().map(Document::getText).reduce("", String::concat);
    assertEquals("Candidate: first@example.com Java developer", indexedText);
  }

  @Test
  void keepsEmptyDocumentTextEmptyWhenNormalizing() {
    Document empty = new Document("");

    List<Document> normalized = new ResumeWhitespaceNormalizer().apply(List.of(empty));

    assertEquals("", normalized.getFirst().getText());
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
