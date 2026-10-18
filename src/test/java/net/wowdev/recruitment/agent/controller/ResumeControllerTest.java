package net.wowdev.recruitment.agent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.service.InvalidResumePdfException;
import net.wowdev.recruitment.agent.service.ResumeDownload;
import net.wowdev.recruitment.agent.service.ResumeDownloadException;
import net.wowdev.recruitment.agent.service.ResumeNotFoundException;
import net.wowdev.recruitment.agent.service.ResumeStorageException;
import net.wowdev.recruitment.agent.service.ResumeUploadResult;
import net.wowdev.recruitment.agent.service.ResumeUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class ResumeControllerTest {

  private static final String NAME = "Ada Lovelace";
  private static final String EMAIL = "ada@example.com";

  private final ResumeUploadService uploadService = mock(ResumeUploadService.class);
  private MockMvc mockMvc;

  private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
      validMultipart() {
    return multipart("/api/resumes")
        .file(pdfFile())
        .param("candidateName", NAME);
  }

  private static MockMultipartFile pdfFile() {
    return new MockMultipartFile("file", "resume.pdf", "application/pdf", "%PDF-sample".getBytes());
  }

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(new ResumeController(uploadService)).build();
  }

  @Test
  void createsResumeAndReturnsSavedMetadata() throws Exception {
    UUID id = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-10-16T10:00:00Z");
    when(uploadService.upload(eq(NAME), any()))
        .thenReturn(
            new ResumeUploadResult(
                new ResumeDocument(EMAIL, NAME, "Ada_Lovelace.pdf", uploadedAt, id), true));

    mockMvc
        .perform(validMultipart())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.candidateName").value(NAME))
        .andExpect(jsonPath("$.candidateEmail").value(EMAIL))
        .andExpect(jsonPath("$.fileName").value("Ada_Lovelace.pdf"))
        .andExpect(jsonPath("$.uploadDatetime").value("2026-10-16T10:00:00Z"))
        .andExpect(jsonPath("$.documentId").value(id.toString()));

    verify(uploadService).upload(eq(NAME), any());
  }

  @Test
  void updatesExistingResumeWithOkStatus() throws Exception {
    when(uploadService.upload(eq(NAME), any()))
        .thenReturn(
            new ResumeUploadResult(
                new ResumeDocument(
                    EMAIL,
                    NAME,
                    "Ada_Lovelace.pdf",
                    Instant.parse("2026-10-16T10:00:00Z"),
                    UUID.randomUUID()),
                false));

    mockMvc.perform(validMultipart()).andExpect(status().isOk());
  }

  @Test
  void rejectsMissingCandidateName() throws Exception {
    mockMvc
        .perform(multipart("/api/resumes").file(pdfFile()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RESUME_UPLOAD"));
  }

  @Test
  void rejectsMissingFile() throws Exception {
    mockMvc
        .perform(multipart("/api/resumes").param("candidateName", NAME))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RESUME_UPLOAD"));
  }

  @Test
  void rejectsBlankCandidateFieldsAndEmptyFile() throws Exception {
    mockMvc
        .perform(
            multipart("/api/resumes")
                .file(pdfFile())
                .param("candidateName", " "))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart("/api/resumes")
                .file(new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]))
                .param("candidateName", NAME))
        .andExpect(status().isBadRequest());
    verify(uploadService, never()).upload(eq(NAME), any());
  }

  @Test
  void rejectsUnreadablePdf() throws Exception {
    when(uploadService.upload(eq(NAME), any()))
        .thenThrow(new InvalidResumePdfException("Unable to read PDF"));

    mockMvc
        .perform(validMultipart())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RESUME_UPLOAD"));
  }

  @Test
  void mapsMultipartReadFailureToBadRequest() throws Exception {
    MockMultipartFile unreadableFile =
        new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] {1}) {
          @Override
          public byte[] getBytes() throws IOException {
            throw new IOException("read failed");
          }
        };

    mockMvc
        .perform(
            multipart("/api/resumes")
                .file(unreadableFile)
                .param("candidateName", NAME))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RESUME_UPLOAD"));
  }

  @Test
  void returnsServiceUnavailableWhenIndexOrPersistenceFails() throws Exception {
    when(uploadService.upload(eq(NAME), any()))
        .thenThrow(new IllegalStateException("dependency unavailable"));

    mockMvc
        .perform(validMultipart())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("RESUME_UPLOAD_FAILED"));
  }

  @Test
  void returnsServiceUnavailableWhenResumeFileStorageFails() throws Exception {
    when(uploadService.upload(eq(NAME), any()))
        .thenThrow(new ResumeStorageException("write failed", new IOException("disk full")));

    mockMvc
        .perform(validMultipart())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("RESUME_UPLOAD_FAILED"));
  }

  @Test
  void downloadsRetainedResumeAsPdfAttachment() throws Exception {
    byte[] pdf = "%PDF-download".getBytes();
    when(uploadService.download(EMAIL)).thenReturn(new ResumeDownload("Ada_Lovelace.pdf", pdf));

    mockMvc
        .perform(get("/api/resumes/{candidateEmail}/file", EMAIL))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("Ada_Lovelace.pdf")))
        .andExpect(content().bytes(pdf));
  }

  @Test
  void returnsNotFoundWhenResumeDoesNotExist() throws Exception {
    when(uploadService.download(EMAIL)).thenThrow(new ResumeNotFoundException(EMAIL));

    mockMvc
        .perform(get("/api/resumes/{candidateEmail}/file", EMAIL))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESUME_NOT_FOUND"));
  }

  @Test
  void returnsServiceUnavailableWhenRetainedFileCannotBeRead() throws Exception {
    when(uploadService.download(EMAIL))
        .thenThrow(new ResumeDownloadException("read failed", new IOException("not found")));

    mockMvc
        .perform(get("/api/resumes/{candidateEmail}/file", EMAIL))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("RESUME_DOWNLOAD_FAILED"));
  }
}
