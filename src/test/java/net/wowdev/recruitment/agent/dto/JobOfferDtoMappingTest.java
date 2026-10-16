package net.wowdev.recruitment.agent.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.wowdev.recruitment.agent.document.JobOffer;
import org.junit.jupiter.api.Test;

class JobOfferDtoMappingTest {

  @Test
  void mapsEnhancementFieldsFromRequestToDocumentAndResponse() {
    JobOfferRequest request =
        new JobOfferRequest(
            "Backend Engineer",
            "Build services",
            "Java experience",
            "Dublin",
            "Remote",
            "Lead platform work",
            "Health coverage",
            "Example Corp",
            "Kubernetes experience",
            "Competitive salary");

    JobOffer document = request.toDocument("offer-1");
    JobOfferResponse response = JobOfferResponse.from(document);

    assertEquals("Lead platform work", document.responsibilities());
    assertEquals("Health coverage", document.benefits());
    assertEquals("Example Corp", document.companyName());
    assertEquals("Kubernetes experience", document.niceToHave());
    assertEquals("Competitive salary", document.compensation());
    assertEquals("Lead platform work", response.responsibilities());
    assertEquals("Health coverage", response.benefits());
    assertEquals("Example Corp", response.companyName());
    assertEquals("Kubernetes experience", response.niceToHave());
    assertEquals("Competitive salary", response.compensation());
  }

  @Test
  void allowsEnhancementFieldsToBeAbsent() {
    JobOfferRequest request =
        new JobOfferRequest(
            "Backend Engineer", "Build services", "Java experience", "Dublin", "Remote",
            null, null, null, null, null);

    JobOffer document = request.toDocument("offer-1");

    assertEquals(null, document.responsibilities());
    assertEquals(null, document.benefits());
    assertEquals(null, document.companyName());
    assertEquals(null, document.niceToHave());
    assertEquals(null, document.compensation());
  }
}
