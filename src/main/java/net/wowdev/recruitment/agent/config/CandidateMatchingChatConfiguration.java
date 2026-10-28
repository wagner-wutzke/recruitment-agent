package net.wowdev.recruitment.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Primary;

@Configuration
public class CandidateMatchingChatConfiguration {

  @Value("${candidate.matching.system-prompt-file}")
  private Resource systemPrompt;

  @Bean
  @Primary
  ChatClient candidateMatchingChatClient(ChatClient.Builder builder) {
    validateSystemPrompt();
    return builder
        .defaultSystem(systemPrompt, StandardCharsets.UTF_8)
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
  }

  @Bean
  @Qualifier("resumeMetadataChatClient")
  ChatClient resumeMetadataChatClient(ChatClient.Builder builder) {
    return builder.clone().build();
  }

  private void validateSystemPrompt() {
    try {
      if (systemPrompt.getContentAsString(StandardCharsets.UTF_8).isBlank()) {
        throw new IllegalStateException("Candidate matching system prompt file is empty.");
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not read candidate matching system prompt file: " + systemPrompt.getDescription(),
          exception);
    }
  }
}
