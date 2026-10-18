package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.junit.jupiter.api.Test;

class CandidateMatchingServiceTest {

  private static final String OFFER_ID = "offer-123";
  private static final String EMAIL = "ada@example.com";
  private static final String SECOND_EMAIL = "grace@example.com";

  private static JobOffer jobOffer() {
    return new JobOffer(
        OFFER_ID,
        "Platform Engineer",
        "Build reliable Java services",
        "Java and distributed systems",
        "Remote",
        "Remote");
  }

  private static ResumeDocument resume(String email, String name) {
    return new ResumeDocument(
        email, name, name.replace(' ', '_') + ".pdf", Instant.now(), UUID.randomUUID());
  }

  private static CandidateMatchingService service(
      ResumeRepository resumeRepository, CandidateRanker ranker) {
    return new CandidateMatchingService(resumeRepository, ranker);
  }

  @Test
  void delegatesOfferLoadingAndRankingAndReturnsSortedMongoCandidates() {
    ResumeRepository resumes = mock(ResumeRepository.class);
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(
                new RankedCandidateEmail(EMAIL, 0.81),
                new RankedCandidateEmail(SECOND_EMAIL, 0.92)));
    when(resumes.findById(EMAIL)).thenReturn(Optional.of(resume(EMAIL, "Ada Lovelace")));
    when(resumes.findById(SECOND_EMAIL))
        .thenReturn(Optional.of(resume(SECOND_EMAIL, "Grace Hopper")));
    CandidateMatchingService service = service(resumes, ranker);

    assertEquals(
        List.of(
            new CandidateMatch("Grace Hopper", SECOND_EMAIL, 1, 0.92),
            new CandidateMatch("Ada Lovelace", EMAIL, 2, 0.81)),
        service.findCandidates(OFFER_ID));
    verify(ranker).loadJobOffer(OFFER_ID);
    verify(ranker).rank(jobOffer());
  }

  @Test
  void returnsOnlyExistingMongoResumeIdentitiesAndDropsDuplicateEmails() {
    ResumeRepository resumes = mock(ResumeRepository.class);
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(
            List.of(
                new RankedCandidateEmail(EMAIL, 0.91),
                new RankedCandidateEmail(EMAIL, 0.82),
                new RankedCandidateEmail(SECOND_EMAIL, 0.7)));
    when(resumes.findById(EMAIL)).thenReturn(Optional.of(resume(EMAIL, "Ada Lovelace")));
    when(resumes.findById(SECOND_EMAIL)).thenReturn(Optional.empty());

    assertEquals(
        List.of(new CandidateMatch("Ada Lovelace", EMAIL, 1, 0.91)),
        service(resumes, ranker).findCandidates(OFFER_ID));
    verify(resumes).findById(EMAIL);
    verify(resumes).findById(SECOND_EMAIL);
  }

  @Test
  void returnsEmptyWhenRankerFindsNoMatches() {
    ResumeRepository resumes = mock(ResumeRepository.class);
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class))).thenReturn(List.of());

    assertEquals(List.of(), service(resumes, ranker).findCandidates(OFFER_ID));
    verify(resumes, never()).findById(any());
  }

  @Test
  void rejectsNullRankerResponse() {
    ResumeRepository resumes = mock(ResumeRepository.class);
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class))).thenReturn(null);

    assertThrows(
        IllegalStateException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));
  }

  @Test
  void rejectsInvalidCandidateAndProbability() {
    ResumeRepository resumes = mock(ResumeRepository.class);
    CandidateRanker ranker = mock(CandidateRanker.class);
    when(ranker.loadJobOffer(OFFER_ID)).thenReturn(jobOffer());
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(new RankedCandidateEmail(" ", 0.5)));
    assertThrows(
        IllegalStateException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));

    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(new RankedCandidateEmail(EMAIL, Double.NaN)));
    assertThrows(
        IllegalStateException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));
    when(ranker.rank(any(JobOffer.class)))
        .thenReturn(List.of(new RankedCandidateEmail(EMAIL, 1.01)));
    assertThrows(
        IllegalStateException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));
  }

  @Test
  void rejectsUnknownOfferAndPropagatesRankerOrMongoFailures() {
    ResumeRepository resumes = mock(ResumeRepository.class);
    CandidateRanker ranker = mock(CandidateRanker.class);
    doThrow(new IllegalArgumentException("Missing offer")).when(ranker).loadJobOffer(OFFER_ID);
    assertThrows(
        IllegalArgumentException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));
    verify(ranker, never()).rank(any());

    doReturn(jobOffer()).when(ranker).loadJobOffer(OFFER_ID);
    doThrow(new IllegalStateException("Vector store or model unavailable"))
        .when(ranker).rank(any(JobOffer.class));
    assertThrows(
        IllegalStateException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));

    doReturn(List.of(new RankedCandidateEmail(EMAIL, 0.88)))
        .when(ranker).rank(any(JobOffer.class));
    when(resumes.findById(EMAIL)).thenThrow(new IllegalStateException("Mongo unavailable"));
    assertThrows(
        IllegalStateException.class, () -> service(resumes, ranker).findCandidates(OFFER_ID));
  }
}
