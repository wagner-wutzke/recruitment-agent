package net.wowdev.recruitment.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.mongodb.uri=${MONGODB_URI:mongodb://${MONGODB_USERNAME:admin}:${MONGODB_PASSWORD:secret_password}@localhost:27017/resume_upload_integration_test?authSource=${MONGODB_AUTHENTICATION_DATABASE:admin}}",
      "spring.ai.vectorstore.chroma.collection-name=resume-upload-integration-test",
      "spring.ai.vectorstore.chroma.initialize-schema=true",
      "spring.ai.openai.api-key=test-only-not-a-real-openai-key"
    })
@AutoConfigureMockMvc
class ResumeUploadIntegrationTest {

  private static final String EMAIL = "resume-integration@example.test";

  @TempDir static Path storageDirectory;
  @Autowired private MockMvc mockMvc;
  @Autowired private ResumeRepository resumeRepository;
  @Autowired private VectorStore vectorStore;

  @DynamicPropertySource
  static void resumeStorageProperties(DynamicPropertyRegistry registry) {
    registry.add("resume.storage.directory", () -> storageDirectory.resolve("resumes").toString());
  }

  private static String jsonValue(MvcResult result, String path) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
  }

  private static byte[] pdfWithText(String text) throws Exception {
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

  private static String extractText(byte[] pdf) throws Exception {
    try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
      return new org.apache.pdfbox.text.PDFTextStripper().getText(document).trim();
    }
  }

  @AfterEach
  void cleanUpCandidateData() {
    vectorStore.delete(
        new Filter.Expression(
            Filter.ExpressionType.EQ, new Filter.Key("candidateEmail"), new Filter.Value(EMAIL)));
    resumeRepository.deleteById(EMAIL);
  }

  @Test
  void savesAndReplacesResumeAcrossHttpDatabaseFilesAndChroma() throws Exception {
    MvcResult created =
        upload("Ada Integration", pdfWithText("First resume content for Java systems"));
    assertEquals(201, created.getResponse().getStatus());
    String firstDocumentId = jsonValue(created, "$.documentId");
    assertEquals("Ada_Integration.pdf", jsonValue(created, "$.fileName"));
    assertTrue(resumeRepository.findById(EMAIL).isPresent());
    ResumeDocument savedResume = resumeRepository.findById(EMAIL).orElseThrow();
    assertEquals("Ada_Integration.pdf", savedResume.getFileName());
    assertEquals(firstDocumentId, savedResume.getDocumentId());
    assertTrue(Files.exists(storageDirectory.resolve("resumes/Ada_Integration.pdf")));
    assertCandidateDocumentExists(firstDocumentId);

    MvcResult updated =
        upload("Ada Integration", pdfWithText("Replacement resume content for Kotlin systems"));
    assertEquals(200, updated.getResponse().getStatus());
    String replacementDocumentId = jsonValue(updated, "$.documentId");
    assertNotEquals(firstDocumentId, replacementDocumentId);
    assertEquals(1, resumeRepository.count());
    assertCandidateDocumentExists(replacementDocumentId);
    assertCandidateDocumentDoesNotExist(firstDocumentId);

    byte[] downloaded =
        mockMvc
            .perform(get("/api/resumes/{candidateEmail}/file", EMAIL))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("Ada_Integration.pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertEquals("Replacement resume content for Kotlin systems", extractText(downloaded));
  }

  @Test
  void rejectsUnreadablePdfWithoutSavingRowOrRetainingFile() throws Exception {
    mockMvc
        .perform(
            multipart("/api/resumes")
                .file(
                    new MockMultipartFile(
                        "file", "bad.pdf", "application/pdf", "not a PDF".getBytes()))
                .param("candidateName", "Unreadable Candidate")
                .param("candidateEmail", EMAIL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RESUME_UPLOAD"));

    assertFalse(resumeRepository.existsById(EMAIL));
    assertFalse(Files.exists(storageDirectory.resolve("resumes/Unreadable_Candidate.pdf")));
  }

  private MvcResult upload(String candidateName, byte[] pdf) throws Exception {
    return mockMvc
        .perform(
            multipart("/api/resumes")
                .file(new MockMultipartFile("file", "original.pdf", "application/pdf", pdf))
                .param("candidateName", candidateName)
                .param("candidateEmail", EMAIL))
        .andReturn();
  }

  private void assertCandidateDocumentExists(String documentId) {
    List<Document> results = searchCandidateDocuments();
    assertTrue(
        results.stream()
            .anyMatch(
                document ->
                    EMAIL.equals(document.getMetadata().get("candidateEmail"))
                        && documentId.equals(document.getMetadata().get("documentId"))));
  }

  private void assertCandidateDocumentDoesNotExist(String documentId) {
    assertFalse(
        searchCandidateDocuments().stream()
            .anyMatch(document -> documentId.equals(document.getMetadata().get("documentId"))));
  }

  private List<Document> searchCandidateDocuments() {
    return vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("resume content candidate software systems")
            .topK(20)
            .similarityThreshold(0)
            .filterExpression(
                new Filter.Expression(
                    Filter.ExpressionType.EQ,
                    new Filter.Key("candidateEmail"),
                    new Filter.Value(EMAIL)))
            .build());
  }
}
