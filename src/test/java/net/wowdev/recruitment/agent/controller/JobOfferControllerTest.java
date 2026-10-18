package net.wowdev.recruitment.agent.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.JobOfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

  private static String offerWithEnhancementFieldsJson() {
    return "{\"jobName\":\"Backend Engineer\",\"jobDescription\":\"Build services\",\"requiredQualifications\":\"Java\",\"location\":\"Dublin\",\"allocation\":\"Remote\",\"responsibilities\":\"Lead platform work\",\"benefits\":\"Health coverage\",\"companyName\":\"Example Corp\",\"niceToHave\":\"Kubernetes experience\",\"compensation\":\"Competitive salary\"}";
  }

  private static JobOffer offerWithEnhancementFields(String id) {
    return new JobOffer(
        id,
        "Backend Engineer",
        "Build services",
        "Java",
        "Dublin",
        "Remote",
        "Lead platform work",
        "Health coverage",
        "Example Corp",
        "Kubernetes experience",
        "Competitive salary");
  }

  private static void assertEnhancementFields(JobOffer jobOffer) {
    assertEquals("Lead platform work", jobOffer.responsibilities());
    assertEquals("Health coverage", jobOffer.benefits());
    assertEquals("Example Corp", jobOffer.companyName());
    assertEquals("Kubernetes experience", jobOffer.niceToHave());
    assertEquals("Competitive salary", jobOffer.compensation());
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

    mockMvc
        .perform(get("/api/job-offers"))
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

    mockMvc
        .perform(get("/api/job-offers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void returnsTheSpecifiedUnavailableErrorWhenRetrievalFails() throws Exception {
    when(jobOfferService.findAll()).thenThrow(new IllegalStateException("database unavailable"));

    mockMvc
        .perform(get("/api/job-offers"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("JOB_OFFERS_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Job offers could not be loaded."));
  }

  @Test
  void createsOfferAndReturnsCreatedResponse() throws Exception {
    JobOffer saved = offerWithEnhancementFields("new-id");
    when(jobOfferService.create(any(JobOffer.class))).thenReturn(saved);

    mockMvc
        .perform(
            post("/api/job-offers")
                .contentType("application/json")
                .content(offerWithEnhancementFieldsJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("new-id"))
        .andExpect(jsonPath("$.jobName").value("Backend Engineer"))
        .andExpect(jsonPath("$.responsibilities").value("Lead platform work"))
        .andExpect(jsonPath("$.benefits").value("Health coverage"))
        .andExpect(jsonPath("$.companyName").value("Example Corp"))
        .andExpect(jsonPath("$.niceToHave").value("Kubernetes experience"))
        .andExpect(jsonPath("$.compensation").value("Competitive salary"));

    ArgumentCaptor<JobOffer> capturedOffer = ArgumentCaptor.forClass(JobOffer.class);
    verify(jobOfferService).create(capturedOffer.capture());
    assertEnhancementFields(capturedOffer.getValue());
  }

  @Test
  void rejectsInvalidOfferRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/job-offers")
                .contentType("application/json")
                .content(
                    "{\"jobName\":\" \",\"jobDescription\":\"x\",\"requiredQualifications\":\"x\",\"location\":\"x\",\"allocation\":\"Remote\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_JOB_OFFER"));
  }

  @Test
  void rejectsUnknownAllocation() throws Exception {
    mockMvc
        .perform(
            post("/api/job-offers")
                .contentType("application/json")
                .content(validOfferJson().replace("Remote", "Flexible")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatesExistingOffer() throws Exception {
    JobOffer saved = offerWithEnhancementFields("offer-1");
    when(jobOfferService.update(eq("offer-1"), any(JobOffer.class))).thenReturn(saved);

    mockMvc
        .perform(
            put("/api/job-offers/offer-1")
                .contentType("application/json")
                .content(offerWithEnhancementFieldsJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("offer-1"))
        .andExpect(jsonPath("$.responsibilities").value("Lead platform work"))
        .andExpect(jsonPath("$.benefits").value("Health coverage"))
        .andExpect(jsonPath("$.companyName").value("Example Corp"))
        .andExpect(jsonPath("$.niceToHave").value("Kubernetes experience"))
        .andExpect(jsonPath("$.compensation").value("Competitive salary"));

    ArgumentCaptor<JobOffer> capturedOffer = ArgumentCaptor.forClass(JobOffer.class);
    verify(jobOfferService).update(eq("offer-1"), capturedOffer.capture());
    assertEnhancementFields(capturedOffer.getValue());
  }

  @Test
  void returnsNotFoundWhenUpdatingUnknownOffer() throws Exception {
    when(jobOfferService.update(eq("missing"), any(JobOffer.class)))
        .thenThrow(new java.util.NoSuchElementException("missing"));

    mockMvc
        .perform(
            put("/api/job-offers/missing")
                .contentType("application/json")
                .content(validOfferJson()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("JOB_OFFER_NOT_FOUND"));
  }

  @Test
  void keepsSaveFailuresDistinct() throws Exception {
    when(jobOfferService.create(any(JobOffer.class)))
        .thenThrow(new IllegalStateException("database unavailable"));

    mockMvc
        .perform(post("/api/job-offers").contentType("application/json").content(validOfferJson()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("JOB_OFFER_SAVE_FAILED"));
  }
}
