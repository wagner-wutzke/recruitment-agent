package net.wowdev.recruitment.agent.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResumeUploadService {

  private static final System.Logger LOGGER = System.getLogger(ResumeUploadService.class.getName());

  private final ResumeRepository resumeRepository;
  private final ResumeFileStorage fileStorage;
  private final ResumePdfIndexer pdfIndexer;
  private final VectorStore vectorStore;

  public ResumeUploadService(
      ResumeRepository resumeRepository,
      ResumeFileStorage fileStorage,
      ResumePdfIndexer pdfIndexer,
      VectorStore vectorStore) {
    this.resumeRepository = resumeRepository;
    this.fileStorage = fileStorage;
    this.pdfIndexer = pdfIndexer;
    this.vectorStore = vectorStore;
  }

  public ResumeUploadResult upload(String candidateName, byte[] pdfBytes) {
    UUID documentId = UUID.randomUUID();

    try (ResumeFileStorage.StoredFile storedFile =
        fileStorage.storeForUpload(candidateName, pdfBytes)) {
      ResumePdfIndexer.PreparedResume prepared =
          pdfIndexer.prepare(pdfBytes, documentId, storedFile.fileName());
      String candidateEmail = prepared.candidateEmail();
      ResumeDocument previous = resumeRepository.findById(candidateEmail).orElse(null);
      List<Document> documents = prepared.documents();
      List<String> documentIds = documents.stream().map(Document::getId).toList();
      try {
        vectorStore.add(documents);
        ResumeDocument saved =
            resumeRepository.save(
                new ResumeDocument(
                    candidateEmail,
                    candidateName,
                    storedFile.fileName(),
                    Instant.now(),
                    documentId.toString()));
        removePreviousDocuments(previous);
        storedFile.commit();
        return new ResumeUploadResult(saved, previous == null);
      } catch (RuntimeException exception) {
        rollbackIndexedDocuments(documentIds, exception);
        throw exception;
      }
    } catch (IOException exception) {
      throw new ResumeStorageException("Unable to retain the uploaded resume", exception);
    }
  }

  public ResumeDownload download(String candidateEmail) {
    ResumeDocument resume =
        resumeRepository
            .findById(candidateEmail)
            .orElseThrow(() -> new ResumeNotFoundException(candidateEmail));
    try {
      return new ResumeDownload(resume.getFileName(), fileStorage.read(resume.getFileName()));
    } catch (IOException exception) {
      throw new ResumeDownloadException("Unable to read the retained resume", exception);
    }
  }

  private void removePreviousDocuments(ResumeDocument previous) {
    if (previous == null) {
      return;
    }
    try {
      vectorStore.delete(
          new Filter.Expression(
              Filter.ExpressionType.EQ,
              new Filter.Key("documentId"),
              new Filter.Value(previous.getDocumentId())));
    } catch (RuntimeException exception) {
      LOGGER.log(
          System.Logger.Level.ERROR,
          "Unable to remove replaced resume documents for " + previous.getCandidateEmail(),
          exception);
    }
  }

  private void rollbackIndexedDocuments(List<String> documentIds, RuntimeException failure) {
    if (documentIds.isEmpty()) {
      return;
    }
    try {
      vectorStore.delete(documentIds);
    } catch (RuntimeException cleanupException) {
      failure.addSuppressed(cleanupException);
      LOGGER.log(
          System.Logger.Level.ERROR, "Unable to clean up failed resume indexing", cleanupException);
    }
  }
}
