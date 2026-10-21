package net.wowdev.recruitment.agent.service.matching;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;

public interface CandidateRanker {

  JobOffer loadJobOffer(String jobOfferId);

  List<RankedCandidate> rank(JobOffer offer);
}
