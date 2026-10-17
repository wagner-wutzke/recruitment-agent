package net.wowdev.recruitment.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import java.util.NoSuchElementException;
import net.wowdev.recruitment.agent.service.CandidateMatch;
import net.wowdev.recruitment.agent.service.CandidateMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class CandidateMatchingControllerTest {

  @Mock private CandidateMatchingService candidateMatchingService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = standaloneSetup(new CandidateMatchingController(candidateMatchingService)).build();
  }

  @Test
  void returnsMatchingCandidates() throws Exception {
    when(candidateMatchingService.findCandidates("offer-1"))
        .thenReturn(List.of(new CandidateMatch("Ada Lovelace", "ada@example.com")));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType("application/json")
                .content("{\"jobOfferId\":\"offer-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].candidateName").value("Ada Lovelace"))
        .andExpect(jsonPath("$[0].candidateEmail").value("ada@example.com"));

    verify(candidateMatchingService).findCandidates(eq("offer-1"));
  }

  @Test
  void returnsEmptyArrayWhenThereAreNoMatches() throws Exception {
    when(candidateMatchingService.findCandidates("offer-1")).thenReturn(List.of());

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType("application/json")
                .content("{\"jobOfferId\":\"offer-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void rejectsMissingOrBlankJobOfferIds() throws Exception {
    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType("application/json")
                .content("{\"jobOfferId\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CANDIDATE_MATCH_REQUEST"));

    mockMvc
        .perform(post("/api/candidate-matches").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsNotFoundWhenJobOfferDoesNotExist() throws Exception {
    when(candidateMatchingService.findCandidates("missing"))
        .thenThrow(new NoSuchElementException("Job offer was not found."));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType("application/json")
                .content("{\"jobOfferId\":\"missing\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("JOB_OFFER_NOT_FOUND"));
  }

  @Test
  void returnsServiceUnavailableWhenMatchingDependencyFails() throws Exception {
    when(candidateMatchingService.findCandidates("offer-1"))
        .thenThrow(new IllegalStateException("Chroma unavailable"));

    mockMvc
        .perform(
            post("/api/candidate-matches")
                .contentType("application/json")
                .content("{\"jobOfferId\":\"offer-1\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CANDIDATE_MATCHING_FAILED"))
        .andExpect(
            jsonPath("$.message").value("Candidate matching could not be completed."));
  }
}
