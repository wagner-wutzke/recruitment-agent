package net.wowdev.recruitment.agent.service.joboffer;

import java.util.List;
import java.util.NoSuchElementException;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import org.springframework.stereotype.Service;

@Service
public class JobOfferService {

  private final JobOfferRepository jobOfferRepository;

  public JobOfferService(JobOfferRepository jobOfferRepository) {
    this.jobOfferRepository = jobOfferRepository;
  }

  public List<JobOffer> findAll() {
    return jobOfferRepository.findAll();
  }

  public JobOffer create(JobOffer jobOffer) {
    return jobOfferRepository.save(jobOffer);
  }

  public JobOffer update(String id, JobOffer jobOffer) {
    if (!jobOfferRepository.existsById(id)) {
      throw new NoSuchElementException("Job offer not found");
    }
    return jobOfferRepository.save(
        new JobOffer(
            id,
            jobOffer.jobName(),
            jobOffer.jobDescription(),
            jobOffer.requiredQualifications(),
            jobOffer.location(),
            jobOffer.allocation(),
            jobOffer.responsibilities(),
            jobOffer.benefits(),
            jobOffer.companyName(),
            jobOffer.niceToHave(),
            jobOffer.compensation()));
  }
}
