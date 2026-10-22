package net.wowdev.recruitment.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateMatchingChatConfigurationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void configuresChatClientWithInjectedPromptWithoutLoggingResumeContent() throws IOException {
    Path promptPath = temporaryDirectory.resolve("system-prompt.txt");
    Files.writeString(promptPath, "Use the configured candidate ranking instructions.");
    Resource systemPrompt = new FileSystemResource(promptPath);
    ChatClient.Builder builder = configuredBuilder();
    CandidateMatchingChatConfiguration configuration = configuration(systemPrompt);
    ChatClient expected = mock(ChatClient.class);
    when(builder.build()).thenReturn(expected);

    ChatClient actual = configuration.candidateMatchingChatClient(builder);

    assertSame(expected, actual);
    ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
    ArgumentCaptor<Charset> charsetCaptor = ArgumentCaptor.forClass(Charset.class);
    verify(builder).defaultSystem(resourceCaptor.capture(), charsetCaptor.capture());
    verify(builder, never()).defaultAdvisors(any(Advisor[].class));
    assertSame(systemPrompt, resourceCaptor.getValue());
    assertEquals(StandardCharsets.UTF_8, charsetCaptor.getValue());
  }

  @Test
  void failsWhenConfiguredPromptResourceIsMissingOrBlank() throws IOException {
    ChatClient.Builder builder = configuredBuilder();
    CandidateMatchingChatConfiguration missingPromptConfiguration =
        configuration(new FileSystemResource(temporaryDirectory.resolve("missing.txt")));

    assertThrows(
        IllegalStateException.class,
        () -> missingPromptConfiguration.candidateMatchingChatClient(builder));

    Path blankPromptPath = temporaryDirectory.resolve("blank.txt");
    Files.writeString(blankPromptPath, " \n");
    CandidateMatchingChatConfiguration blankPromptConfiguration =
        configuration(new FileSystemResource(blankPromptPath));

    assertThrows(
        IllegalStateException.class,
        () -> blankPromptConfiguration.candidateMatchingChatClient(builder));
  }

  @Test
  void acceptsConfiguredClasspathPromptResource() throws IOException {
    Resource systemPrompt = new ClassPathResource("prompts/candidate-ranking-system.txt");
    ChatClient.Builder builder = configuredBuilder();
    CandidateMatchingChatConfiguration configuration = configuration(systemPrompt);
    ChatClient expected = mock(ChatClient.class);
    when(builder.build()).thenReturn(expected);

    assertSame(expected, configuration.candidateMatchingChatClient(builder));
    verify(builder).defaultSystem(systemPrompt, StandardCharsets.UTF_8);

    String prompt = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
    assertTrue(prompt.contains("untrusted data"));
    assertTrue(prompt.contains("Do not infer unsupported facts or invent candidate profiles."));
    assertTrue(prompt.contains("During MAP evaluation, return one result"));
    assertTrue(prompt.contains("During FINAL evaluation, return only candidates"));

    String mapPrompt =
        new ClassPathResource("prompts/candidate-ranking-map.txt")
            .getContentAsString(StandardCharsets.UTF_8);
    assertTrue(mapPrompt.contains("integer from 0 to 100"));
    assertTrue(mapPrompt.contains("\"candidateEmail\""));
    assertTrue(mapPrompt.contains("\"matchedSkills\""));
    assertTrue(mapPrompt.contains("\"relevantWorkExperiences\""));
    assertTrue(mapPrompt.contains("\"scoreEvidence\""));
    assertTrue(mapPrompt.contains("\"chunkId\""));
    assertTrue(mapPrompt.contains("\"quote\""));
    assertTrue(mapPrompt.contains("text, chunkId, and quote"));
    String finalPrompt =
        new ClassPathResource("prompts/candidate-ranking-final.txt")
            .getContentAsString(StandardCharsets.UTF_8);
    assertTrue(finalPrompt.contains("matchingProbability"));
    assertTrue(finalPrompt.contains("finite number from 0"));
    assertTrue(finalPrompt.contains("JSON only with this exact envelope"));
  }

  @Test
  void buildsResumeMetadataClientWithoutRankingDefaults() {
    ChatClient.Builder builder = configuredBuilder();
    when(builder.clone()).thenReturn(builder);
    ChatClient expected = mock(ChatClient.class);
    when(builder.build()).thenReturn(expected);

    assertSame(expected, configuration(new ClassPathResource("prompts/candidate-ranking-system.txt"))
        .resumeMetadataChatClient(builder));

    verify(builder).clone();
    verify(builder, never()).defaultSystem(any(Resource.class), any(Charset.class));
    verify(builder, never()).defaultAdvisors(any(Advisor[].class));
  }

  private ChatClient.Builder configuredBuilder() {
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    when(builder.defaultSystem(any(Resource.class), any(Charset.class))).thenReturn(builder);
    when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
    return builder;
  }

  private CandidateMatchingChatConfiguration configuration(Resource systemPrompt) {
    CandidateMatchingChatConfiguration configuration = new CandidateMatchingChatConfiguration();
    ReflectionTestUtils.setField(configuration, "systemPrompt", systemPrompt);
    return configuration;
  }
}
