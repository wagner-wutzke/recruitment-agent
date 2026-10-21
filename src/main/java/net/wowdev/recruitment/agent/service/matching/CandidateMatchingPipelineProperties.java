package net.wowdev.recruitment.agent.service.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "candidate.matching.pipeline")
public class CandidateMatchingPipelineProperties {

  private int retrievalTopK = 120;
  private int mapBatchSize = 3;
  private int shortlistSize = 3;
  private int resultLimit = 3;
  private int maxConcurrency = 2;
  private int maxEvidenceCharsPerCandidate = 24000;

  public int getRetrievalTopK() {
    return retrievalTopK;
  }

  public void setRetrievalTopK(int retrievalTopK) {
    this.retrievalTopK = requireRange("retrieval-top-k", retrievalTopK, 1, 1000);
  }

  public int getMapBatchSize() {
    return mapBatchSize;
  }

  public void setMapBatchSize(int mapBatchSize) {
    this.mapBatchSize = requireRange("map-batch-size", mapBatchSize, 1, 10);
  }

  public int getShortlistSize() {
    return shortlistSize;
  }

  public void setShortlistSize(int shortlistSize) {
    this.shortlistSize = requireRange("shortlist-size", shortlistSize, 1, 20);
  }

  public int getResultLimit() {
    return resultLimit;
  }

  public void setResultLimit(int resultLimit) {
    this.resultLimit = requireRange("result-limit", resultLimit, 1, 3);
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public int getMaxEvidenceCharsPerCandidate() {
    return maxEvidenceCharsPerCandidate;
  }

  public void setMaxEvidenceCharsPerCandidate(int value) {
    this.maxEvidenceCharsPerCandidate = requireRange("max-evidence-chars-per-candidate", value, 1, 100000);
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = requireRange("max-concurrency", maxConcurrency, 1, 8);
  }

  private int requireRange(String property, int value, int minimum, int maximum) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(
          "candidate.matching.pipeline."
              + property
              + " must be between "
              + minimum
              + " and "
              + maximum);
    }
    return value;
  }
}
