package net.wowdev.recruitment.agent;

import static org.mockito.Mockito.when;
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
import net.wowdev.recruitment.agent.service.CandidateRanker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
  @MockitoBean private JobOfferRepository jobOfferRepository;
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
  void searchesLocalChromaThroughTheMatchingApi() throws Exception {
    String candidateName = "Matching Integration Candidate";
    String resumeText =
        "Java platform engineer experienced in distributed systems, Spring Boot, Kubernetes, "
            + "event processing, and reliable cloud services.";
    when(jobOfferRepository.findById(JOB_OFFER_ID))
        .thenReturn(
            Optional.of(
                new JobOffer(
                    JOB_OFFER_ID,
                    "Java Platform Engineer",
                    "Build reliable cloud services with Java and distributed systems",
                    "Spring Boot, Kubernetes, and event processing experience",
                    "Remote",
                    "Remote")));
    when(resumeRepository.findById(CANDIDATE_EMAIL))
        .thenReturn(
            Optional.of(
                new ResumeDocument(
                    CANDIDATE_EMAIL,
                    candidateName,
                    "Matching_Integration_Candidate.pdf",
                    Instant.now(),
                    DOCUMENT_ID)));
    vectorStore.add(
        List.of(
            new Document(
                resumeText,
                Map.of(
                    "candidateEmail", CANDIDATE_EMAIL,
                    "documentId", DOCUMENT_ID,
                    "fileName", "Matching_Integration_Candidate.pdf"))));
    when(candidateRanker.rank(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of("candidate-1"));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobOfferId\":\"" + JOB_OFFER_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].candidateName").value(candidateName))
        .andExpect(jsonPath("$[0].candidateEmail").value(CANDIDATE_EMAIL));
  }
}
