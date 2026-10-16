package net.wowdev.recruitment.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import net.wowdev.recruitment.agent.document.JobOffer;
import net.wowdev.recruitment.agent.repository.JobOfferRepository;
import net.wowdev.recruitment.agent.service.JobOfferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobOfferServiceTest {

  @Mock private JobOfferRepository jobOfferRepository;

  @InjectMocks private JobOfferService jobOfferService;

  private static JobOffer offer(String id, String name) {
    return new JobOffer(
        id,
        name,
        "Lead a team",
        "Java experience",
        "Dublin",
        "Hybrid",
        "Own platform reliability",
        "Health coverage",
        "Example Corp",
        "Kubernetes experience",
        "Competitive salary");
  }

  @Test
  void returnsAllJobOffersFromRepository() {
    List<JobOffer> jobOffers =
        List.of(
            new JobOffer(
                "offer-1",
                "Backend Engineer",
                "Build backend services",
                "Five years of Java experience",
                "Dublin",
                "Remote"));
    when(jobOfferRepository.findAll()).thenReturn(jobOffers);

    assertEquals(jobOffers, jobOfferService.findAll());
    verify(jobOfferRepository).findAll();
  }

  @Test
  void returnsEmptyListWhenRepositoryHasNoJobOffers() {
    when(jobOfferRepository.findAll()).thenReturn(List.of());

    assertEquals(List.of(), jobOfferService.findAll());
    verify(jobOfferRepository).findAll();
  }

  @Test
  void savesNewJobOffer() {
    JobOffer jobOffer = offer(null, "Staff Engineer");
    JobOffer saved = offer("offer-2", "Staff Engineer");
    when(jobOfferRepository.save(jobOffer)).thenReturn(saved);

    assertEquals(saved, jobOfferService.create(jobOffer));
    verify(jobOfferRepository).save(jobOffer);
  }

  @Test
  void createPreservesAllEnhancementFields() {
    JobOffer jobOffer = offer(null, "Staff Engineer");
    when(jobOfferRepository.save(jobOffer)).thenReturn(jobOffer);

    JobOffer saved = jobOfferService.create(jobOffer);

    assertEquals("Own platform reliability", saved.responsibilities());
    assertEquals("Health coverage", saved.benefits());
    assertEquals("Example Corp", saved.companyName());
    assertEquals("Kubernetes experience", saved.niceToHave());
    assertEquals("Competitive salary", saved.compensation());
    verify(jobOfferRepository).save(jobOffer);
  }

  @Test
  void updatesExistingJobOfferKeepingItsId() {
    JobOffer request = offer(null, "Principal Engineer");
    JobOffer saved = offer("offer-1", "Principal Engineer");
    when(jobOfferRepository.existsById("offer-1")).thenReturn(true);
    when(jobOfferRepository.save(saved)).thenReturn(saved);

    JobOffer updated = jobOfferService.update("offer-1", request);

    assertEquals(saved, updated);
    assertEquals("Own platform reliability", updated.responsibilities());
    assertEquals("Health coverage", updated.benefits());
    assertEquals("Example Corp", updated.companyName());
    assertEquals("Kubernetes experience", updated.niceToHave());
    assertEquals("Competitive salary", updated.compensation());
    verify(jobOfferRepository).existsById("offer-1");
    verify(jobOfferRepository).save(saved);
  }

  @Test
  void rejectsUpdateForMissingJobOffer() {
    when(jobOfferRepository.existsById("missing")).thenReturn(false);

    assertThrows(
        NoSuchElementException.class,
        () -> jobOfferService.update("missing", offer(null, "Principal Engineer")));
  }
}
