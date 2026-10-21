package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateFinalResponse;
import net.wowdev.recruitment.agent.service.matching.model.CandidateFinalResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceBackedClaim;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceCitation;
import net.wowdev.recruitment.agent.service.matching.model.RankedCandidate;
import net.wowdev.recruitment.agent.service.matching.model.ResumeEvidenceChunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateFinalReducerTest {

  private static final JobOffer OFFER =
      new JobOffer("offer-1", "Engineer", "Build systems", "Java", "Brazil", "Remote",
          "Reliability", null, null, "Kubernetes", null);

  @Test
  void sortsMapScoresSelectsShortlistAndReturnsFinalProbabilities() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateFinalResponse.class))
        .thenReturn(
            new CandidateFinalResponse(
                List.of(
                    finalResult("b@example.com", 0.87, "chunk-b"),
                    finalResult("a@example.com", 0.91, "chunk-a"))));
    CandidateMatchingPipelineProperties properties = new CandidateMatchingPipelineProperties();
    properties.setShortlistSize(2);
    CandidateFinalReducer reducer = new CandidateFinalReducer(chatClient, properties);
    ReflectionTestUtils.setField(
        reducer, "finalPrompt", new ClassPathResource("prompts/candidate-ranking-final.txt"));
    List<CandidateResumeEvidence> candidates = candidates();
    List<CandidateMapResult> mapResults =
        List.of(mapResult("c@example.com", 80, "chunk-c"),
            mapResult("b@example.com", 90, "chunk-b"),
            mapResult("a@example.com", 90, "chunk-a"));

    List<RankedCandidate> ranked = reducer.reduce(OFFER, candidates, mapResults);

    assertEquals(List.of("a@example.com", "b@example.com"),
        ranked.stream().map(RankedCandidate::candidateEmail).toList());
    assertEquals("Candidate a", ranked.getFirst().candidateName());
    assertEquals(0.91, ranked.getFirst().matchingProbability());
    ArgumentCaptor<String> requestText = ArgumentCaptor.forClass(String.class);
    verify(request).user(requestText.capture());
    assertTrue(requestText.getValue().contains("a@example.com"));
    assertTrue(requestText.getValue().contains("b@example.com"));
    assertTrue(!requestText.getValue().contains("c@example.com"));
    assertTrue(requestText.getValue().contains("complete evidence a"));
  }

  @Test
  void returnsEmptyWithoutCallingModelWhenNoCandidateIsShortlisted() {
    ChatClient chatClient = mock(ChatClient.class);
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(chatClient, new CandidateMatchingPipelineProperties());

    assertEquals(List.of(), reducer.reduce(OFFER, List.of(), List.of()));
  }

  @Test
  void capsFinalResultsAtThree() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateFinalResponse.class))
        .thenReturn(
            new CandidateFinalResponse(
                List.of(
                    finalResult("a@example.com", 0.96, "chunk-a"),
                    finalResult("b@example.com", 0.95, "chunk-b"),
                    finalResult("c@example.com", 0.94, "chunk-c"),
                    finalResult("d@example.com", 0.93, "chunk-d"))));
    CandidateMatchingPipelineProperties properties = new CandidateMatchingPipelineProperties();
    properties.setShortlistSize(4);
    CandidateFinalReducer reducer = new CandidateFinalReducer(chatClient, properties);
    ReflectionTestUtils.setField(
        reducer, "finalPrompt", new ClassPathResource("prompts/candidate-ranking-final.txt"));
    List<CandidateResumeEvidence> fourCandidates =
        List.of("a", "b", "c", "d").stream()
            .map(
                email ->
                    new CandidateResumeEvidence(
                        email + "@example.com",
                        "Candidate " + email,
                        "resume-" + email,
                        List.of(new ResumeEvidenceChunk("chunk-" + email, "evidence " + email))))
            .toList();
    List<CandidateMapResult> scores =
        List.of("a", "b", "c", "d").stream()
            .map(email -> mapResult(email + "@example.com", 90, "chunk-" + email))
            .toList();

    List<RankedCandidate> result = reducer.reduce(OFFER, fourCandidates, scores);

    assertEquals(3, result.size());
    assertEquals(List.of("a@example.com", "b@example.com", "c@example.com"),
        result.stream().map(RankedCandidate::candidateEmail).toList());
  }

  @Test
  void sendsOnlyTheDefaultTopThreeCandidatesToFinalModel() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateFinalResponse.class))
        .thenReturn(
            new CandidateFinalResponse(
                List.of(
                    finalResult("a@example.com", 0.96, "chunk-a"),
                    finalResult("b@example.com", 0.95, "chunk-b"),
                    finalResult("c@example.com", 0.94, "chunk-c"))));
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(chatClient, new CandidateMatchingPipelineProperties());
    ReflectionTestUtils.setField(
        reducer, "finalPrompt", new ClassPathResource("prompts/candidate-ranking-final.txt"));
    List<CandidateResumeEvidence> fourCandidates =
        List.of("a", "b", "c", "d").stream()
            .map(
                email ->
                    new CandidateResumeEvidence(
                        email + "@example.com",
                        "Candidate " + email,
                        "resume-" + email,
                        List.of(
                            new ResumeEvidenceChunk(
                                "chunk-" + email, "complete evidence " + email))))
            .toList();
    List<CandidateMapResult> scores =
        List.of(
            mapResult("a@example.com", 96, "chunk-a"),
            mapResult("b@example.com", 95, "chunk-b"),
            mapResult("c@example.com", 94, "chunk-c"),
            mapResult("d@example.com", 93, "chunk-d"));

    List<RankedCandidate> result = reducer.reduce(OFFER, fourCandidates, scores);

    assertEquals(3, result.size());
    ArgumentCaptor<String> requestText = ArgumentCaptor.forClass(String.class);
    verify(request).user(requestText.capture());
    assertTrue(requestText.getValue().contains("a@example.com"));
    assertTrue(requestText.getValue().contains("b@example.com"));
    assertTrue(requestText.getValue().contains("c@example.com"));
    assertFalse(requestText.getValue().contains("d@example.com"));
  }

  @Test
  void rejectsFinalResultsWithInvalidProbabilityOrUnsupportedEvidenceReference() {
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    List<CandidateResumeEvidence> shortlist = candidates().subList(0, 1);

    assertThrows(
        IllegalStateException.class,
        () -> reducer.validate(
            new CandidateFinalResponse(List.of(finalResult("a@example.com", 1.1, "chunk-a"))),
            shortlist));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.validate(
            new CandidateFinalResponse(List.of(finalResult("a@example.com", 0.8, "unknown"))),
            shortlist));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.validate(
            new CandidateFinalResponse(List.of(new CandidateFinalResult("a@example.com", 0.8,
                List.of(new EvidenceBackedClaim("Invented skill", "chunk-a", "fabricated quote")),
                List.of(), List.of(new EvidenceCitation("chunk-a", "evidence"))))),
            shortlist));
  }

  @Test
  void reportsSafeValidationCategoryWhenProbabilityEvidenceIsUnsupported() {
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    CandidateFinalResult result =
        new CandidateFinalResult(
            "a@example.com", 0.8, List.of(), List.of(),
            List.of(new EvidenceCitation("chunk-a", "private quote not in source")));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> reducer.validate(new CandidateFinalResponse(List.of(result)), candidates().subList(0, 1)));

    assertTrue(exception.getMessage().contains("probability evidence"));
    assertFalse(exception.getMessage().contains("a@example.com"));
    assertFalse(exception.getMessage().contains("private quote"));
  }

  @Test
  void acceptsFinalClaimsAndProbabilityEvidenceWithWhitespaceOnlyDifferences() {
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    CandidateResumeEvidence candidate =
        new CandidateResumeEvidence(
            "a@example.com", "Candidate a", "ra",
            List.of(new ResumeEvidenceChunk("chunk-a", "Java\nexpert with 5 years experience")));
    CandidateFinalResult result =
        new CandidateFinalResult(
            "a@example.com", 0.8,
            List.of(new EvidenceBackedClaim("Java expert", "chunk-a", "Java expert")),
            List.of(new EvidenceBackedClaim("5 years experience", "chunk-a", "5 years experience")),
            List.of(new EvidenceCitation("chunk-a", "Java expert with 5 years experience")));

    reducer.validate(new CandidateFinalResponse(List.of(result)), List.of(candidate));
  }

  @Test
  void finalPromptDefinesCompleteStructuredResponseSchema() throws Exception {
    String prompt =
        new ClassPathResource("prompts/candidate-ranking-final.txt")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

    assertTrue(prompt.contains("JSON only with this exact envelope"));
    assertTrue(prompt.contains("\"candidateEmail\""));
    assertTrue(prompt.contains("\"matchingProbability\""));
    assertTrue(prompt.contains("\"matchedSkills\""));
    assertTrue(prompt.contains("\"relevantWorkExperiences\""));
    assertTrue(prompt.contains("\"probabilityEvidence\""));
    assertTrue(prompt.contains("\"chunkId\""));
    assertTrue(prompt.contains("\"quote\""));
  }

  @Test
  void rejectsMissingOrDuplicateMapResultsAndMapResultsOutsideCandidatePool() {
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    List<CandidateResumeEvidence> pool = candidates();

    assertThrows(
        IllegalStateException.class,
        () -> reducer.shortlist(pool, List.of(mapResult("a@example.com", 90, "chunk-a"))));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.shortlist(
            List.of(pool.getFirst()), List.of(mapResult("other@example.com", 90, "chunk-x"))));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.shortlist(
            List.of(pool.getFirst()),
            List.of(mapResult("a@example.com", 90, "chunk-a"), mapResult("A@example.com", 80, "chunk-a"))));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.shortlist(List.of(pool.getFirst()), List.of(mapResult("a@example.com", 101, "chunk-a"))));
  }

  @Test
  void rejectsNullMalformedUnknownAndDuplicateFinalResults() {
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    List<CandidateResumeEvidence> shortlist = candidates().subList(0, 1);

    assertThrows(IllegalStateException.class, () -> reducer.validate(null, shortlist));
    assertThrows(
        IllegalStateException.class,
        () ->
            reducer.validate(
                new CandidateFinalResponse(java.util.Collections.singletonList(null)), shortlist));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.validate(
            new CandidateFinalResponse(List.of(finalResult("unknown@example.com", 0.8, "chunk-x"))),
            shortlist));
    assertThrows(
        IllegalStateException.class,
        () -> reducer.validate(
            new CandidateFinalResponse(
                List.of(
                    finalResult("a@example.com", 0.8, "chunk-a"),
                    finalResult("A@example.com", 0.7, "chunk-a"))),
            shortlist));
  }

  @Test
  void failsClearlyWhenFinalPromptCannotBeRead() {
    ChatClient chatClient = mock(ChatClient.class);
    CandidateFinalReducer reducer =
        new CandidateFinalReducer(chatClient, new CandidateMatchingPipelineProperties());
    ReflectionTestUtils.setField(
        reducer, "finalPrompt", new FileSystemResource("/tmp/missing-final-ranking-prompt.txt"));
    List<CandidateResumeEvidence> pool = candidates().subList(0, 1);

    assertThrows(
        IllegalStateException.class,
        () -> reducer.reduce(OFFER, pool, List.of(mapResult("a@example.com", 90, "chunk-a"))));
  }

  private static CandidateFinalResult finalResult(String email, double probability, String chunkId) {
    return new CandidateFinalResult(email, probability,
        List.of(new EvidenceBackedClaim("Java", chunkId, "evidence")),
        List.of(new EvidenceBackedClaim("Build systems", chunkId, "evidence")),
        List.of(new EvidenceCitation(chunkId, "evidence")));
  }

  private static CandidateMapResult mapResult(String email, int score, String chunkId) {
    return new CandidateMapResult(email, score, List.of(), List.of(),
        List.of(new EvidenceCitation(chunkId, "complete evidence")));
  }

  private static List<CandidateResumeEvidence> candidates() {
    return List.of(
        new CandidateResumeEvidence("a@example.com", "Candidate a", "ra",
            List.of(new ResumeEvidenceChunk("chunk-a", "complete evidence a"))),
        new CandidateResumeEvidence("b@example.com", "Candidate b", "rb",
            List.of(new ResumeEvidenceChunk("chunk-b", "complete evidence b"))),
        new CandidateResumeEvidence("c@example.com", "Candidate c", "rc",
            List.of(new ResumeEvidenceChunk("chunk-c", "complete evidence c"))));
  }
}
