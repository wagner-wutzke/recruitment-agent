package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

class ResumeUploadServiceTest {

  private static final String EMAIL = "candidate@example.com";
  private static final String CANDIDATE_NAME = "Ada Lovelace";

  @TempDir Path tempDirectory;

  @Test
  void savesMetadataAndReplacesOldIndexedContent() throws IOException {
    Instant uploadStartedAt = Instant.now().minusSeconds(1);
    ResumeRepository repository = mock(ResumeRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ResumeFileStorage fileStorage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    ResumePdfIndexer indexer = indexer(vectorStore);
    ResumeDocument previous =
        new ResumeDocument(
            EMAIL,
            CANDIDATE_NAME,
            "Ada_Lovelace.pdf",
            Instant.parse("2026-10-15T10:00:00Z"),
            UUID.randomUUID());
    fileStorage.store(CANDIDATE_NAME, "old PDF".getBytes());
    when(repository.findById(EMAIL)).thenReturn(Optional.of(previous));
    when(repository.save(org.mockito.ArgumentMatchers.any(ResumeDocument.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ResumeDocument saved =
        new ResumeUploadService(repository, fileStorage, indexer, vectorStore)
            .upload(CANDIDATE_NAME, EMAIL, pdfWithText("Updated resume information"))
            .resume();

    assertEquals(EMAIL, saved.getCandidateEmail());
    assertEquals("Ada_Lovelace.pdf", saved.getFileName());
    assertTrue(saved.getUploadDatetime().isAfter(uploadStartedAt));
    assertEquals(
        pdfText(Files.readAllBytes(tempDirectory.resolve("resumes/Ada_Lovelace.pdf"))),
        "Updated resume information");
    InOrder order = inOrder(vectorStore, repository);
    order.verify(vectorStore).add(anyList());
    order.verify(repository).save(org.mockito.ArgumentMatchers.any(ResumeDocument.class));
    org.mockito.ArgumentCaptor<Filter.Expression> filterCaptor =
        org.mockito.ArgumentCaptor.forClass(Filter.Expression.class);
    verify(vectorStore).delete(filterCaptor.capture());
    assertEquals("documentId", ((Filter.Key) filterCaptor.getValue().left()).key());
    assertEquals(previous.getDocumentId().toString(), ((Filter.Value) filterCaptor.getValue().right()).value());
  }

  @Test
  void restoresPreviousFileAndRemovesNewVectorsWhenMetadataSaveFails() throws IOException {
    ResumeRepository repository = mock(ResumeRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ResumeFileStorage fileStorage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    ResumePdfIndexer indexer = indexer(vectorStore);
    fileStorage.store(CANDIDATE_NAME, "previous PDF".getBytes());
    ResumeDocument previous =
        new ResumeDocument(
            EMAIL,
            CANDIDATE_NAME,
            "Ada_Lovelace.pdf",
            Instant.parse("2026-10-15T10:00:00Z"),
            UUID.randomUUID());
    when(repository.findById(EMAIL)).thenReturn(Optional.of(previous));
    doThrow(new IllegalStateException("database unavailable"))
        .when(repository)
        .save(org.mockito.ArgumentMatchers.any(ResumeDocument.class));
    ResumeUploadService service = new ResumeUploadService(repository, fileStorage, indexer, vectorStore);

    assertThrows(
        IllegalStateException.class,
        () -> service.upload(CANDIDATE_NAME, EMAIL, pdfWithText("New resume information")));

    assertEquals(
        "previous PDF", Files.readString(tempDirectory.resolve("resumes/Ada_Lovelace.pdf")));
    verify(vectorStore).delete(anyList());
  }

  @Test
  void restoresPreviousFileAndCleansPartialIndexWhenChromaAddFails() throws IOException {
    ResumeRepository repository = mock(ResumeRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ResumeFileStorage fileStorage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    ResumePdfIndexer indexer = indexer(vectorStore);
    fileStorage.store(CANDIDATE_NAME, "previous PDF".getBytes());
    when(repository.findById(EMAIL)).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("Chroma unavailable")).when(vectorStore).add(anyList());
    ResumeUploadService service = new ResumeUploadService(repository, fileStorage, indexer, vectorStore);

    assertThrows(
        IllegalStateException.class,
        () -> service.upload(CANDIDATE_NAME, EMAIL, pdfWithText("New resume information")));

    assertEquals(
        "previous PDF", Files.readString(tempDirectory.resolve("resumes/Ada_Lovelace.pdf")));
    verify(vectorStore).delete(anyList());
    verify(repository, never()).save(org.mockito.ArgumentMatchers.any(ResumeDocument.class));
  }

  @Test
  void returnsSuccessAndLogsWhenOldVectorCleanupFails() throws IOException {
    ResumeRepository repository = mock(ResumeRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ResumeFileStorage fileStorage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    ResumePdfIndexer indexer = indexer(vectorStore);
    ResumeDocument previous =
        new ResumeDocument(
            EMAIL,
            CANDIDATE_NAME,
            "Ada_Lovelace.pdf",
            Instant.parse("2026-10-15T10:00:00Z"),
            UUID.randomUUID());
    when(repository.findById(EMAIL)).thenReturn(Optional.of(previous));
    when(repository.save(org.mockito.ArgumentMatchers.any(ResumeDocument.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(new IllegalStateException("Chroma delete failed"))
        .when(vectorStore)
        .delete(org.mockito.ArgumentMatchers.any(Filter.Expression.class));

    ResumeUploadResult result =
        new ResumeUploadService(repository, fileStorage, indexer, vectorStore)
            .upload(CANDIDATE_NAME, EMAIL, pdfWithText("Replacement resume content"));

    assertEquals(false, result.created());
    assertEquals(EMAIL, result.resume().getCandidateEmail());
  }

  @Test
  void preservesOriginalFailureWhenVectorRollbackAlsoFails() throws IOException {
    ResumeRepository repository = mock(ResumeRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ResumeFileStorage fileStorage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    ResumePdfIndexer indexer = indexer(vectorStore);
    when(repository.findById(EMAIL)).thenReturn(Optional.empty());
    doThrow(new IllegalStateException("database unavailable"))
        .when(repository)
        .save(org.mockito.ArgumentMatchers.any(ResumeDocument.class));
    doThrow(new IllegalStateException("Chroma delete failed")).when(vectorStore).delete(anyList());
    ResumeUploadService service = new ResumeUploadService(repository, fileStorage, indexer, vectorStore);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> service.upload(CANDIDATE_NAME, EMAIL, pdfWithText("New resume information")));

    assertEquals(1, failure.getSuppressed().length);
  }

  @Test
  void mapsFilesystemFailuresToResumeStorageException() throws IOException {
    ResumeRepository repository = mock(ResumeRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    Path notDirectory = tempDirectory.resolve("file");
    Files.writeString(notDirectory, "not a directory");
    ResumeFileStorage fileStorage = new ResumeFileStorage(notDirectory.resolve("resumes"));
    when(repository.findById(EMAIL)).thenReturn(Optional.empty());
    ResumeUploadService service = new ResumeUploadService(repository, fileStorage, indexer(vectorStore), vectorStore);

    assertThrows(
        ResumeStorageException.class,
        () -> service.upload(CANDIDATE_NAME, EMAIL, pdfWithText("Resume information")));
    verifyNoInteractions(vectorStore);
  }

  @Test
  void downloadsStoredPdfForResumeEmail() throws IOException {
    ResumeRepository repository = mock(ResumeRepository.class);
    ResumeFileStorage fileStorage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    byte[] pdf = "stored PDF".getBytes();
    fileStorage.store(CANDIDATE_NAME, pdf);
    ResumeDocument resume =
        new ResumeDocument(
            EMAIL,
            CANDIDATE_NAME,
            "Ada_Lovelace.pdf",
            Instant.parse("2026-10-15T10:00:00Z"),
            UUID.randomUUID());
    when(repository.findById(EMAIL)).thenReturn(Optional.of(resume));
    ResumeUploadService service =
        new ResumeUploadService(repository, fileStorage, mock(ResumePdfIndexer.class), mock(VectorStore.class));

    ResumeDownload downloaded = service.download(EMAIL);

    assertEquals("Ada_Lovelace.pdf", downloaded.fileName());
    org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, downloaded.contents());
  }

  @Test
  void rejectsDownloadForUnknownResumeEmail() {
    ResumeRepository repository = mock(ResumeRepository.class);
    when(repository.findById(EMAIL)).thenReturn(Optional.empty());
    ResumeUploadService service =
        new ResumeUploadService(
            repository,
            new ResumeFileStorage(tempDirectory.resolve("resumes")),
            mock(ResumePdfIndexer.class),
            mock(VectorStore.class));

    assertThrows(ResumeNotFoundException.class, () -> service.download(EMAIL));
  }

  @Test
  void mapsMissingRetainedFileToStorageFailure() {
    ResumeRepository repository = mock(ResumeRepository.class);
    when(repository.findById(EMAIL))
        .thenReturn(
            Optional.of(
                new ResumeDocument(
                    EMAIL,
                    CANDIDATE_NAME,
                    "missing.pdf",
                    Instant.parse("2026-10-15T10:00:00Z"),
                    UUID.randomUUID())));
    ResumeUploadService service =
        new ResumeUploadService(
            repository,
            new ResumeFileStorage(tempDirectory.resolve("resumes")),
            mock(ResumePdfIndexer.class),
            mock(VectorStore.class));

    assertThrows(ResumeDownloadException.class, () -> service.download(EMAIL));
  }

  private static byte[] pdfWithText(String text) throws IOException {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
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

  private static String pdfText(byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new org.apache.pdfbox.text.PDFTextStripper().getText(document).trim();
    }
  }

  private static ResumePdfIndexer indexer(VectorStore vectorStore) {
    return new ResumePdfIndexer(vectorStore, new ResumeIndexingProperties());
  }
}
