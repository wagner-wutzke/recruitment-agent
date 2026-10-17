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

  @Test
  void searchesWithOfferDescriptionAndQualificationsAndReturnsUniqueCandidates() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    JobOffer offer =
        new JobOffer(
            OFFER_ID,
            "Platform Engineer",
            "Build reliable Java services",
            "Java and distributed systems",
            "Remote",
            "Remote");
    ResumeDocument resume = resume(EMAIL);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                vectorHit(EMAIL),
                vectorHit(EMAIL),
                new Document("Missing candidate metadata", Map.of("source", "resume")),
                vectorHit("unknown@example.com")));
    when(resumeRepository.findById(EMAIL)).thenReturn(Optional.of(resume));
    when(resumeRepository.findById("unknown@example.com")).thenReturn(Optional.empty());
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    List<CandidateMatch> candidates = service.findCandidates(OFFER_ID);

    assertEquals(List.of(new CandidateMatch(NAME, EMAIL)), candidates);
    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertEquals("Build reliable Java services\nJava and distributed systems", request.getValue().getQuery());
    assertEquals(SearchRequest.DEFAULT_TOP_K, request.getValue().getTopK());
    verify(resumeRepository).findById(EMAIL);
    verify(resumeRepository).findById("unknown@example.com");
  }

  @Test
  void returnsEmptyWhenVectorStoreHasNoMatches() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    assertEquals(List.of(), service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
  }

  @Test
  void treatsNullVectorStoreResultsAsNoMatches() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    assertEquals(List.of(), service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
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
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    assertEquals(List.of(), service.findCandidates(OFFER_ID));
    verify(resumeRepository, never()).findById(any());
  }

  @Test
  void usesEmptyTextForMissingOfferDescriptionOrQualifications() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    JobOffer offer = new JobOffer(OFFER_ID, "Role", null, null, "Remote", "Remote");
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    service.findCandidates(OFFER_ID);

    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertEquals("\n", request.getValue().getQuery());
  }

  @Test
  void rejectsUnknownJobOfferWithoutSearchingVectorStore() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.empty());
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

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
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    assertThrows(IllegalStateException.class, () -> service.findCandidates(OFFER_ID));
  }

  @Test
  void propagatesMongoResumeLookupFailures() {
    JobOfferRepository jobOfferRepository = org.mockito.Mockito.mock(JobOfferRepository.class);
    ResumeRepository resumeRepository = org.mockito.Mockito.mock(ResumeRepository.class);
    VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
    when(jobOfferRepository.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorHit(EMAIL)));
    when(resumeRepository.findById(EMAIL)).thenThrow(new IllegalStateException("Mongo unavailable"));
    CandidateMatchingService service =
        new CandidateMatchingService(jobOfferRepository, resumeRepository, vectorStore);

    assertThrows(IllegalStateException.class, () -> service.findCandidates(OFFER_ID));
  }

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
    return new Document("Resume vector match", Map.of("candidateEmail", email));
  }
}
