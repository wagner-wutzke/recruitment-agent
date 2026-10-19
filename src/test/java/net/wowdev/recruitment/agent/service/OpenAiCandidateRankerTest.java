package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

class OpenAiCandidateRankerTest {

  private static final String OFFER_ID = "offer-1";
  private static final String CANDIDATE_NAME = "Ada Lovelace";
  private static final String CANDIDATE_EMAIL = "ada@example.com";

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

  private static OpenAiCandidateRanker ranker(ChatClient chatClient, JobOfferRepository offers) {
    return ranker(chatClient, offers, mock(VectorStore.class), 0.6);
  }

  private static OpenAiCandidateRanker ranker(
      ChatClient chatClient,
      JobOfferRepository offers,
      VectorStore vectorStore,
      double similarityThreshold) {
    CandidateMatchingProperties properties = new CandidateMatchingProperties();
    properties.setSimilarityThreshold(similarityThreshold);
    OpenAiCandidateRanker ranker =
        new OpenAiCandidateRanker(chatClient, offers, vectorStore, properties);
    ReflectionTestUtils.setField(
        ranker,
        "userPrompt",
        new org.springframework.core.io.ClassPathResource("prompts/candidate-ranking-user.txt"));
    return ranker;
  }

  private static ChatClient.ChatClientRequestSpec stubChat(
      ChatClient chatClient, List<RankedCandidate> rankedCandidates) {
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.advisors(any(Advisor[].class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(new CandidateRankingResponse(rankedCandidates));
    return request;
  }

  @Test
  void attachesRequestRetrievalAdvisorAndSubmitsOfferCriteria() {
    ChatClient chatClient = mock(ChatClient.class);
    VectorStore vectorStore = mock(VectorStore.class);
    ChatClient.ChatClientRequestSpec request =
        stubChat(chatClient, List.of(new RankedCandidate(CANDIDATE_NAME, CANDIDATE_EMAIL, 0.93)));
    OpenAiCandidateRanker ranker =
        ranker(chatClient, mock(JobOfferRepository.class), vectorStore, 0.82);

    assertEquals(
        List.of(new RankedCandidate(CANDIDATE_NAME, CANDIDATE_EMAIL, 0.93)),
        ranker.rank(jobOffer()));

    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(request).user(query.capture());
    assertTrue(query.getValue().contains("Platform engineer"));
    assertTrue(query.getValue().contains("Build cloud services"));
    assertTrue(query.getValue().contains("Java and Kubernetes"));
    assertTrue(query.getValue().contains("Own service reliability"));
    assertTrue(query.getValue().contains("Event streaming"));
    assertTrue(query.getValue().contains("Brazil"));
    assertTrue(query.getValue().contains("Remote"));
    assertTrue(query.getValue().contains("technical skills"));
    assertTrue(query.getValue().contains("country"));
    assertTrue(query.getValue().contains("city"));
    assertTrue(query.getValue().contains("professional experience"));

    ArgumentCaptor<Advisor[]> advisors = ArgumentCaptor.forClass(Advisor[].class);
    verify(request).advisors(advisors.capture());
    assertEquals(1, advisors.getValue().length);
    RetrievalAugmentationAdvisor retrievalAdvisor =
        assertInstanceOf(RetrievalAugmentationAdvisor.class, advisors.getValue()[0]);
    when(vectorStore.similaritySearch(any(SearchRequest.class)))
        .thenReturn(List.of(mock(Document.class)));
    DocumentRetriever retriever =
        (DocumentRetriever) ReflectionTestUtils.getField(retrievalAdvisor, "documentRetriever");
    retriever.retrieve(new Query(query.getValue()));
    ArgumentCaptor<SearchRequest> searchRequest = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(searchRequest.capture());
    assertEquals(0.82, searchRequest.getValue().getSimilarityThreshold());
  }

  @Test
  void acceptsInclusiveProbabilityBoundariesAndEmptyMatchResponse() {
    ChatClient chatClient = mock(ChatClient.class);
    stubChat(
        chatClient,
        List.of(
            new RankedCandidate("Candidate at zero", "zero@example.com", 0.0),
            new RankedCandidate("Candidate at one", "one@example.com", 1.0)));

    assertEquals(2, ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()).size());

    ChatClient emptyChatClient = mock(ChatClient.class);
    stubChat(emptyChatClient, List.of());
    assertEquals(
        List.of(), ranker(emptyChatClient, mock(JobOfferRepository.class)).rank(jobOffer()));
  }

  @Test
  void propagatesRetrievalAugmentationFailuresFromChatClient() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.advisors(any(Advisor[].class))).thenReturn(request);
    when(request.call()).thenThrow(new IllegalStateException("Vector Store unavailable"));

    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));
  }

  @Test
  void reusesTheSameRankerOwnedAdvisorAcrossRequests() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = stubChat(chatClient, List.of());
    ChatClient.ChatClientRequestSpec secondRequest = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec secondResponse = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request, secondRequest);
    when(secondRequest.user(anyString())).thenReturn(secondRequest);
    when(secondRequest.advisors(any(Advisor[].class))).thenReturn(secondRequest);
    when(secondRequest.call()).thenReturn(secondResponse);
    when(secondResponse.entity(CandidateRankingResponse.class))
        .thenReturn(new CandidateRankingResponse(List.of()));
    VectorStore vectorStore = mock(VectorStore.class);
    OpenAiCandidateRanker ranker = ranker(chatClient, mock(JobOfferRepository.class), vectorStore, 0.6);
    ranker.rank(jobOffer());
    ranker.rank(jobOffer());

    ArgumentCaptor<Advisor[]> advisors = ArgumentCaptor.forClass(Advisor[].class);
    verify(request).advisors(advisors.capture());
    ArgumentCaptor<Advisor[]> secondAdvisors = ArgumentCaptor.forClass(Advisor[].class);
    verify(secondRequest).advisors(secondAdvisors.capture());
    assertEquals(1, advisors.getValue().length);
    assertEquals(1, secondAdvisors.getValue().length);
    assertEquals(advisors.getValue()[0], secondAdvisors.getValue()[0]);
    assertInstanceOf(RetrievalAugmentationAdvisor.class, advisors.getValue()[0]);
  }

  @Test
  void rejectsNullOrMalformedStructuredResponse() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.advisors(any(Advisor[].class))).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class)).thenReturn(null);

    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));

    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(new CandidateRankingResponse(null));
    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));

    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(
            new CandidateRankingResponse(List.of(new RankedCandidate(" ", CANDIDATE_EMAIL, 0.5))));
    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));

    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(
            new CandidateRankingResponse(List.of(new RankedCandidate(CANDIDATE_NAME, " ", 0.5))));
    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));

    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(
            new CandidateRankingResponse(
                List.of(new RankedCandidate(CANDIDATE_NAME, CANDIDATE_EMAIL, Double.NaN))));
    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));

    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(
            new CandidateRankingResponse(
                List.of(new RankedCandidate(CANDIDATE_NAME, CANDIDATE_EMAIL, 1.01))));
    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));

    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(
            new CandidateRankingResponse(
                List.of(new RankedCandidate(CANDIDATE_NAME, CANDIDATE_EMAIL, null))));
    assertThrows(
        IllegalStateException.class,
        () -> ranker(chatClient, mock(JobOfferRepository.class)).rank(jobOffer()));
  }

  @Test
  void loadsSelectedOfferFromMongoAndRejectsUnknownOffers() {
    JobOfferRepository offers = mock(JobOfferRepository.class);
    when(offers.findById(OFFER_ID)).thenReturn(Optional.of(jobOffer()));
    OpenAiCandidateRanker ranker = ranker(mock(ChatClient.class), offers);

    assertEquals(jobOffer(), ranker.loadJobOffer(OFFER_ID));
    assertThrows(NoSuchElementException.class, () -> ranker.loadJobOffer("missing"));
  }
}
