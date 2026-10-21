package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.wowdev.recruitment.agent.entity.JobOffer;
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
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(document))
        .thenReturn(List.of(document, completeEvidence));
    ResumeIndexingProperties resumeProperties = new ResumeIndexingProperties();
    resumeProperties.setMaxNumChunks(73);
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(vectorStore, matchingProperties, pipelineProperties, resumeProperties);
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
            new ResumeIndexingProperties());

    assertEquals(List.of(), retriever.retrieve(new JobOffer(
        "offer-1", "Engineer", "Description", "Qualifications", "Brazil", "Remote",
        "Responsibilities", null, null, "Nice to have", null)));
  }
}
