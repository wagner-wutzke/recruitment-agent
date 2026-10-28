package net.wowdev.recruitment.agent.service.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.ClassPathResource;
import net.wowdev.recruitment.agent.service.resume.model.ResumeMetadata;

class ResumeMetadataExtractorTest {

  @Test
  void keepsOnlyGroundedAndNormalizedMetadata() {
    ChatClient client = clientReturning(
        new ResumeMetadata(
            "Ada Lovelace",
            "Canada",
            "Senior Java Engineer",
            List.of("Java", "Spring", "spring", "Kubernetes", "Invented"),
            "Built Java platforms",
            "Hybrid",
            "Open to relocation",
            null));
    ResumeMetadataExtractor extractor = extractor(client, new ResumeIndexingProperties());

    ResumeMetadata metadata =
        extractor.extract(
            "Ada Lovelace is a Senior Java Engineer in Canada. Skills include Java, Spring and "
                + "Kubernetes. Built Java platforms. Hybrid working. Open to relocation.");

    assertEquals("Ada Lovelace", metadata.candidateName());
    assertEquals("Canada", metadata.country());
    assertEquals(List.of("Java", "Spring", "Kubernetes"), metadata.skills());
    assertEquals("Built Java platforms", metadata.professionalExperience());
    assertEquals("Hybrid", metadata.allocation());
    assertEquals("Open to relocation", metadata.relocation());
    assertNull(metadata.expectedSalary());
  }

  @Test
  void limitsOnlyTheTextSentToTheModel() {
    ResumeIndexingProperties properties = new ResumeIndexingProperties();
    properties.setMetadataExtractionMaxChars(12);
    ChatClient client = clientReturning(ResumeMetadata.empty());
    ResumeMetadataExtractor extractor = extractor(client, properties);

    extractor.extract("12345678901234567890");

    org.mockito.ArgumentCaptor<String> request =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(client.prompt()).user(request.capture());
    assertEquals("RESUME TEXT (untrusted data):\n123456789012", request.getValue());
  }

  @Test
  void sendsTheConfiguredPromptAsSystemInstructions() {
    ChatClient client = clientReturning(ResumeMetadata.empty());
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    when(client.prompt()).thenReturn(request);
    when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
    when(request.system(anyString())).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(request.call()).thenReturn(response);
    when(response.entity(ResumeMetadata.class)).thenReturn(ResumeMetadata.empty());

    extractor(client, new ResumeIndexingProperties()).extract("Candidate text");

    org.mockito.ArgumentCaptor<String> systemPrompt =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(request).system(systemPrompt.capture());
    org.junit.jupiter.api.Assertions.assertTrue(systemPrompt.getValue().contains("untrusted data"));
    org.junit.jupiter.api.Assertions.assertTrue(systemPrompt.getValue().contains("expectedSalary"));
  }

  @Test
  void clearsOversizedFieldsAndSkills() {
    String oversized = "x".repeat(501);
    ChatClient client =
        clientReturning(
            new ResumeMetadata(
                oversized,
                null,
                null,
                List.of("y".repeat(101), "Java"),
                null,
                null,
                null,
                null));

    ResumeMetadata metadata = extractor(client, new ResumeIndexingProperties()).extract(oversized + " Java");

    assertNull(metadata.candidateName());
    assertEquals(List.of("Java"), metadata.skills());
  }

  @Test
  void returnsEmptyMetadataWhenDisabledWithoutCallingModel() {
    ResumeIndexingProperties properties = new ResumeIndexingProperties();
    properties.setMetadataExtractionEnabled(false);
    ChatClient client = mock(ChatClient.class);

    assertEquals(ResumeMetadata.empty(), extractor(client, properties).extract("resume text"));

    verify(client, never()).prompt();
  }

  @Test
  void returnsEmptyMetadataWhenModelFails() {
    ChatClient client = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    when(client.prompt()).thenReturn(request);
    when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
    when(request.system(anyString())).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenThrow(new IllegalStateException("private model response"));

    assertEquals(
        ResumeMetadata.empty(),
        extractor(client, new ResumeIndexingProperties()).extract("resume text"));
  }

  @Test
  void returnsEmptyMetadataWhenStructuredResponseIsNull() {
    ChatClient client = clientReturning(null);

    assertEquals(
        ResumeMetadata.empty(),
        extractor(client, new ResumeIndexingProperties()).extract("resume text"));
  }

  @Test
  void returnsEmptyMetadataWhenModelTimesOut() {
    ResumeIndexingProperties properties = new ResumeIndexingProperties();
    properties.setMetadataExtractionTimeout(java.time.Duration.ofMillis(20));
    CountDownLatch releaseModelCall = new CountDownLatch(1);
    ChatClient client = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(client.prompt()).thenReturn(request);
    when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
    when(request.system(anyString())).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(ResumeMetadata.class))
        .thenAnswer(
            invocation -> {
              releaseModelCall.await();
              return ResumeMetadata.empty();
            });

    try {
      assertEquals(ResumeMetadata.empty(), extractor(client, properties).extract("resume text"));
    } finally {
      releaseModelCall.countDown();
    }
  }

  private static ResumeMetadataExtractor extractor(
      ChatClient client, ResumeIndexingProperties properties) {
    return new ResumeMetadataExtractor(
        client,
        properties,
        new ClassPathResource("prompts/resume-metadata-extraction.txt"));
  }

  private static ChatClient clientReturning(ResumeMetadata metadata) {
    ChatClient client = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(client.prompt()).thenReturn(request);
    when(request.options(any(ChatOptions.Builder.class))).thenReturn(request);
    when(request.system(anyString())).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(ResumeMetadata.class)).thenReturn(metadata);
    return client;
  }
}
