package net.wowdev.recruitment.agent.service.resume.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeMetadataTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void representsUnknownFieldsWithNullScalarsAndEmptySkills() {
    ResumeMetadata metadata = ResumeMetadata.empty();

    assertNull(metadata.candidateName());
    assertNull(metadata.country());
    assertNull(metadata.position());
    assertEquals(List.of(), metadata.skills());
    assertNull(metadata.professionalExperience());
    assertNull(metadata.allocation());
    assertNull(metadata.relocation());
    assertNull(metadata.expectedSalary());
  }

  @Test
  void roundTripsAllCanonicalFieldsThroughStructuredJson() throws Exception {
    ResumeMetadata metadata =
        new ResumeMetadata(
            "Ada Lovelace",
            "United Kingdom",
            "Software Engineer",
            List.of("Java", "Spring"),
            "Backend development",
            "Hybrid",
            "Open to relocation",
            "GBP 90,000");

    String json = objectMapper.writeValueAsString(metadata);
    ResumeMetadata decoded = objectMapper.readValue(json, ResumeMetadata.class);

    assertEquals(metadata, decoded);
    assertNotNull(objectMapper.readTree(json).get("professionalExperience"));
  }

  @Test
  void defaultsOmittedSkillsToEmptyListWhenReadingModelOutput() throws Exception {
    ResumeMetadata decoded =
        objectMapper.readValue("{\"country\":\"Canada\"}", ResumeMetadata.class);

    assertEquals(List.of(), decoded.skills());
    assertEquals("Canada", decoded.country());
    assertNull(decoded.candidateName());
  }

  @Test
  void serializesSkillsAsJsonAndOmitsUnknownScalarMetadata() {
    ResumeMetadata metadata = new ResumeMetadata(null, "Canada", null, List.of("Java"), null, null, null, null);

    Map<String, Object> vectorMetadata = metadata.toVectorStoreMetadata();

    assertEquals("Canada", vectorMetadata.get("country"));
    assertEquals("[\"Java\"]", vectorMetadata.get("skills"));
    assertEquals(2, vectorMetadata.size());
  }

  @Test
  void preservesDisplayValuesAndAddsCaseInsensitiveFilterValues() {
    ResumeMetadata metadata =
        new ResumeMetadata(null, null, "  Senior   Java Engineer ", List.of(), null, " HYBRID ", null, null);

    Map<String, Object> vectorMetadata = metadata.toVectorStoreMetadata();

    assertEquals("  Senior   Java Engineer ", vectorMetadata.get("position"));
    assertEquals("senior java engineer", vectorMetadata.get("positionFilterValue"));
    assertEquals(" HYBRID ", vectorMetadata.get("allocation"));
    assertEquals("hybrid", vectorMetadata.get("allocationFilterValue"));
  }
}
