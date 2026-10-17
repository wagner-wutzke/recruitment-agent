package net.wowdev.recruitment.agent.service;

import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
public class ResumePdfIndexer {

  private final VectorStore vectorStore;
  private final TokenTextSplitter textSplitter;

  public ResumePdfIndexer(VectorStore vectorStore, ResumeIndexingProperties properties) {
    this.vectorStore = vectorStore;
    this.textSplitter =
        TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
            .withMaxNumChunks(properties.getMaxNumChunks())
            .withKeepSeparator(properties.isKeepSeparator())
            .build();
  }

  public List<String> index(
      byte[] pdfBytes, String candidateEmail, UUID documentId, String fileName) {
    List<Document> chunks = prepare(pdfBytes, candidateEmail, documentId, fileName);
    vectorStore.add(chunks);
    return chunks.stream().map(Document::getId).toList();
  }

  public List<Document> prepare(
      byte[] pdfBytes, String candidateEmail, UUID documentId, String fileName) {
    List<Document> pages = extractPages(pdfBytes);
    if (pages.isEmpty()
        || pages.stream().allMatch(page -> page.getText() == null || page.getText().isBlank())) {
      throw new InvalidResumePdfException("PDF contains no extractable text");
    }

    List<Document> chunks = textSplitter.apply(pages);
    chunks.forEach(
        chunk -> {
          chunk.getMetadata().put("candidateEmail", candidateEmail);
          chunk.getMetadata().put("documentId", documentId.toString());
          chunk.getMetadata().put("fileName", fileName);
        });
    if (chunks.isEmpty()) {
      throw new InvalidResumePdfException("PDF contains no extractable text");
    }

    return chunks;
  }

  private List<Document> extractPages(byte[] pdfBytes) {
    if (pdfBytes == null || pdfBytes.length == 0) {
      throw new InvalidResumePdfException("PDF file is required");
    }
    try {
      return new PagePdfDocumentReader(new ByteArrayResource(pdfBytes)).read();
    } catch (RuntimeException exception) {
      throw new InvalidResumePdfException("Unable to read PDF", exception);
    }
  }
}
