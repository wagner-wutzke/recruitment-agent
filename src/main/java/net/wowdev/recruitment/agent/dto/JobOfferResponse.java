package net.wowdev.recruitment.agent.dto;

import net.wowdev.recruitment.agent.document.JobOffer;

public record JobOfferResponse(
    String id,
    String jobName,
    String jobDescription,
    String requiredQualifications,
    String location,
    String allocation) {

  public static JobOfferResponse from(JobOffer jobOffer) {
    return new JobOfferResponse(
        jobOffer.id(),
        jobOffer.jobName(),
        jobOffer.jobDescription(),
        jobOffer.requiredQualifications(),
        jobOffer.location(),
        jobOffer.allocation());
  }
}
