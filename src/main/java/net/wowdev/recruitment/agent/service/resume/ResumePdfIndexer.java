package net.wowdev.recruitment.agent.service.resume;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
public class ResumePdfIndexer {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

  private final VectorStore vectorStore;
  private final DocumentTransformer whitespaceNormalizer;
  private final TokenTextSplitter textSplitter;

  public ResumePdfIndexer(VectorStore vectorStore, ResumeIndexingProperties properties) {
    this.vectorStore = vectorStore;
    this.whitespaceNormalizer = new ResumeWhitespaceNormalizer();
    this.textSplitter =
        TokenTextSplitter.builder()
            .withChunkSize(properties.getChunkSize())
            .withMinChunkSizeChars(properties.getMinChunkSizeChars())
            .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
            .withMaxNumChunks(properties.getMaxNumChunks())
            .withKeepSeparator(properties.isKeepSeparator())
            .build();
  }

  public List<String> index(byte[] pdfBytes, UUID documentId, String fileName) {
    PreparedResume prepared = prepare(pdfBytes, documentId, fileName);
    vectorStore.add(prepared.documents());
    return prepared.documents().stream().map(Document::getId).toList();
  }

  public PreparedResume prepare(byte[] pdfBytes, UUID documentId, String fileName) {
    List<Document> pages = extractPages(pdfBytes);
    if (pages.isEmpty()
        || pages.stream().allMatch(page -> page.getText() == null || page.getText().isBlank())) {
      throw new InvalidResumePdfException("PDF contains no extractable text");
    }

    String candidateEmail = extractCandidateEmail(pages);
    List<Document> normalizedPages = whitespaceNormalizer.transform(pages);
    List<Document> chunks = textSplitter.apply(normalizedPages);
    chunks.forEach(
        chunk -> {
          chunk.getMetadata().put("candidateEmail", candidateEmail);
          chunk.getMetadata().put("documentId", documentId.toString());
          chunk.getMetadata().put("fileName", fileName);
        });
    if (chunks.isEmpty()) {
      throw new InvalidResumePdfException("PDF contains no extractable text");
    }

    return new PreparedResume(candidateEmail, chunks);
  }

  private String extractCandidateEmail(List<Document> pages) {
    String extractedText =
        pages.stream()
            .map(Document::getText)
            .filter(text -> text != null && !text.isBlank())
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    Matcher matcher = EMAIL_PATTERN.matcher(extractedText);
    if (!matcher.find()) {
      throw new InvalidResumePdfException("PDF contains no valid candidate email address");
    }
    return matcher.group();
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

  public record PreparedResume(String candidateEmail, List<Document> documents) {}
}
