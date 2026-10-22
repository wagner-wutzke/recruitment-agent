package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CandidateMatchingPipelinePropertiesTest {

  @Test
  void bindsDefaultsAndOverrides() {
    CandidateMatchingPipelineProperties defaults = new CandidateMatchingPipelineProperties();
    assertEquals(1200, defaults.getRetrievalTopK());
    assertEquals(100, defaults.getMaxCandidates());
    assertEquals(3, defaults.getMapBatchSize());
    assertEquals(3, defaults.getShortlistSize());
    assertEquals(3, defaults.getResultLimit());
    assertEquals(2, defaults.getMaxConcurrency());
    assertEquals(24000, defaults.getMaxEvidenceCharsPerCandidate());

    CandidateMatchingPipelineProperties configured =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "candidate.matching.pipeline.retrieval-top-k", "2400",
                        "candidate.matching.pipeline.max-candidates", "80",
                        "candidate.matching.pipeline.map-batch-size", "4",
                        "candidate.matching.pipeline.shortlist-size", "8",
                        "candidate.matching.pipeline.result-limit", "2",
                        "candidate.matching.pipeline.max-concurrency", "3",
                        "candidate.matching.pipeline.max-evidence-chars-per-candidate", "15000")))
            .bind("candidate.matching.pipeline", Bindable.of(CandidateMatchingPipelineProperties.class))
            .orElseThrow(IllegalStateException::new);
    assertEquals(2400, configured.getRetrievalTopK());
    assertEquals(80, configured.getMaxCandidates());
    assertEquals(4, configured.getMapBatchSize());
    assertEquals(8, configured.getShortlistSize());
    assertEquals(2, configured.getResultLimit());
    assertEquals(3, configured.getMaxConcurrency());
    assertEquals(15000, configured.getMaxEvidenceCharsPerCandidate());
  }

  @Test
  void rejectsOutOfRangePipelineLimits() {
    CandidateMatchingPipelineProperties properties = new CandidateMatchingPipelineProperties();

    assertThrows(IllegalArgumentException.class, () -> properties.setRetrievalTopK(0));
    assertThrows(IllegalArgumentException.class, () -> properties.setRetrievalTopK(10001));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxCandidates(0));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxCandidates(1001));
    assertThrows(IllegalArgumentException.class, () -> properties.setMapBatchSize(11));
    assertThrows(IllegalArgumentException.class, () -> properties.setShortlistSize(21));
    assertThrows(IllegalArgumentException.class, () -> properties.setResultLimit(4));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxConcurrency(9));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxEvidenceCharsPerCandidate(0));
    assertThrows(IllegalArgumentException.class, () -> properties.setMaxEvidenceCharsPerCandidate(100001));
  }
}
