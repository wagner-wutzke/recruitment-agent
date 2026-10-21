package net.wowdev.recruitment.agent.service.resume;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

/** Normalizes PDF extraction whitespace without joining words or page boundaries. */
final class ResumeWhitespaceNormalizer implements DocumentTransformer {

  private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[\\t\\x0B\\f\\r ]+");
  private static final Pattern EXCESSIVE_LINE_BREAKS = Pattern.compile("\\n{3,}");

  @Override
  public List<Document> apply(List<Document> documents) {
    return documents.stream()
        .map(
            document ->
                document.mutate()
                    .text(normalize(document.getText()))
                    .metadata(document.getMetadata())
                    .build())
        .toList();
  }

  private String normalize(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return EXCESSIVE_LINE_BREAKS
        .matcher(HORIZONTAL_WHITESPACE.matcher(text).replaceAll(" "))
        .replaceAll("\n\n")
        .strip();
  }
}
