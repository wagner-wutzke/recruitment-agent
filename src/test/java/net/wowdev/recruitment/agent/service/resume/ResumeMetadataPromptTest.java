package net.wowdev.recruitment.agent.service.resume;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ResumeMetadataPromptTest {

  @Test
  void requestsGroundedMetadataAndTreatsResumeTextAsUntrusted() throws Exception {
    String prompt =
        new ClassPathResource("prompts/resume-metadata-extraction.txt")
            .getContentAsString(StandardCharsets.UTF_8);

    assertTrue(prompt.contains("untrusted data"));
    assertTrue(prompt.contains("Do not infer"));
    assertTrue(prompt.contains("candidateName"));
    assertTrue(prompt.contains("country"));
    assertTrue(prompt.contains("professionalExperience"));
    assertTrue(prompt.contains("expectedSalary"));
    assertTrue(prompt.contains("empty list"));
  }
}
