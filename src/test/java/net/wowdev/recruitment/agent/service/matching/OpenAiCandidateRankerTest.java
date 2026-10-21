package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import org.junit.jupiter.api.Test;

class OpenAiCandidateRankerTest {

  @Test
  void loadsJobOfferFromRepository() {
    JobOfferRepository repository = mock(JobOfferRepository.class);
    CandidateRankingPipeline pipeline = mock(CandidateRankingPipeline.class);
    JobOffer offer = offer();
    when(repository.findById("offer-1")).thenReturn(Optional.of(offer));
    OpenAiCandidateRanker ranker = new OpenAiCandidateRanker(repository, pipeline);

    assertEquals(offer, ranker.loadJobOffer("offer-1"));
  }

  @Test
  void rejectsMissingJobOffer() {
    JobOfferRepository repository = mock(JobOfferRepository.class);
    when(repository.findById("missing")).thenReturn(Optional.empty());
    OpenAiCandidateRanker ranker = new OpenAiCandidateRanker(repository, mock(CandidateRankingPipeline.class));

    assertThrows(NoSuchElementException.class, () -> ranker.loadJobOffer("missing"));
  }

  @Test
  void delegatesRankingToPipeline() {
    JobOfferRepository repository = mock(JobOfferRepository.class);
    CandidateRankingPipeline pipeline = mock(CandidateRankingPipeline.class);
    JobOffer offer = offer();
    List<RankedCandidate> expected = List.of(
        new RankedCandidate("Ada Lovelace", "ada@example.com", 0.95, List.of("Java"), List.of("Built systems")));
    when(pipeline.rank(offer)).thenReturn(expected);
    OpenAiCandidateRanker ranker = new OpenAiCandidateRanker(repository, pipeline);

    assertEquals(expected, ranker.rank(offer));
    verify(pipeline).rank(offer);
  }

  private static JobOffer offer() {
    return new JobOffer("offer-1", "Engineer", "Description", "Java", "Brazil", "Remote",
        "Responsibilities", null, null, "Kubernetes", null);
  }
}
