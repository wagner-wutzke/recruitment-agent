package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    verify(vectorStore, org.mockito.Mockito.times(2)).similaritySearch(request.capture());
    List<SearchRequest> requests = request.getAllValues();
    assertEquals(37, requests.getFirst().getTopK());
    assertEquals(0.82, requests.getFirst().getSimilarityThreshold());
    assertTrue(requests.getFirst().getQuery().contains("Platform engineer"));
    assertTrue(requests.getFirst().getQuery().contains("Build cloud services"));
    assertTrue(requests.getFirst().getQuery().contains("Java and Kubernetes"));
    assertTrue(requests.getFirst().getQuery().contains("Own service reliability"));
    assertTrue(requests.getFirst().getQuery().contains("Event streaming"));
    assertTrue(requests.getFirst().getQuery().contains("Brazil"));
    assertTrue(requests.getFirst().getQuery().contains("Remote"));
    SearchRequest candidateRequest = requests.get(1);
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
    verify(vectorStore, org.mockito.Mockito.times(3)).similaritySearch(requests.capture());
    assertTrue(requests.getAllValues().get(1).getFilterExpression().toString().contains("Ada@Example.com"));
    assertTrue(requests.getAllValues().get(2).getFilterExpression().toString().contains("bea@example.com"));
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
}
