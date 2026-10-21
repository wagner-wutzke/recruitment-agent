package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class CandidateMatchingPropertiesTest {

  @Test
  void usesDocumentedMatchingDefaults() {
    CandidateMatchingProperties properties = new CandidateMatchingProperties();

    assertEquals("gpt-6-luna", properties.getModel());
    assertEquals(0.5, properties.getSimilarityThreshold());
    assertEquals(10, properties.getTopK());
  }

  @Test
  void bindsMatchingOverridesFromApplicationProperties() {
    Map<String, Object> values =
        Map.of(
            "candidate.matching.model", "gpt-6.1-sol",
            "candidate.matching.similarity-threshold", "0.75",
            "candidate.matching.top-k", "8");
    CandidateMatchingProperties properties =
        new Binder(new MapConfigurationPropertySource(values))
            .bind("candidate.matching", Bindable.of(CandidateMatchingProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertEquals("gpt-6.1-sol", properties.getModel());
    assertEquals(0.75, properties.getSimilarityThreshold());
    assertEquals(8, properties.getTopK());
  }

  @Test
  void applicationPropertiesUseDocumentedMatchingDefaults() throws Exception {
    Properties applicationProperties =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

    assertEquals(
        "${CANDIDATE_MATCHING_MODEL:gpt-6-luna}",
        applicationProperties.getProperty("candidate.matching.model"));
    assertEquals(
        "${CANDIDATE_MATCHING_SIMILARITY_THRESHOLD:0.5}",
        applicationProperties.getProperty("candidate.matching.similarity-threshold"));
    assertEquals(
        "${CANDIDATE_MATCHING_TOP_K:10}",
        applicationProperties.getProperty("candidate.matching.top-k"));
  }

  @Test
  void rejectsThresholdOutsideNormalizedSimilarityRange() {
    CandidateMatchingProperties properties = new CandidateMatchingProperties();

    assertThrows(IllegalArgumentException.class, () -> properties.setSimilarityThreshold(-0.01));
    assertThrows(IllegalArgumentException.class, () -> properties.setSimilarityThreshold(1.01));
    assertThrows(
        IllegalArgumentException.class, () -> properties.setSimilarityThreshold(Double.NaN));
  }

  @Test
  void rejectsNonPositiveTopK() {
    CandidateMatchingProperties properties = new CandidateMatchingProperties();

    assertThrows(IllegalArgumentException.class, () -> properties.setTopK(0));
    assertThrows(IllegalArgumentException.class, () -> properties.setTopK(-1));
  }

  @Test
  void acceptsThresholdRangeBoundaries() {
    CandidateMatchingProperties properties = new CandidateMatchingProperties();

    properties.setSimilarityThreshold(0.0);
    assertEquals(0.0, properties.getSimilarityThreshold());

    properties.setSimilarityThreshold(1.0);
    assertEquals(1.0, properties.getSimilarityThreshold());
  }

  @Test
  void rejectsBlankModelOverride() {
    CandidateMatchingProperties properties = new CandidateMatchingProperties();

    assertThrows(IllegalArgumentException.class, () -> properties.setModel(" "));
  }
}
