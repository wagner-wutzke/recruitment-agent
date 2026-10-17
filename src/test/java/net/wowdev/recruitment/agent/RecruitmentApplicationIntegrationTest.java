package net.wowdev.recruitment.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecruitmentApplicationIntegrationTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();
  @LocalServerPort private int port;
  @MockitoBean private JobOfferRepository jobOfferRepository;

  @Test
  void startsApplicationAndServesSpaAndPopulatedApiResponse() throws Exception {
    when(jobOfferRepository.findAll())
        .thenReturn(
            List.of(
                new JobOffer(
                    "offer-1",
                    "Backend Engineer",
                    "Build backend services",
                    "Five years of Java experience",
                    "Dublin",
                    "Remote")));

    HttpResponse<String> page = get("/");
    HttpResponse<String> spaScript = get("/app.js");
    HttpResponse<String> offers = get("/api/job-offers");

    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("Recruitment made easy"));
    assertTrue(page.body().contains("id=\"offer-list\""));
    assertEquals(200, spaScript.statusCode());
    assertTrue(spaScript.body().contains("loadOffers();"));
    assertEquals(200, offers.statusCode());
    assertTrue(offers.body().contains("\"jobName\":\"Backend Engineer\""));
    assertTrue(
        offers.body().contains("\"requiredQualifications\":\"Five years of Java experience\""));
  }

  @Test
  void servesEmptyApiResponseWhenNoOffersExist() throws Exception {
    when(jobOfferRepository.findAll()).thenReturn(List.of());

    HttpResponse<String> offers = get("/api/job-offers");

    assertEquals(200, offers.statusCode());
    assertEquals("[]", offers.body());
  }

  @Test
  void servesUnavailableResponseWhenRepositoryFails() throws Exception {
    when(jobOfferRepository.findAll()).thenThrow(new IllegalStateException("database unavailable"));

    HttpResponse<String> offers = get("/api/job-offers");

    assertEquals(503, offers.statusCode());
    assertTrue(offers.body().contains("JOB_OFFERS_UNAVAILABLE"));
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
