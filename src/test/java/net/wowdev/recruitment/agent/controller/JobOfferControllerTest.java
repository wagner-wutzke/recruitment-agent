package net.wowdev.recruitment.agent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import net.wowdev.recruitment.agent.document.JobOffer;
import net.wowdev.recruitment.agent.service.JobOfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class JobOfferControllerTest {

    @Mock private JobOfferService jobOfferService;

    private MockMvc mockMvc;

    private static String validOfferJson() {
        return "{\"jobName\":\"Backend Engineer\",\"jobDescription\":\"Build services\",\"requiredQualifications\":\"Java\",\"location\":\"Dublin\",\"allocation\":\"Remote\"}";
    }

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new JobOfferController(jobOfferService)).build();
    }

    @Test
    void returnsAllOffersWithTheSpecifiedResponseFields() throws Exception {
        when(jobOfferService.findAll())
                .thenReturn(
                        List.of(
                                new JobOffer(
                                        "offer-1",
                                        "Backend Engineer",
                                        "Build backend services",
                                        "Five years of Java experience",
                                        "Dublin",
                                        "Remote")));

        mockMvc.perform(get("/api/job-offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("offer-1"))
                .andExpect(jsonPath("$[0].jobName").value("Backend Engineer"))
                .andExpect(jsonPath("$[0].jobDescription").value("Build backend services"))
                .andExpect(jsonPath("$[0].requiredQualifications").value("Five years of Java experience"))
                .andExpect(jsonPath("$[0].location").value("Dublin"))
                .andExpect(jsonPath("$[0].allocation").value("Remote"));
    }

    @Test
    void returnsAnEmptyArrayWhenThereAreNoOffers() throws Exception {
        when(jobOfferService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/job-offers")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsTheSpecifiedUnavailableErrorWhenRetrievalFails() throws Exception {
        when(jobOfferService.findAll()).thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(get("/api/job-offers"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("JOB_OFFERS_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Job offers could not be loaded."));
    }

    @Test
    void createsOfferAndReturnsCreatedResponse() throws Exception {
        JobOffer saved = new JobOffer("new-id", "Backend Engineer", "Build services", "Java", "Dublin", "Remote");
        when(jobOfferService.create(any(JobOffer.class))).thenReturn(saved);

        mockMvc.perform(post("/api/job-offers")
                        .contentType("application/json")
                        .content(validOfferJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-id"))
                .andExpect(jsonPath("$.jobName").value("Backend Engineer"));
    }

    @Test
    void rejectsInvalidOfferRequest() throws Exception {
        mockMvc.perform(post("/api/job-offers")
                        .contentType("application/json")
                        .content("{\"jobName\":\" \",\"jobDescription\":\"x\",\"requiredQualifications\":\"x\",\"location\":\"x\",\"allocation\":\"Remote\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_OFFER"));
    }

    @Test
    void rejectsUnknownAllocation() throws Exception {
        mockMvc.perform(post("/api/job-offers")
                        .contentType("application/json")
                        .content(validOfferJson().replace("Remote", "Flexible")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesExistingOffer() throws Exception {
        JobOffer saved = new JobOffer("offer-1", "Backend Engineer", "Build services", "Java", "Dublin", "Hybrid");
        when(jobOfferService.update(eq("offer-1"), any(JobOffer.class))).thenReturn(saved);

        mockMvc.perform(put("/api/job-offers/offer-1")
                        .contentType("application/json")
                        .content(validOfferJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("offer-1"));
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownOffer() throws Exception {
        when(jobOfferService.update(eq("missing"), any(JobOffer.class)))
                .thenThrow(new java.util.NoSuchElementException("missing"));

        mockMvc.perform(put("/api/job-offers/missing")
                        .contentType("application/json")
                        .content(validOfferJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_OFFER_NOT_FOUND"));
    }

    @Test
    void keepsSaveFailuresDistinct() throws Exception {
        when(jobOfferService.create(any(JobOffer.class))).thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(post("/api/job-offers")
                        .contentType("application/json")
                        .content(validOfferJson()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("JOB_OFFER_SAVE_FAILED"));
    }
}
