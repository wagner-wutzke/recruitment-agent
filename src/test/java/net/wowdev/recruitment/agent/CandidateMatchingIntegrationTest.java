package net.wowdev.recruitment.agent;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.CandidateRanker;
import net.wowdev.recruitment.agent.service.RankedCandidate;
import org.junit.jupiter.api.Test;
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
  @Autowired private MockMvc mockMvc;
  @MockitoBean private CandidateRanker candidateRanker;

  @Test
  void returnsRankedModelCandidateThroughMatchingApi() throws Exception {
    String candidateName = "Model Returned Candidate";
    when(candidateRanker.loadJobOffer(JOB_OFFER_ID))
        .thenReturn(
            new JobOffer(
                JOB_OFFER_ID,
                "Java Platform Engineer",
                "Build reliable cloud services with Java and distributed systems",
                "Spring Boot, Kubernetes, and event processing experience",
                "Remote",
                "Remote"));
    when(candidateRanker.rank(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new RankedCandidate(
                    candidateName,
                    CANDIDATE_EMAIL,
                    0.87,
                    List.of("Java", "Spring Boot"),
                    List.of("Delivered reliable cloud services."))));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobOfferId\":\"" + JOB_OFFER_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].candidateName").value(candidateName))
        .andExpect(jsonPath("$[0].candidateEmail").value(CANDIDATE_EMAIL))
        .andExpect(jsonPath("$[0].rankingPosition").value(1))
        .andExpect(jsonPath("$[0].matchingProbability").value(0.87))
        .andExpect(jsonPath("$[0].matchedSkills[0]").value("Java"))
        .andExpect(jsonPath("$[0].matchedSkills[1]").value("Spring Boot"))
        .andExpect(
            jsonPath("$[0].relevantWorkExperiences[0]")
                .value("Delivered reliable cloud services."));
  }

  @Test
  void mapsInvalidExplanationResponseToExistingMatchingFailure() throws Exception {
    when(candidateRanker.loadJobOffer(JOB_OFFER_ID))
        .thenReturn(
            new JobOffer(
                JOB_OFFER_ID,
                "Java Platform Engineer",
                "Build reliable cloud services with Java and distributed systems",
                "Spring Boot, Kubernetes, and event processing experience",
                "Remote",
                "Remote"));
    when(candidateRanker.rank(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new RankedCandidate(
                    "Model Returned Candidate", CANDIDATE_EMAIL, 0.87, null, List.of())));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobOfferId\":\"" + JOB_OFFER_ID + "\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CANDIDATE_MATCHING_FAILED"));
  }
}
