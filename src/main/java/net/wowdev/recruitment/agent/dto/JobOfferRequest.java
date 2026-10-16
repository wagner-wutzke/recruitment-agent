package net.wowdev.recruitment.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import net.wowdev.recruitment.agent.document.JobOffer;

public record JobOfferRequest(
    @NotBlank String jobName,
    @NotBlank String jobDescription,
    @NotBlank String requiredQualifications,
    @NotBlank String location,
    @NotNull @Pattern(regexp = "Remote|Hybrid|On-site") String allocation) {

  public JobOffer toDocument(String id) {
    return new JobOffer(id, jobName, jobDescription, requiredQualifications, location, allocation);
  }
}
