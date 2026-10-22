package net.wowdev.recruitment.agent.service.resume;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resume.indexing")
public class ResumeIndexingProperties {

  private int chunkSize = 800;
  private int minChunkSizeChars = 350;
  private int minChunkLengthToEmbed = 20;
  private int maxNumChunks = 100;
  private boolean keepSeparator = true;
  private boolean metadataExtractionEnabled = true;
  private int metadataExtractionMaxChars = 20000;
  private Duration metadataExtractionTimeout = Duration.ofSeconds(30);
  private String metadataExtractionPromptFile =
      "classpath:prompts/resume-metadata-extraction.txt";

  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  public int getMinChunkSizeChars() {
    return minChunkSizeChars;
  }

  public void setMinChunkSizeChars(int minChunkSizeChars) {
    this.minChunkSizeChars = minChunkSizeChars;
  }

  public int getMinChunkLengthToEmbed() {
    return minChunkLengthToEmbed;
  }

  public void setMinChunkLengthToEmbed(int minChunkLengthToEmbed) {
    this.minChunkLengthToEmbed = minChunkLengthToEmbed;
  }

  public int getMaxNumChunks() {
    return maxNumChunks;
  }

  public void setMaxNumChunks(int maxNumChunks) {
    this.maxNumChunks = maxNumChunks;
  }

  public boolean isKeepSeparator() {
    return keepSeparator;
  }

  public void setKeepSeparator(boolean keepSeparator) {
    this.keepSeparator = keepSeparator;
  }

  public boolean isMetadataExtractionEnabled() {
    return metadataExtractionEnabled;
  }

  public void setMetadataExtractionEnabled(boolean metadataExtractionEnabled) {
    this.metadataExtractionEnabled = metadataExtractionEnabled;
  }

  public int getMetadataExtractionMaxChars() {
    return metadataExtractionMaxChars;
  }

  public void setMetadataExtractionMaxChars(int metadataExtractionMaxChars) {
    if (metadataExtractionMaxChars < 1) {
      throw new IllegalArgumentException("resume.indexing.metadata-extraction-max-chars must be positive");
    }
    this.metadataExtractionMaxChars = metadataExtractionMaxChars;
  }

  public Duration getMetadataExtractionTimeout() {
    return metadataExtractionTimeout;
  }

  public void setMetadataExtractionTimeout(Duration metadataExtractionTimeout) {
    if (metadataExtractionTimeout == null || metadataExtractionTimeout.isZero() || metadataExtractionTimeout.isNegative()) {
      throw new IllegalArgumentException("resume.indexing.metadata-extraction-timeout must be positive");
    }
    this.metadataExtractionTimeout = metadataExtractionTimeout;
  }

  public String getMetadataExtractionPromptFile() {
    return metadataExtractionPromptFile;
  }

  public void setMetadataExtractionPromptFile(String metadataExtractionPromptFile) {
    if (metadataExtractionPromptFile == null || metadataExtractionPromptFile.isBlank()) {
      throw new IllegalArgumentException("resume.indexing.metadata-extraction-prompt-file is required");
    }
    this.metadataExtractionPromptFile = metadataExtractionPromptFile;
  }
}
