package net.wowdev.recruitment.agent.dto;

import net.wowdev.recruitment.agent.entity.JobOffer;

public record JobOfferResponse(
    String id,
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

  public static JobOfferResponse from(JobOffer jobOffer) {
    return new JobOfferResponse(
        jobOffer.id(),
        jobOffer.jobName(),
        jobOffer.jobDescription(),
        jobOffer.requiredQualifications(),
        jobOffer.location(),
        jobOffer.allocation(),
        jobOffer.responsibilities(),
        jobOffer.benefits(),
        jobOffer.companyName(),
        jobOffer.niceToHave(),
        jobOffer.compensation());
  }
}
