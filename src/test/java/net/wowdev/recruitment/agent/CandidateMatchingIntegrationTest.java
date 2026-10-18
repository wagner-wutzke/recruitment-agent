package net.wowdev.recruitment.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import net.wowdev.recruitment.agent.service.CandidateMatchingProperties;
import net.wowdev.recruitment.agent.service.CandidateRanker;
import net.wowdev.recruitment.agent.service.CandidateRankingResponse;
import net.wowdev.recruitment.agent.service.OpenAiCandidateRanker;
import net.wowdev.recruitment.agent.service.RankedCandidateReference;
import net.wowdev.recruitment.agent.service.RankedCandidateEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.ai.vectorstore.chroma.collection-name=candidate-matching-integration-test",
      "spring.ai.vectorstore.chroma.initialize-schema=true",
      "spring.ai.openai.api-key=test-only-not-a-real-openai-key"
    })
@AutoConfigureMockMvc
class CandidateMatchingIntegrationTest {

  private static final String CANDIDATE_EMAIL = "candidate-matching@example.test";
  private static final String JOB_OFFER_ID = "candidate-matching-offer";
  private static final String DOCUMENT_ID = UUID.randomUUID().toString();

  @Autowired private MockMvc mockMvc;
  @Autowired private VectorStore vectorStore;
  @MockitoBean private ResumeRepository resumeRepository;
  @MockitoBean private CandidateRanker candidateRanker;

  @AfterEach
  void cleanUpVectorStore() {
    vectorStore.delete(
        new Filter.Expression(
            Filter.ExpressionType.EQ,
            new Filter.Key("candidateEmail"),
            new Filter.Value(CANDIDATE_EMAIL)));
  }

  @Test
  void returnsRankedMongoCandidateThroughMatchingApi() throws Exception {
    String candidateName = "Matching Integration Candidate";
    when(candidateRanker.loadJobOffer(JOB_OFFER_ID))
        .thenReturn(
            new JobOffer(
                JOB_OFFER_ID,
                "Java Platform Engineer",
                "Build reliable cloud services with Java and distributed systems",
                "Spring Boot, Kubernetes, and event processing experience",
                "Remote",
                "Remote"));
    when(resumeRepository.findById(CANDIDATE_EMAIL))
        .thenReturn(
            Optional.of(
                new ResumeDocument(
                    CANDIDATE_EMAIL,
                    candidateName,
                    "Matching_Integration_Candidate.pdf",
                    Instant.now(),
                    DOCUMENT_ID)));
    when(candidateRanker.rank(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new RankedCandidateEmail(CANDIDATE_EMAIL, 0.87)));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobOfferId\":\"" + JOB_OFFER_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].candidateName").value(candidateName))
        .andExpect(jsonPath("$[0].candidateEmail").value(CANDIDATE_EMAIL))
        .andExpect(jsonPath("$[0].rankingPosition").value(1))
        .andExpect(jsonPath("$[0].matchingProbability").value(0.87));
  }

  @Test
  void retrievesResumeEvidenceFromLocalChromaAndRanksItWithoutExternalChat() {
    String resumeText =
        "Java platform engineer experienced in distributed systems, Spring Boot, Kubernetes, "
            + "event processing, and reliable cloud services.";
    vectorStore.add(
        List.of(
            new Document(
                resumeText,
                Map.of(
                    "candidateEmail", CANDIDATE_EMAIL,
                    "documentId", DOCUMENT_ID,
                    "fileName", "Matching_Integration_Candidate.pdf"))));
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(
            new CandidateRankingResponse(
                List.of(new RankedCandidateReference("candidate-1", 0.87))));
    OpenAiCandidateRanker ranker =
        new OpenAiCandidateRanker(
            chatClient,
            mock(JobOfferRepository.class),
            vectorStore,
            new CandidateMatchingProperties());

    assertEquals(
        List.of(new RankedCandidateEmail(CANDIDATE_EMAIL, 0.87)),
        ranker.rank(
            new JobOffer(
                JOB_OFFER_ID,
                "Java Platform Engineer",
                "Build reliable cloud services with Java and distributed systems",
                "Spring Boot, Kubernetes, and event processing experience",
                "Remote",
                "Remote")));
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(request).user(prompt.capture());
    assertTrue(prompt.getValue().contains(resumeText));
    assertFalse(prompt.getValue().contains(CANDIDATE_EMAIL));
  }
}
