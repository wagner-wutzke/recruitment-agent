package net.wowdev.recruitment.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateMatchingChatConfigurationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void configuresChatClientWithInjectedPromptResourceAndOrderedLogger() throws IOException {
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
    ArgumentCaptor<Advisor[]> advisorsCaptor = ArgumentCaptor.forClass(Advisor[].class);
    verify(builder).defaultSystem(resourceCaptor.capture(), charsetCaptor.capture());
    verify(builder).defaultAdvisors(advisorsCaptor.capture());
    assertSame(systemPrompt, resourceCaptor.getValue());
    assertEquals(StandardCharsets.UTF_8, charsetCaptor.getValue());
    Advisor[] advisors = advisorsCaptor.getValue();
    assertEquals(1, advisors.length);
    assertInstanceOf(SimpleLoggerAdvisor.class, advisors[0]);
    assertEquals(Ordered.LOWEST_PRECEDENCE, advisors[0].getOrder());
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

    String userPrompt =
        new ClassPathResource("prompts/candidate-ranking-user.txt")
            .getContentAsString(StandardCharsets.UTF_8);
    String normalizedUserPrompt = userPrompt.replaceAll("\\s+", " ");
    assertTrue(normalizedUserPrompt.contains("technical skills, country, city, working mode"));
    assertTrue(normalizedUserPrompt.contains("candidateName"));
    assertTrue(normalizedUserPrompt.contains("candidateEmail"));
    assertTrue(normalizedUserPrompt.contains("finite decimal from 0 to 1 inclusive"));
    assertTrue(normalizedUserPrompt.contains("matchedSkills"));
    assertTrue(normalizedUserPrompt.contains("relevantWorkExperiences"));
    assertTrue(normalizedUserPrompt.contains("must be an array of strings"));
    assertTrue(normalizedUserPrompt.contains("Do not invent skills"));
    assertTrue(
        normalizedUserPrompt.contains(
            "must provide evidence that supports the candidate's matchingProbability"));
    assertTrue(normalizedUserPrompt.contains("Return an empty array for either explanation field"));
    assertTrue(normalizedUserPrompt.contains("empty rankedCandidates array"));
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
