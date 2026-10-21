package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
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
    Document document = new Document("resume evidence");
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document));
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(vectorStore, matchingProperties, pipelineProperties);
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

    assertEquals(List.of(document), retriever.retrieve(offer));

    ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(request.capture());
    assertEquals(37, request.getValue().getTopK());
    assertEquals(0.82, request.getValue().getSimilarityThreshold());
    assertTrue(request.getValue().getQuery().contains("Platform engineer"));
    assertTrue(request.getValue().getQuery().contains("Build cloud services"));
    assertTrue(request.getValue().getQuery().contains("Java and Kubernetes"));
    assertTrue(request.getValue().getQuery().contains("Own service reliability"));
    assertTrue(request.getValue().getQuery().contains("Event streaming"));
    assertTrue(request.getValue().getQuery().contains("Brazil"));
    assertTrue(request.getValue().getQuery().contains("Remote"));
  }

  @Test
  void returnsEmptyListWhenVectorStoreReturnsNull() {
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);
    CandidateResumeRetriever retriever =
        new CandidateResumeRetriever(
            vectorStore, new CandidateMatchingProperties(), new CandidateMatchingPipelineProperties());

    assertEquals(List.of(), retriever.retrieve(new JobOffer(
        "offer-1", "Engineer", "Description", "Qualifications", "Brazil", "Remote",
        "Responsibilities", null, null, "Nice to have", null)));
  }
}
