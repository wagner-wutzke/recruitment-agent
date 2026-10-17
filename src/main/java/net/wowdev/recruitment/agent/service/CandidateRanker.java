package net.wowdev.recruitment.agent.service;

import java.util.List;
import java.util.Map;
import net.wowdev.recruitment.agent.entity.JobOffer;

public interface CandidateRanker {

  List<String> rank(JobOffer offer, Map<String, List<String>> evidenceByReference);
}
