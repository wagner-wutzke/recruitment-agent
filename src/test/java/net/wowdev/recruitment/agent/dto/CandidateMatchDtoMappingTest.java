package net.wowdev.recruitment.agent.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMatch;
import org.junit.jupiter.api.Test;

class CandidateMatchDtoMappingTest {

  @Test
  void mapsCandidateExplanationFieldsToApiResponse() {
    CandidateMatch match =
        new CandidateMatch(
            "Ada Lovelace",
            "ada@example.com",
            1,
            0.91,
            List.of("Java"),
            List.of("Built backend services"));

    assertEquals(
        new CandidateMatchResponse(
            "Ada Lovelace",
            "ada@example.com",
            1,
            0.91,
            List.of("Java"),
            List.of("Built backend services")),
        CandidateMatchResponse.from(match));
  }
}
