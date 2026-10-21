package net.wowdev.recruitment.agent.service.matching;

import java.util.List;
import java.util.NoSuchElementException;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCandidateRanker implements CandidateRanker {

  private final JobOfferRepository jobOfferRepository;
  private final CandidateRankingPipeline rankingPipeline;

  public OpenAiCandidateRanker(
      JobOfferRepository jobOfferRepository, CandidateRankingPipeline rankingPipeline) {
    this.jobOfferRepository = jobOfferRepository;
    this.rankingPipeline = rankingPipeline;
  }

  @Override
  public JobOffer loadJobOffer(String jobOfferId) {
    return jobOfferRepository
        .findById(jobOfferId)
        .orElseThrow(() -> new NoSuchElementException("Job offer was not found."));
  }

  @Override
  public List<RankedCandidate> rank(JobOffer offer) {
    return rankingPipeline.rank(offer);
  }
}
