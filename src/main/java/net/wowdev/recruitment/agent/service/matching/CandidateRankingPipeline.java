package net.wowdev.recruitment.agent.service.matching;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import org.springframework.stereotype.Service;

@Service
public class CandidateRankingPipeline {

  private final CandidateResumeRetriever retriever;
  private final CandidateEvidenceAssembler evidenceAssembler;
  private final CandidateMapEvaluator mapEvaluator;
  private final CandidateFinalReducer finalReducer;

  public CandidateRankingPipeline(
      CandidateResumeRetriever retriever,
      CandidateEvidenceAssembler evidenceAssembler,
      CandidateMapEvaluator mapEvaluator,
      CandidateFinalReducer finalReducer) {
    this.retriever = retriever;
    this.evidenceAssembler = evidenceAssembler;
    this.mapEvaluator = mapEvaluator;
    this.finalReducer = finalReducer;
  }

  public List<RankedCandidate> rank(JobOffer offer) {
    List<CandidateResumeEvidence> evidence =
        evidenceAssembler.assemble(retriever.retrieve(offer));
    if (evidence.isEmpty()) {
      return List.of();
    }
    List<CandidateMapResult> mapResults = mapEvaluator.evaluate(offer, evidence);
    if (mapResults.isEmpty()) {
      return List.of();
    }
    return finalReducer.reduce(offer, evidence, mapResults);
  }
}
