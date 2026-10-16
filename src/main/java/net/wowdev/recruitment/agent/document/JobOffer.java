package net.wowdev.recruitment.agent.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "job_offers")
public record JobOffer(
    @Id String id,
    String jobName,
    String jobDescription,
    String requiredQualifications,
    String location,
    String allocation,
    String responsibilities,
    String benefits,
    String companyName,
    String niceToHave,
    String compensation) {

  public JobOffer(
      String id,
      String jobName,
      String jobDescription,
      String requiredQualifications,
      String location,
      String allocation) {
    this(
        id,
        jobName,
        jobDescription,
        requiredQualifications,
        location,
        allocation,
        null,
        null,
        null,
        null,
        null);
  }
}
