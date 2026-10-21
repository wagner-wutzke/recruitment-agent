package net.wowdev.recruitment.agent.service.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ResumeIndexingPropertiesTest {

  @Test
  void usesDocumentedDefaultSplitterSettings() {
    ResumeIndexingProperties properties = new ResumeIndexingProperties();

    assertEquals(800, properties.getChunkSize());
    assertEquals(350, properties.getMinChunkSizeChars());
    assertEquals(20, properties.getMinChunkLengthToEmbed());
    assertEquals(100, properties.getMaxNumChunks());
    assertTrue(properties.isKeepSeparator());
  }

  @Test
  void bindsCustomSplitterSettings() {
    Map<String, Object> values =
        Map.of(
            "resume.indexing.chunk-size", "512",
            "resume.indexing.min-chunk-size-chars", "240",
            "resume.indexing.min-chunk-length-to-embed", "12",
            "resume.indexing.max-num-chunks", "4000",
            "resume.indexing.keep-separator", "false");
    ResumeIndexingProperties properties =
        new Binder(new MapConfigurationPropertySource(values))
            .bind("resume.indexing", Bindable.of(ResumeIndexingProperties.class))
            .orElseThrow(IllegalStateException::new);

    assertEquals(512, properties.getChunkSize());
    assertEquals(240, properties.getMinChunkSizeChars());
    assertEquals(12, properties.getMinChunkLengthToEmbed());
    assertEquals(4000, properties.getMaxNumChunks());
    assertFalse(properties.isKeepSeparator());
  }
}
