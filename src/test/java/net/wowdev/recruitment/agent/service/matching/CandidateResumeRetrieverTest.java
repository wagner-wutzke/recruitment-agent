package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import net.wowdev.recruitment.agent.service.resume.ResumeIndexingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class CandidateResumeRetrieverTest {

  @Test
  void searchesUsingAllOfferCriteriaAndConfiguredLimits() {
    VectorStore vectorStore = mock(VectorStore.class);
    CandidateMatchingProperties matchingProperties = new CandidateMatchingProperties();
    matchingProperties.setSimilarityThreshold(0.82);
    CandidateMatchingPipelineProperties pipelineProperties =
        new CandidateMatchingPipelineProperties();
    pipelineProperties.setRetrievalTopK(37);
    Document document =
        new Document(
            "chunk-1",
            "resume evidence",
            Map.of("candidateEmail", "ada@example.com", "documentId", "resume-v1"));
    Document completeEvidence =
        new Document("chunk-2", "another section", Map.of("candidateEmail", "ada@example.com", "documentId", "resume-v1"));
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any())).thenReturn(List.of(resume("ada@example.com", "resume-v1")));
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(document))
        .thenReturn(List.of(document))
        .thenReturn(List.of(document, completeEvidence));
    ResumeIndexingProperties resumeProperties = new ResumeIndexingProperties();
    resumeProperties.setMaxNumChunks(73);
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(
            vectorStore, matchingProperties, pipelineProperties, resumeProperties, resumeRepository);
    JobOffer offer =
        new JobOffer(
            "offer-1",
            "Platform engineer",
            "Build cloud services",
            "Java and Kubernetes",
            "Brazil",
            "Remote",
            "Own service reliability",
            null,
            null,
            "Event streaming",
            null);

    assertEquals(List.of(document, completeEvidence), retriever.retrieve(offer));

    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(3)).similaritySearch(request.capture());
    List<SearchRequest> requests = request.getAllValues();
    assertEquals(37, requests.getFirst().getTopK());
    assertEquals(0.82, requests.getFirst().getSimilarityThreshold());
    assertTrue(requests.getFirst().hasFilterExpression());
    assertTrue(requests.getFirst().getFilterExpression().toString().contains("positionFilterValue"));
    assertTrue(requests.getFirst().getFilterExpression().toString().contains("allocationFilterValue"));
    assertTrue(requests.getFirst().getFilterExpression().toString().contains("platform engineer"));
    assertTrue(requests.getFirst().getFilterExpression().toString().contains("remote"));
    assertFalse(requests.getFirst().getFilterExpression().toString().contains("country"));
    assertTrue(requests.getFirst().getQuery().contains("Platform engineer"));
    assertTrue(requests.getFirst().getQuery().contains("Build cloud services"));
    assertTrue(requests.getFirst().getQuery().contains("Java and Kubernetes"));
    assertTrue(requests.getFirst().getQuery().contains("Own service reliability"));
    assertTrue(requests.getFirst().getQuery().contains("Event streaming"));
    assertTrue(requests.getFirst().getQuery().contains("Brazil"));
    assertTrue(requests.getFirst().getQuery().contains("Remote"));
    assertFalse(requests.get(1).hasFilterExpression());
    SearchRequest candidateRequest = requests.get(2);
    assertEquals(73, candidateRequest.getTopK());
    assertEquals(0.0, candidateRequest.getSimilarityThreshold());
    assertTrue(candidateRequest.hasFilterExpression());
  }

  @Test
  void returnsEmptyListWhenVectorStoreReturnsNull() {
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(
            vectorStore,
            new CandidateMatchingProperties(),
            new CandidateMatchingPipelineProperties(),
            new ResumeIndexingProperties(),
            mock(ResumeRepository.class));

    assertEquals(List.of(), retriever.retrieve(new JobOffer(
        "offer-1", "Engineer", "Description", "Qualifications", "Brazil", "Remote",
        "Responsibilities", null, null, "Nice to have", null)));
  }

  @Test
  void filtersStaleVersionsDeduplicatesCandidatesAndCapsBeforeHydrating() {
    VectorStore vectorStore = mock(VectorStore.class);
    CandidateMatchingPipelineProperties pipelineProperties =
        new CandidateMatchingPipelineProperties();
    pipelineProperties.setMaxCandidates(2);
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(
            List.of(
                resume("ada@example.com", "ada-v1"),
                resume("bea@example.com", "bea-v1"),
                resume("cy@example.com", "cy-v2")));

    Document adaStrong = seed("ada-strong", "Ada@Example.com", "ada-v1", 0.91);
    Document adaWeak = seed("ada-weak", "ada@example.com", "ada-v1", 0.71);
    Document beaHit = seed("bea-hit", "bea@example.com", "bea-v1", 0.83);
    Document cyStaleHit = seed("cy-stale", "cy@example.com", "cy-v1", 0.99);
    Document adaHydrated = seed("ada-hydrated", "ada@example.com", "ada-v1");
    Document beaHydrated = seed("bea-hydrated", "bea@example.com", "bea-v1");
    Document unrelatedHydrationResult = seed("unrelated", "cy@example.com", "cy-v2");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(adaStrong, adaWeak, beaHit, cyStaleHit))
        .thenReturn(List.of())
        .thenReturn(List.of(adaHydrated, unrelatedHydrationResult))
        .thenReturn(List.of(beaHydrated));

    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(
            vectorStore,
            new CandidateMatchingProperties(),
            pipelineProperties,
            new ResumeIndexingProperties(),
            resumeRepository);

    assertEquals(List.of(adaHydrated, beaHydrated), retriever.retrieve(offer()));

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(4)).similaritySearch(requests.capture());
    assertFalse(requests.getAllValues().get(1).hasFilterExpression());
    assertTrue(requests.getAllValues().get(2).getFilterExpression().toString().contains("Ada@Example.com"));
    assertTrue(requests.getAllValues().get(3).getFilterExpression().toString().contains("bea@example.com"));
  }

  @Test
  void breaksEqualSimilarityScoresByNormalizedEmail() {
    VectorStore vectorStore = mock(VectorStore.class);
    CandidateMatchingPipelineProperties pipelineProperties =
        new CandidateMatchingPipelineProperties();
    pipelineProperties.setMaxCandidates(1);
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(
            List.of(resume("zeta@example.com", "zeta-v1"), resume("alpha@example.com", "alpha-v1")));
    Document zeta = seed("zeta-seed", "zeta@example.com", "zeta-v1", 0.8);
    Document alpha = seed("alpha-seed", "alpha@example.com", "alpha-v1", 0.8);
    Document alphaHydrated = seed("alpha-hydrated", "alpha@example.com", "alpha-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(zeta, alpha))
        .thenReturn(List.of())
        .thenReturn(List.of(alphaHydrated));
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(
            vectorStore,
            new CandidateMatchingProperties(),
            pipelineProperties,
            new ResumeIndexingProperties(),
            resumeRepository);

    assertEquals(List.of(alphaHydrated), retriever.retrieve(offer()));
  }

  @Test
  void preservesVectorStoreOrderWhenSimilarityScoresAreUnavailable() {
    VectorStore vectorStore = mock(VectorStore.class);
    CandidateMatchingPipelineProperties pipelineProperties =
        new CandidateMatchingPipelineProperties();
    pipelineProperties.setMaxCandidates(1);
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(
            List.of(resume("zeta@example.com", "zeta-v1"), resume("alpha@example.com", "alpha-v1")));
    Document zeta = seed("zeta-seed", "zeta@example.com", "zeta-v1");
    Document alpha = seed("alpha-seed", "alpha@example.com", "alpha-v1");
    Document zetaHydrated = seed("zeta-hydrated", "zeta@example.com", "zeta-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(zeta, alpha))
        .thenReturn(List.of())
        .thenReturn(List.of(zetaHydrated));
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(
            vectorStore,
            new CandidateMatchingProperties(),
            pipelineProperties,
            new ResumeIndexingProperties(),
            resumeRepository);

    assertEquals(List.of(zetaHydrated), retriever.retrieve(offer()));
  }

  private static Document seed(String id, String email, String documentId) {
    return new Document(id, "resume text", Map.of("candidateEmail", email, "documentId", documentId));
  }

  private static Document seed(String id, String email, String documentId, double score) {
    return Document.builder()
        .id(id)
        .text("resume text")
        .metadata(Map.of("candidateEmail", email, "documentId", documentId))
        .score(score)
        .build();
  }

  private static ResumeDocument resume(String email, String documentId) {
    return new ResumeDocument(email, "Candidate", "resume.pdf", Instant.EPOCH, documentId);
  }

  private static JobOffer offer() {
    return new JobOffer(
        "offer-1", "Engineer", "Description", "Qualifications", "Brazil", "Remote",
        "Responsibilities", null, null, "Nice to have", null);
  }

  @Test
  void usesOnlyUnconstrainedSearchWhenOfferHasNoFilterableValues() {
    VectorStore vectorStore = mock(VectorStore.class);
    Document seed = seed("seed", "ada@example.com", "resume-v1", 0.9);
    Document hydrated = seed("hydrated", "ada@example.com", "resume-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(seed))
        .thenReturn(List.of(hydrated));
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(List.of(resume("ada@example.com", "resume-v1")));
    CandidateResumeRetriever retriever =
        retriever(vectorStore, new CandidateMatchingPipelineProperties(), resumeRepository);
    JobOffer offer = new JobOffer("offer", "  ", "Description", null, "Toronto", null);

    assertEquals(List.of(hydrated), retriever.retrieve(offer));

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(2)).similaritySearch(requests.capture());
    assertFalse(requests.getAllValues().getFirst().hasFilterExpression());
  }

  @Test
  void supplementsConstrainedResultsDeduplicatesAndCapsTheCombinedSeedSet() {
    VectorStore vectorStore = mock(VectorStore.class);
    CandidateMatchingPipelineProperties pipelineProperties =
        new CandidateMatchingPipelineProperties();
    pipelineProperties.setRetrievalTopK(2);
    pipelineProperties.setMaxCandidates(5);
    Document first = seed("first-seed", "first@example.com", "first-v1", 0.95);
    Document second = seed("second-seed", "second@example.com", "second-v1", 0.9);
    Document third = seed("third-seed", "third@example.com", "third-v1", 0.85);
    Document firstHydrated = seed("first-hydrated", "first@example.com", "first-v1");
    Document secondHydrated = seed("second-hydrated", "second@example.com", "second-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(first))
        .thenReturn(List.of(first, second, third))
        .thenReturn(List.of(firstHydrated))
        .thenReturn(List.of(secondHydrated));
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(
            List.of(
                resume("first@example.com", "first-v1"),
                resume("second@example.com", "second-v1"),
                resume("third@example.com", "third-v1")));
    CandidateResumeRetriever retriever =
        retriever(vectorStore, pipelineProperties, resumeRepository);
    JobOffer offer = new JobOffer("offer", "  eNgInEeR  ", null, null, "Toronto", " REMOTE ");

    assertEquals(List.of(firstHydrated, secondHydrated), retriever.retrieve(offer));

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(4)).similaritySearch(requests.capture());
    String filter = requests.getAllValues().getFirst().getFilterExpression().toString();
    assertTrue(filter.contains("positionFilterValue"));
    assertTrue(filter.contains("engineer"));
    assertTrue(filter.contains("allocationFilterValue"));
    assertTrue(filter.contains("remote"));
    assertEquals(2, requests.getAllValues().getFirst().getTopK());
    assertFalse(requests.getAllValues().get(1).hasFilterExpression());
  }

  @Test
  void retriesUnconstrainedSearchWhenMetadataFilterSearchThrows() {
    VectorStore vectorStore = mock(VectorStore.class);
    Document seed = seed("seed", "ada@example.com", "resume-v1", 0.9);
    Document hydrated = seed("hydrated", "ada@example.com", "resume-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new IllegalArgumentException("filter unsupported"))
        .thenReturn(List.of(seed))
        .thenReturn(List.of(hydrated));
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(List.of(resume("ada@example.com", "resume-v1")));
    CandidateResumeRetriever retriever =
        retriever(vectorStore, new CandidateMatchingPipelineProperties(), resumeRepository);

    assertEquals(List.of(hydrated), retriever.retrieve(offer()));

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(3)).similaritySearch(requests.capture());
    assertTrue(requests.getAllValues().getFirst().hasFilterExpression());
    assertFalse(requests.getAllValues().get(1).hasFilterExpression());
  }

  @Test
  void doesNotFallbackWhenConstrainedSearchFillsTheRetrievalLimit() {
    VectorStore vectorStore = mock(VectorStore.class);
    CandidateMatchingPipelineProperties pipelineProperties = new CandidateMatchingPipelineProperties();
    pipelineProperties.setRetrievalTopK(1);
    Document seed = seed("seed", "ada@example.com", "resume-v1", 0.9);
    Document hydrated = seed("hydrated", "ada@example.com", "resume-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(seed))
        .thenReturn(List.of(hydrated));
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(List.of(resume("ada@example.com", "resume-v1")));
    CandidateResumeRetriever retriever =
        retriever(vectorStore, pipelineProperties, resumeRepository);

    assertEquals(List.of(hydrated), retriever.retrieve(offer()));

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(2)).similaritySearch(requests.capture());
    assertTrue(requests.getAllValues().getFirst().hasFilterExpression());
    assertTrue(requests.getAllValues().get(1).getFilterExpression().toString().contains("candidateEmail"));
  }

  @Test
  void filtersByOnlyPositionWhenAllocationIsMissing() {
    VectorStore vectorStore = mock(VectorStore.class);
    Document seed = seed("seed", "ada@example.com", "resume-v1", 0.9);
    Document hydrated = seed("hydrated", "ada@example.com", "resume-v1");
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(seed))
        .thenReturn(List.of())
        .thenReturn(List.of(hydrated));
    ResumeRepository resumeRepository = mock(ResumeRepository.class);
    when(resumeRepository.findAllById(any()))
        .thenReturn(List.of(resume("ada@example.com", "resume-v1")));
    CandidateResumeRetriever retriever =
        retriever(vectorStore, new CandidateMatchingPipelineProperties(), resumeRepository);
    JobOffer offer = new JobOffer("offer", "Engineer", null, null, "Toronto", "  ");

    assertEquals(List.of(hydrated), retriever.retrieve(offer));

    ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, org.mockito.Mockito.times(3)).similaritySearch(requests.capture());
    String filter = requests.getAllValues().getFirst().getFilterExpression().toString();
    assertTrue(filter.contains("positionFilterValue"));
    assertFalse(filter.contains("allocationFilterValue"));
  }

  @Test
  void propagatesFailureWhenConstrainedAndFallbackSearchBothFail() {
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new IllegalStateException("search unavailable"));
    CandidateResumeRetriever retriever =
        retriever(
            vectorStore, new CandidateMatchingPipelineProperties(), mock(ResumeRepository.class));

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> retriever.retrieve(offer()));

    verify(vectorStore, org.mockito.Mockito.times(2)).similaritySearch(any(SearchRequest.class));
  }

  private static CandidateResumeRetriever retriever(
      VectorStore vectorStore,
      CandidateMatchingPipelineProperties pipelineProperties,
      ResumeRepository resumeRepository) {
    return new CandidateResumeRetriever(
        vectorStore,
        new CandidateMatchingProperties(),
        pipelineProperties,
        new ResumeIndexingProperties(),
        resumeRepository);
  }
}
