package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class OpenAiCandidateRankerTest {

  private static final String OFFER_ID = "offer-1";
  private static final String ADA_EMAIL = "ada@example.com";
  private static final String GRACE_EMAIL = "grace@example.com";

  private static JobOffer jobOffer() {
    return new JobOffer(
        OFFER_ID,
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
  }

  private static OpenAiCandidateRanker ranker(
      ChatClient chatClient, JobOfferRepository offers, VectorStore vectorStore) {
    return new OpenAiCandidateRanker(
        chatClient, offers, vectorStore, new CandidateMatchingProperties());
  }

  private static void stubChat(
      ChatClient chatClient, List<RankedCandidateReference> rankedCandidates) {
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(new CandidateRankingResponse(rankedCandidates));
  }

  @Test
  void retrievesWithOfferCriteriaAndThresholdThenSendsOpaqueGroundedEvidenceToLlm() {
    ChatClient chatClient = mock(ChatClient.class);
    stubChat(
        chatClient,
        List.of(
            new RankedCandidateReference("candidate-2", 0.93),
            new RankedCandidateReference("candidate-1", 0.79)));
    JobOfferRepository offers = mock(JobOfferRepository.class);
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                new Document(
                    "Experienced backend developer in Brazil",
                    Map.of("candidateEmail", ADA_EMAIL)),
                new Document(
                    "Java platform specialist, remote", Map.of("candidateEmail", GRACE_EMAIL)),
                new Document("Additional experience", Map.of("candidateEmail", ADA_EMAIL))));
    OpenAiCandidateRanker ranker = ranker(chatClient, offers, vectorStore);

    assertEquals(
        List.of(
            new RankedCandidateEmail(GRACE_EMAIL, 0.93),
            new RankedCandidateEmail(ADA_EMAIL, 0.79)),
        ranker.rank(jobOffer()));
    ArgumentCaptor<SearchRequest> search = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(search.capture());
    assertEquals(
        "Platform engineer\nBuild cloud services\nJava and Kubernetes\n"
            + "Own service reliability\nEvent streaming",
        search.getValue().getQuery());
    assertEquals(0.6, search.getValue().getSimilarityThreshold());
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(chatClient.prompt()).user(prompt.capture());
    assertTrue(prompt.getValue().contains("Platform engineer"));
    assertTrue(prompt.getValue().contains("Brazil"));
    assertTrue(prompt.getValue().contains("Remote"));
    assertTrue(prompt.getValue().contains("candidate-1"));
    assertTrue(prompt.getValue().contains("Experienced backend developer in Brazil"));
    assertTrue(prompt.getValue().contains("technical skills"));
    assertTrue(prompt.getValue().contains("untrusted data"));
    assertTrue(!prompt.getValue().contains(ADA_EMAIL));
    assertTrue(!prompt.getValue().contains(GRACE_EMAIL));
  }

  @Test
  void groupsEvidenceAndSkipsDocumentsWithoutCandidateEmail() {
    ChatClient chatClient = mock(ChatClient.class);
    stubChat(chatClient, List.of(new RankedCandidateReference("candidate-1", 0.8)));
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(
            List.of(
                new Document("First chunk", Map.of("candidateEmail", ADA_EMAIL)),
                new Document("No email metadata", Map.of("source", "resume")),
                new Document("Second chunk", Map.of("candidateEmail", ADA_EMAIL)),
                new Document("Invalid email type", Map.of("candidateEmail", 42))));

    assertEquals(
        List.of(new RankedCandidateEmail(ADA_EMAIL, 0.8)),
        ranker(chatClient, mock(JobOfferRepository.class), vectorStore).rank(jobOffer()));
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(chatClient.prompt()).user(prompt.capture());
    assertEquals(2, prompt.getValue().split("chunk", -1).length - 1);
    assertTrue(!prompt.getValue().contains("No email metadata"));
  }

  @Test
  void avoidsLlmCallWhenRetrievalHasNoEligibleDocuments() {
    ChatClient chatClient = mock(ChatClient.class);
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    assertEquals(
        List.of(),
        ranker(chatClient, mock(JobOfferRepository.class), vectorStore).rank(jobOffer()));
    verify(chatClient, never()).prompt();
  }

  @Test
  void propagatesVectorStoreSearchFailures() {
    ChatClient chatClient = mock(ChatClient.class);
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenThrow(new IllegalStateException("Chroma is unavailable"));

    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class), vectorStore).rank(jobOffer()));
    verify(chatClient, never()).prompt();
  }

  @Test
  void ignoresUnknownAndDuplicateReferencesButRejectsInvalidProbabilities() {
    ChatClient chatClient = mock(ChatClient.class);
    stubChat(
        chatClient,
        List.of(
            new RankedCandidateReference("unknown", 0.99),
            new RankedCandidateReference("candidate-1", 0.9),
            new RankedCandidateReference("candidate-1", 0.8)));
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("Resume", Map.of("candidateEmail", ADA_EMAIL))));
    OpenAiCandidateRanker ranker = ranker(chatClient, mock(JobOfferRepository.class), vectorStore);

    assertEquals(List.of(new RankedCandidateEmail(ADA_EMAIL, 0.9)), ranker.rank(jobOffer()));

    ChatClient invalidChatClient = mock(ChatClient.class);
    stubChat(invalidChatClient, List.of(new RankedCandidateReference("candidate-1", Double.NaN)));
    assertThrows(
        IllegalStateException.class,
        () ->
            ranker(invalidChatClient, mock(JobOfferRepository.class), vectorStore)
                .rank(jobOffer()));
  }

  @Test
  void rejectsNullOrMalformedStructuredResponse() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class)).thenReturn(null);
    VectorStore vectorStore = mock(VectorStore.class);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(new Document("Resume", Map.of("candidateEmail", ADA_EMAIL))));

    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class), vectorStore).rank(jobOffer()));
  }

  @Test
  void loadsSelectedOfferFromMongoAndRejectsUnknownOffers() {
    JobOfferRepository offers = mock(JobOfferRepository.class);
    when(offers.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    OpenAiCandidateRanker ranker =
        ranker(mock(ChatClient.class), offers, mock(VectorStore.class));

    assertEquals(jobOffer(), ranker.loadJobOffer(OFFER_ID));
    assertThrows(NoSuchElementException.class, () -> ranker.loadJobOffer("missing"));
  }
}
