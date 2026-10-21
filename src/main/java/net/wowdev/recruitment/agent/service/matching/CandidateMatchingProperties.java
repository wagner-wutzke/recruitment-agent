package net.wowdev.recruitment.agent.service.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "candidate.matching")
public class CandidateMatchingProperties {

  private String model = "gpt-6-luna";
  private double similarityThreshold = 0.5;
  private int topK = 10;

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Candidate matching model must not be blank");
    }
    this.model = model;
  }

  public double getSimilarityThreshold() {
    return similarityThreshold;
  }

  public void setSimilarityThreshold(double similarityThreshold) {
    if (!Double.isFinite(similarityThreshold)
        || similarityThreshold < 0.0
        || similarityThreshold > 1.0) {
      throw new IllegalArgumentException("Similarity threshold must be in the range [0,1]");
    }
    this.similarityThreshold = similarityThreshold;
  }

  public int getTopK() {
    return topK;
  }

  public void setTopK(int topK) {
    if (topK < 1) {
      throw new IllegalArgumentException("Candidate matching topK must be positive");
    }
    this.topK = topK;
  }
}
