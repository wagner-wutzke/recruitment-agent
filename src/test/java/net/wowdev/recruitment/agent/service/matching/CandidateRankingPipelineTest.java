package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceBackedClaim;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceCitation;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import net.wowdev.recruitment.agent.service.matching.model.ResumeEvidenceChunk;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.document.Document;

class CandidateRankingPipelineTest {

  private static final JobOffer OFFER =
      new JobOffer("offer-1", "Engineer", "Build systems", "Java", "Brazil", "Remote",
          "Reliability", null, null, "Kubernetes", null);

  @Test
  void sequencesRetrievalMappingAndFinalReduction() {
    CandidateResumeRetriever retriever = mock(CandidateResumeRetriever.class);
    CandidateEvidenceAssembler assembler = mock(CandidateEvidenceAssembler.class);
    CandidateMapEvaluator mapEvaluator = mock(CandidateMapEvaluator.class);
    CandidateFinalReducer reducer = mock(CandidateFinalReducer.class);
    Document chunk = new Document("chunk-1", "Evidence", java.util.Map.of());
    CandidateResumeEvidence evidence = evidence();
    CandidateMapResult mapResult =
        new CandidateMapResult("candidate@example.com", 90,
            List.of(new EvidenceBackedClaim("Java", "chunk-1", "resume text")),
            List.of(new EvidenceBackedClaim("Built systems", "chunk-1", "resume text")),
            List.of(new EvidenceCitation("chunk-1", "resume text")));
    RankedCandidate expected =
        new RankedCandidate("Candidate", "candidate@example.com", 0.9, List.of("Java"),
            List.of("Built systems"));
    when(retriever.retrieve(OFFER)).thenReturn(List.of(chunk));
    when(assembler.assemble(List.of(chunk))).thenReturn(List.of(evidence));
    when(mapEvaluator.evaluate(OFFER, List.of(evidence))).thenReturn(List.of(mapResult));
    when(reducer.reduce(OFFER, List.of(evidence), List.of(mapResult))).thenReturn(List.of(expected));
    CandidateRankingPipeline pipeline =
        new CandidateRankingPipeline(retriever, assembler, mapEvaluator, reducer);

    assertEquals(List.of(expected), pipeline.rank(OFFER));
    InOrder order = inOrder(retriever, assembler, mapEvaluator, reducer);
    order.verify(retriever).retrieve(OFFER);
    order.verify(assembler).assemble(List.of(chunk));
    order.verify(mapEvaluator).evaluate(OFFER, List.of(evidence));
    order.verify(reducer).reduce(OFFER, List.of(evidence), List.of(mapResult));
  }

  @Test
  void skipsModelStagesWhenNoEligibleEvidenceRemains() {
    CandidateResumeRetriever retriever = mock(CandidateResumeRetriever.class);
    CandidateEvidenceAssembler assembler = mock(CandidateEvidenceAssembler.class);
    CandidateMapEvaluator mapEvaluator = mock(CandidateMapEvaluator.class);
    CandidateFinalReducer reducer = mock(CandidateFinalReducer.class);
    Document chunk = new Document("invalid", "Evidence", java.util.Map.of());
    when(retriever.retrieve(OFFER)).thenReturn(List.of(chunk));
    when(assembler.assemble(List.of(chunk))).thenReturn(List.of());
    CandidateRankingPipeline pipeline =
        new CandidateRankingPipeline(retriever, assembler, mapEvaluator, reducer);

    assertEquals(List.of(), pipeline.rank(OFFER));
    verify(mapEvaluator, never()).evaluate(OFFER, List.of());
    verify(reducer, never()).reduce(OFFER, List.of(), List.of());
  }

  @Test
  void propagatesMapBatchFailureWithoutCallingFinalReducer() {
    CandidateResumeRetriever retriever = mock(CandidateResumeRetriever.class);
    CandidateEvidenceAssembler assembler = mock(CandidateEvidenceAssembler.class);
    CandidateMapEvaluator mapEvaluator = mock(CandidateMapEvaluator.class);
    CandidateFinalReducer reducer = mock(CandidateFinalReducer.class);
    CandidateResumeEvidence evidence = evidence();
    when(retriever.retrieve(OFFER))
        .thenReturn(List.of(new Document("chunk-1", "Evidence", java.util.Map.of())));
    when(assembler.assemble(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(evidence));
    when(mapEvaluator.evaluate(OFFER, List.of(evidence)))
        .thenThrow(new IllegalStateException("Map batch failed"));
    CandidateRankingPipeline pipeline =
        new CandidateRankingPipeline(retriever, assembler, mapEvaluator, reducer);

    assertThrows(IllegalStateException.class, () -> pipeline.rank(OFFER));
    verify(reducer, never()).reduce(OFFER, List.of(evidence), List.of());
  }

  private static CandidateResumeEvidence evidence() {
    return new CandidateResumeEvidence("candidate@example.com", "Candidate", "resume-1",
        List.of(new ResumeEvidenceChunk("chunk-1", "Evidence")));
  }
}
