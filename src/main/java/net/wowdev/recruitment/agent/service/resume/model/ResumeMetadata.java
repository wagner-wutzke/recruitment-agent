package net.wowdev.recruitment.agent.service.resume.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Structured metadata extracted from resume text; it does not establish candidate identity. */
public record ResumeMetadata(
    String candidateName,
    String country,
    String position,
    List<String> skills,
    String professionalExperience,
    String allocation,
    String relocation,
    String expectedSalary) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public ResumeMetadata {
    skills = skills == null ? List.of() : skills.stream().filter(Objects::nonNull).toList();
  }

  public static ResumeMetadata empty() {
    return new ResumeMetadata(null, null, null, List.of(), null, null, null, null);
  }

  public Map<String, Object> toVectorStoreMetadata() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfPresent(metadata, "candidateName", candidateName);
    putIfPresent(metadata, "country", country);
    putIfPresent(metadata, "position", position);
    putNormalizedIfPresent(metadata, "positionFilterValue", position);
    putIfPresent(metadata, "professionalExperience", professionalExperience);
    putIfPresent(metadata, "allocation", allocation);
    putNormalizedIfPresent(metadata, "allocationFilterValue", allocation);
    putIfPresent(metadata, "relocation", relocation);
    putIfPresent(metadata, "expectedSalary", expectedSalary);
    try {
      metadata.put("skills", OBJECT_MAPPER.writeValueAsString(skills));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize resume skills metadata.", exception);
    }
    return Map.copyOf(metadata);
  }

  private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
    if (value != null && !value.isBlank()) {
      metadata.put(key, value);
    }
  }

  private static void putNormalizedIfPresent(
      Map<String, Object> metadata, String key, String value) {
    if (value != null && !value.isBlank()) {
      metadata.put(
          key,
          WHITESPACE.matcher(value).replaceAll(" ").trim().toLowerCase(Locale.ROOT));
    }
  }
}
