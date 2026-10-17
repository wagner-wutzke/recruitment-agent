package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class CandidateMatchingServiceTest {

  private static final String OFFER_ID = "offer-123";
  private static final String EMAIL = "ada@example.com";
  private static final String NAME = "Ada Lovelace";
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

  private static ResumeDocument resume(String email) {
    return new ResumeDocument(email, NAME, "Ada_Lovelace.pdf", Instant.now(), UUID.randomUUID());
  }

  private static Document vectorHit(String email) {
    return new Document("Resume vector match " + email, Map.of("candidateEmail", email));
  }

  private static CandidateMatchingService service(
      JobOfferRepository jobOfferRepository,
      ResumeRepository resumeRepository,
      VectorStore vectorStore,
      CandidateRanker candidateRanker) {
    return new CandidateMatchingService(
        jobOfferRepository,
        resumeRepository,
        vectorStore,
        new CandidateMatchingProperties(),
        candidateRanker);
  }

  @Test
  void searchesWithAllOfferCriteriaAndConfiguredThresholdAndReturnsUniqueCandidates() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    when(candidateRanker.rank(any(), any()))
        .thenReturn(List.of("candidate-2", "candidate-1", "candidate-2", "invented"));
    JobOffer offer =
        new JobOffer(
            OFFER_ID,
            "Platform Engineer",
            "Build reliable Java services",
            "Java and distributed systems",
            "Remote",
            "Remote",
            "Build and maintain services",
            "Health insurance",
            "Example Co",
            "Kubernetes experience",
            "Market salary");
    ResumeDocument resume = resume(EMAIL);
    ResumeDocument secondResume =
        new ResumeDocument(
            SECOND_EMAIL, "Grace Hopper", "Grace_Hopper.pdf", Instant.now(), UUID.randomUUID());
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                vectorHit(EMAIL),
                vectorHit(EMAIL),
                new Document("Missing candidate metadata", Map.of("source", "resume")),
                vectorHit(SECOND_EMAIL)));
    when(resumeRepository.findById(EMAIL)).thenReturn(Optional.of(resume));
    when(resumeRepository.findById(SECOND_EMAIL)).thenReturn(Optional.of(secondResume));
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    List<CandidateMatch> candidates = service.findCandidates(OFFER_ID);

    assertEquals(
        List.of(new CandidateMatch("Grace Hopper", SECOND_EMAIL), new CandidateMatch(NAME, EMAIL)),
        candidates);
    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertEquals(
        "Platform Engineer\nBuild reliable Java services\nJava and distributed systems"
            + "\nBuild and maintain services\nKubernetes experience",
        request.getValue().getQuery());
    assertEquals(SearchRequest.DEFAULT_TOP_K, request.getValue().getTopK());
    assertEquals(0.6, request.getValue().getSimilarityThreshold());
    verify(resumeRepository).findById(EMAIL);
    verify(resumeRepository).findById(SECOND_EMAIL);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, List<String>>> evidence = ArgumentCaptor.forClass(Map.class);
    verify(candidateRanker).rank(any(JobOffer.class), evidence.capture());
    assertEquals(
        List.of("Resume vector match " + EMAIL, "Resume vector match " + EMAIL),
        evidence.getValue().get("candidate-1"));
    assertEquals(
        List.of("Resume vector match " + SECOND_EMAIL), evidence.getValue().get("candidate-2"));
  }

  @Test
  void returnsEmptyWhenVectorStoreHasNoMatches() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertEquals(List.of(), service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
    verify(candidateRanker, never()).rank(any(), any());
  }

  @Test
  void treatsNullVectorStoreResultsAsNoMatches() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertEquals(List.of(), service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
    verify(candidateRanker, never()).rank(any(), any());
  }

  @Test
  void ignoresMatchesWithoutAUsableCandidateEmail() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                new Document("Blank candidate email", Map.of("candidateEmail", " ")),
                new Document("Wrong candidate email type", Map.of("candidateEmail", 42))));
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertEquals(List.of(), service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
    verify(candidateRanker, never()).rank(any(), any());
  }

  @Test
  void usesEmptyTextForMissingOfferCriteria() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    JobOffer offer = new JobOffer(OFFER_ID, "Role", null, null, "Remote", "Remote");
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    service.findCandidates(OFFER_ID);

    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertEquals("Role\n\n\n\n", request.getValue().getQuery());
  }

  @Test
  void rejectsUnknownJobOfferWithoutSearchingVectorStore() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.empty());
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertThrows(NoSuchElementException.class, () -> service.findCandidates(OFFER_ID));
    verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
  }

  @Test
  void propagatesVectorStoreFailures() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new IllegalStateException("Chroma is unavailable"));
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertThrows(IllegalStateException.class, () -> service.findCandidates(OFFER_ID));
  }

  @Test
  void propagatesMongoResumeLookupFailures() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(vectorHit(EMAIL)));
    when(resumeRepository.findById(EMAIL))
        .thenThrow(new IllegalStateException("Mongo unavailable"));
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    when(candidateRanker.rank(any(), any())).thenReturn(List.of("candidate-1"));
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertThrows(IllegalStateException.class, () -> service.findCandidates(OFFER_ID));
  }

  @Test
  void propagatesModelRankingFailuresWithoutReturningPartialCandidates() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    CandidateRanker candidateRanker = org.mockito.Mockito.mock(CandidateRanker.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(vectorHit(EMAIL)));
    when(candidateRanker.rank(any(), any()))
        .thenThrow(new IllegalStateException("OpenAI unavailable"));
    CandidateMatchingService service =
        service(jobOfferRepository, resumeRepository, vectorStore, candidateRanker);

    assertThrows(IllegalStateException.class, () -> service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
  }
}
