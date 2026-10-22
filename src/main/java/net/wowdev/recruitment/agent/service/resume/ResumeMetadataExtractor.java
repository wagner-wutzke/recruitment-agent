package net.wowdev.recruitment.agent.service.resume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import net.wowdev.recruitment.agent.service.resume.model.ResumeMetadata;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ResumeMetadataExtractor {

  private static final System.Logger LOGGER =
      System.getLogger(ResumeMetadataExtractor.class.getName());
  private static final int MAX_FIELD_CHARS = 500;
  private static final int MAX_SKILLS = 100;
  private static final int MAX_SKILL_CHARS = 100;
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final ChatClient chatClient;
  private final ResumeIndexingProperties properties;
  private final Resource promptResource;

  public ResumeMetadataExtractor(
      @Qualifier("resumeMetadataChatClient") ChatClient chatClient,
      ResumeIndexingProperties properties,
      @Value("${resume.indexing.metadata-extraction-prompt-file:classpath:prompts/resume-metadata-extraction.txt}") Resource promptResource) {
    this.chatClient = chatClient;
    this.properties = properties;
    this.promptResource = promptResource;
  }

  public ResumeMetadata extract(String resumeText) {
    if (!properties.isMetadataExtractionEnabled()
        || resumeText == null
        || resumeText.isBlank()
        || properties.getMetadataExtractionMaxChars() < 1) {
      return ResumeMetadata.empty();
    }

    String boundedText = boundInput(resumeText);
    FutureTask<ResumeMetadata> extraction = new FutureTask<>(() -> callModel(boundedText));
    Thread.ofVirtual().start(extraction);
    try {
      ResumeMetadata response =
          extraction.get(properties.getMetadataExtractionTimeout().toMillis(), TimeUnit.MILLISECONDS);
      return validate(response, boundedText);
    } catch (TimeoutException exception) {
      extraction.cancel(true);
      return extractionFailed();
    } catch (InterruptedException exception) {
      extraction.cancel(true);
      Thread.currentThread().interrupt();
      return extractionFailed();
    } catch (ExecutionException | RuntimeException exception) {
      return extractionFailed();
    }
  }

  String boundInput(String text) {
    int maximum = properties.getMetadataExtractionMaxChars();
    if (maximum <= 0 || text.length() <= maximum) {
      return maximum <= 0 ? "" : text;
    }
    return text.substring(0, maximum);
  }

  private ResumeMetadata callModel(String resumeText) {
    try {
      String prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
      ResumeMetadata response =
          chatClient
              .prompt()
              //.options(ChatOptions.builder().maxTokens(MAX_OUTPUT_TOKENS))
              .system(prompt)
              .user("RESUME TEXT (untrusted data):\n" + resumeText)
              .call()
              .entity(ResumeMetadata.class);
      if (response == null) {
        throw new IllegalStateException("Empty metadata response");
      }
      return response;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read resume metadata prompt.", exception);
    }
  }

  private ResumeMetadata validate(ResumeMetadata response, String sourceText) {
    if (response == null) {
      return ResumeMetadata.empty();
    }
    return new ResumeMetadata(
        groundedScalar(response.candidateName(), sourceText),
        groundedScalar(response.country(), sourceText),
        groundedScalar(response.position(), sourceText),
        groundedSkills(response.skills(), sourceText),
        groundedScalar(response.professionalExperience(), sourceText),
        groundedScalar(response.allocation(), sourceText),
        groundedScalar(response.relocation(), sourceText),
        groundedScalar(response.expectedSalary(), sourceText));
  }

  private String groundedScalar(String value, String sourceText) {
    if (value == null || value.isBlank() || value.length() > MAX_FIELD_CHARS) {
      return null;
    }
    String normalizedValue = normalize(value);
    return normalize(sourceText).contains(normalizedValue) ? value.trim() : null;
  }

  private List<String> groundedSkills(List<String> skills, String sourceText) {
    if (skills == null || skills.isEmpty()) {
      return List.of();
    }
    String normalizedSource = normalize(sourceText);
    Map<String, String> uniqueSkills = new LinkedHashMap<>();
    for (String skill : skills) {
      if (skill == null || skill.isBlank() || skill.length() > MAX_SKILL_CHARS) {
        continue;
      }
      String normalizedSkill = normalize(skill);
      if (normalizedSource.contains(normalizedSkill)) {
        uniqueSkills.putIfAbsent(normalizedSkill, skill.trim());
      }
      if (uniqueSkills.size() == MAX_SKILLS) {
        break;
      }
    }
    return List.copyOf(new ArrayList<>(uniqueSkills.values()));
  }

  private String normalize(String value) {
    return WHITESPACE.matcher(value).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
  }

  private ResumeMetadata extractionFailed() {
    LOGGER.log(
        System.Logger.Level.WARNING,
        "Resume metadata extraction failed; continuing without extracted metadata.");
    return ResumeMetadata.empty();
  }
}
