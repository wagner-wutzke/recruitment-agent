package net.wowdev.recruitment.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.wowdev.recruitment.agent.entity.JobOffer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

class OpenAiCandidateRankerTest {

  @Test
  void sendsJobCriteriaAndOnlyRetrievedEvidenceWithProfileFactorsAndReturnsReferences() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.system(anyString())).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class))
        .thenReturn(new CandidateRankingResponse(List.of("candidate-2", "candidate-1")));
    OpenAiCandidateRanker ranker = new OpenAiCandidateRanker(chatClient);
    JobOffer offer =
        new JobOffer(
            "offer",
            "Platform engineer",
            "Build cloud services",
            "Java and Kubernetes",
            "Brazil",
            "Remote",
            "Own service reliability",
            null,
            null,
            "Event streaming",
            null);

    List<String> result =
        ranker.rank(
            offer,
            Map.of(
                "candidate-1", List.of("Experienced backend developer in Brazil"),
                "candidate-2", List.of("Java platform specialist, remote, 8 years experience")));

    assertEquals(List.of("candidate-2", "candidate-1"), result);
    ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(request).system(system.capture());
    verify(request).user(prompt.capture());
    assertTrue(
        system.getValue().contains("position, technical skills, country, city, working mode"));
    assertTrue(system.getValue().contains("Do not infer unknown facts"));
    assertTrue(prompt.getValue().contains("Platform engineer"));
    assertTrue(prompt.getValue().contains("Java and Kubernetes"));
    assertTrue(prompt.getValue().contains("Own service reliability"));
    assertTrue(prompt.getValue().contains("Event streaming"));
    assertTrue(prompt.getValue().contains("candidate-1"));
    assertTrue(prompt.getValue().contains("Experienced backend developer in Brazil"));
    assertFalse(prompt.getValue().contains("candidateEmail"));
    assertFalse(prompt.getValue().contains("ada@example.com"));
  }

  @Test
  void rejectsAnInvalidStructuredResponse() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.system(anyString())).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateRankingResponse.class)).thenReturn(null);
    OpenAiCandidateRanker ranker = new OpenAiCandidateRanker(chatClient);

    assertThrows(
        IllegalStateException.class,
        () -> ranker.rank(new JobOffer("offer", "Role", null, null, null, null), Map.of()));
  }
}
