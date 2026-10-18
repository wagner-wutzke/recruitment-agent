package net.wowdev.recruitment.agent.service;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;

public interface CandidateRanker {

  JobOffer loadJobOffer(String jobOfferId);

  List<RankedCandidate> rank(JobOffer offer);
}
