package net.wowdev.recruitment.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CandidateMatchingChatConfiguration {

  @Bean
  ChatClient candidateMatchingChatClient(ChatClient.Builder builder) {
    return builder.build();
  }
}
