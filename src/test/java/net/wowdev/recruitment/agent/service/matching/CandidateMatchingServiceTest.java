package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMatch;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import org.junit.jupiter.api.Test;

class CandidateMatchingServiceTest {

  private static final String OFFER_ID = "offer-123";

  private static JobOffer jobOffer() {
    return new JobOffer(
        OFFER_ID,
        "Platform Engineer",
        "Build reliable Java services",
        "Java and distributed systems",
        "Remote",
        "Remote");
  }

  private static RankedCandidate candidate(String name, String email, double probability) {
    return new RankedCandidate(name, email, probability, List.of(), List.of());
  }

  private static CandidateMatchingService service(CandidateRanker ranker) {
    return new CandidateMatchingService(ranker);
  }

  @Test
  void delegatesOfferLoadingAndRankingAndReturnsSortedModelIdentities() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    org.mockito.Mockito.doReturn(jobOffer()).when(ranker).loadJobOffer(OFFER_ID);
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(
                candidate("Ada Lovelace", "ada@example.com", 0.81),
                candidate("Grace Hopper", "grace@example.com", 0.92)));

    assertEquals(
        List.of(
            new CandidateMatch("Grace Hopper", "grace@example.com", 1, 0.92, List.of(), List.of()),
            new CandidateMatch("Ada Lovelace", "ada@example.com", 2, 0.81, List.of(), List.of())),
        service(ranker).findCandidates(OFFER_ID));
    verify(ranker).loadJobOffer(OFFER_ID);
    verify(ranker).rank(jobOffer());
  }

  @Test
  void deduplicatesByEmailAfterSortingAndKeepsTheHighestProbability() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    doReturn(jobOffer()).when(ranker).loadJobOffer(OFFER_ID);
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(
                candidate("Ada Old", "ada@example.com", 0.61),
                candidate("Grace Hopper", "grace@example.com", 0.7),
                candidate("Ada Lovelace", "ada@example.com", 0.91)));

    assertEquals(
        List.of(
            new CandidateMatch("Ada Lovelace", "ada@example.com", 1, 0.91, List.of(), List.of()),
            new CandidateMatch("Grace Hopper", "grace@example.com", 2, 0.7, List.of(), List.of())),
        service(ranker).findCandidates(OFFER_ID));
  }

  @Test
  void deduplicatesEmailWithoutCaseSensitivityAndKeepsHighestProbability() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    doReturn(jobOffer()).when(ranker).loadJobOffer(OFFER_ID);
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(
                candidate("Ada Old", "Ada@Example.com", 0.61),
                candidate("Ada Lovelace", "ada@example.com", 0.91)));

    assertEquals(
        List.of(
            new CandidateMatch(
                "Ada Lovelace", "ada@example.com", 1, 0.91, List.of(), List.of())),
        service(ranker).findCandidates(OFFER_ID));
  }

  @Test
  void returnsEmptyWhenRankerFindsNoMatches() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class))).thenReturn(List.of());

    assertEquals(List.of(), service(ranker).findCandidates(OFFER_ID));
  }

  @Test
  void preservesExplanationFieldsWithTheirCandidateAfterSortingAndDeduplication() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(
                new RankedCandidate(
                    "Ada older",
                    "ada@example.com",
                    0.61,
                    List.of("Old skill"),
                    List.of("Old experience")),
                new RankedCandidate(
                    "Grace Hopper",
                    "grace@example.com",
                    0.7,
                    List.of("COBOL"),
                    List.of("Built compilers")),
                new RankedCandidate(
                    "Ada Lovelace",
                    "ada@example.com",
                    0.91,
                    List.of("Java"),
                    List.of("Built backend services"))));

    assertEquals(
        List.of(
            new CandidateMatch(
                "Ada Lovelace",
                "ada@example.com",
                1,
                0.91,
                List.of("Java"),
                List.of("Built backend services")),
            new CandidateMatch(
                "Grace Hopper",
                "grace@example.com",
                2,
                0.7,
                List.of("COBOL"),
                List.of("Built compilers"))),
        service(ranker).findCandidates(OFFER_ID));
  }

  @Test
  void rejectsNullRankingResponseAndCandidate() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class))).thenReturn(null);
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));

    when(ranker.rank(any(JobOffer.class))).thenReturn(
        Collections.singletonList(null));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));
  }

  @Test
  void rejectsMissingIdentityAndInvalidProbability() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());

    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(candidate(" ", "ada@example.com", 0.5)));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));

    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(candidate("Ada", " ", 0.5)));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));

    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(candidate("Ada", "ada@example.com", Double.NaN)));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));

    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(candidate("Ada", "ada@example.com", 1.01)));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));

    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(new RankedCandidate("Ada", "ada@example.com", null, List.of(), List.of())));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));
  }

  @Test
  void propagatesMissingOfferAndMatchingFailures() {
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenThrow(new NoSuchElementException("missing offer"));
    assertThrows(
        NoSuchElementException.class, () -> service(ranker).findCandidates(OFFER_ID));
    verify(ranker, never()).rank(any());

    doReturn(jobOffer()).when(ranker).loadJobOffer(OFFER_ID);
    doThrow(new IllegalStateException("Retrieval or model failure"))
        .when(ranker)
        .rank(any(JobOffer.class));
    assertThrows(
        IllegalStateException.class, () -> service(ranker).findCandidates(OFFER_ID));
  }
}
