package net.wowdev.recruitment.agent.service.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResponse;
import net.wowdev.recruitment.agent.service.matching.model.CandidateMapResult;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceBackedClaim;
import net.wowdev.recruitment.agent.service.matching.model.EvidenceCitation;
import net.wowdev.recruitment.agent.service.matching.model.ResumeEvidenceChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateMapEvaluatorTest {

  private static final JobOffer OFFER =
      new JobOffer(
          "offer-1", "Platform engineer", "Build cloud services", "Java", "Brazil", "Remote",
          "Own reliability", null, null, "Kubernetes", null);

  @Test
  void partitionsCandidateGroupsWithinConfiguredBatchSize() {
    CandidateMatchingPipelineProperties properties = new CandidateMatchingPipelineProperties();
    properties.setMapBatchSize(2);
    CandidateMapEvaluator evaluator = new CandidateMapEvaluator(mock(ChatClient.class), properties);

    List<List<CandidateResumeEvidence>> batches = evaluator.partition(candidates(5));

    assertEquals(List.of(2, 2, 1), batches.stream().map(List::size).toList());
  }

  @Test
  void evaluatesAllBatchesAndReturnsEveryCandidateMapResult() {
    CandidateMatchingPipelineProperties properties = new CandidateMatchingPipelineProperties();
    properties.setMapBatchSize(2);
    CandidateMapEvaluator evaluator = spy(new CandidateMapEvaluator(mock(ChatClient.class), properties));
    doAnswer(
            invocation ->
                ((List<CandidateResumeEvidence>) invocation.getArgument(1))
                    .stream()
                    .map(
                        candidate ->
                            new CandidateMapResult(
                                candidate.candidateEmail(), 80, List.of(), List.of(),
                                List.of(new EvidenceCitation(candidate.chunks().getFirst().chunkId(), "resume text"))))
                    .toList())
        .when(evaluator)
        .evaluateBatch(any(), any());

    List<CandidateMapResult> result = evaluator.evaluate(OFFER, candidates(5));

    assertEquals(5, result.size());
    verify(evaluator, org.mockito.Mockito.times(3)).evaluateBatch(any(), any());
  }

  @Test
  void returnsEmptyWithoutCreatingExecutorForEmptyPool() {
    CandidateMapEvaluator evaluator =
        new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());

    assertEquals(List.of(), evaluator.evaluate(OFFER, List.of()));
  }

  @Test
  void propagatesMapBatchFailuresAndWrapsUnexpectedCheckedCauses() {
    CandidateMapEvaluator runtimeFailing =
        spy(new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties()));
    doAnswer(invocation -> { throw new IllegalStateException("batch failed"); })
        .when(runtimeFailing)
        .evaluateBatch(any(), any());
    assertThrows(IllegalStateException.class, () -> runtimeFailing.evaluate(OFFER, candidates(1)));

    CandidateMapEvaluator checkedFailing =
        spy(new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties()));
    doAnswer(invocation -> { throw new CompletionException(new IOException("batch failed")); })
        .when(checkedFailing)
        .evaluateBatch(any(), any());
    assertThrows(IllegalStateException.class, () -> checkedFailing.evaluate(OFFER, candidates(1)));
  }

  @Test
  void evaluatesCandidateAndValidatesIdentityAndEvidenceReferences() {
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(request);
    when(request.user(anyString())).thenReturn(request);
    when(request.call()).thenReturn(response);
    when(response.entity(CandidateMapResponse.class))
        .thenReturn(
            new CandidateMapResponse(
                List.of(
                    new CandidateMapResult(
                        "one@example.com", 91,
                        List.of(new EvidenceBackedClaim("Java", "chunk-one", "resume text")),
                        List.of(new EvidenceBackedClaim("Built services", "chunk-one", "resume text")),
                        List.of(new EvidenceCitation("chunk-one", "resume text"))))));
    CandidateMapEvaluator evaluator = new CandidateMapEvaluator(chatClient, new CandidateMatchingPipelineProperties());
    ReflectionTestUtils.setField(
        evaluator, "mapPrompt", new ClassPathResource("prompts/candidate-ranking-map.txt"));

    assertEquals(91, evaluator.evaluateBatch(OFFER, List.of(candidates(1).getFirst())).getFirst().mapScore());

    org.mockito.ArgumentCaptor<String> requestText =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(request).user(requestText.capture());
    assertTrue(requestText.getValue().contains("\"candidates\""));
    assertTrue(requestText.getValue().contains("\"candidateEmail\""));
    assertTrue(requestText.getValue().contains("\"mapScore\""));
    assertTrue(requestText.getValue().contains("\"matchedSkills\""));
    assertTrue(requestText.getValue().contains("\"relevantWorkExperiences\""));
    assertTrue(requestText.getValue().contains("\"scoreEvidence\""));
    assertTrue(requestText.getValue().contains("\"chunkId\""));
    assertTrue(requestText.getValue().contains("\"quote\""));
    assertTrue(requestText.getValue().contains("\"text\""));
  }

  @Test
  void rejectsUnknownIdentityOutOfRangeScoreAndUnsupportedChunkReference() {
    assertInvalid(
        new CandidateMapResult("other@example.com", 50, List.of(), List.of(), List.of(new EvidenceCitation("chunk-one", "resume text"))));
    assertInvalid(
        new CandidateMapResult("one@example.com", 101, List.of(), List.of(), List.of(new EvidenceCitation("chunk-one", "resume text"))));
    assertInvalid(
        new CandidateMapResult("one@example.com", 50, List.of(), List.of(), List.of(new EvidenceCitation("unknown", "resume text"))));
    CandidateMapEvaluator evaluator = new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    assertThrows(
        IllegalStateException.class,
        () -> evaluator.validateResponse(new CandidateMapResponse(List.of()), List.of(candidates(1).getFirst())));
    assertThrows(
        IllegalStateException.class,
        () -> evaluator.validateResponse(null, List.of(candidates(1).getFirst())));
  }

  @Test
  void reportsSafeValidationCategoryWhenQuoteDoesNotMatchSourceEvidence() {
    CandidateMapEvaluator evaluator =
        new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    CandidateMapResult result =
        new CandidateMapResult(
            "one@example.com", 50, List.of(), List.of(),
            List.of(new EvidenceCitation("chunk-one", "private resume text not present")));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> evaluator.validateResponse(
                new CandidateMapResponse(List.of(result)), List.of(candidates(1).getFirst())));

    assertTrue(exception.getMessage().contains("score evidence"));
    assertFalse(exception.getMessage().contains("one@example.com"));
    assertFalse(exception.getMessage().contains("private resume text"));
  }

  @Test
  void acceptsScoreEvidenceWhenOnlyPdfWhitespaceWasNormalized() {
    CandidateMapEvaluator evaluator =
        new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    CandidateResumeEvidence candidate =
        new CandidateResumeEvidence(
            "one@example.com", "Candidate 0", "resume-0",
            List.of(new ResumeEvidenceChunk("chunk-one", "Java developer\nwith 5 years experience")));
    CandidateMapResult result =
        new CandidateMapResult(
            "one@example.com", 50, List.of(), List.of(),
            List.of(new EvidenceCitation("chunk-one", "Java developer with 5 years experience")));

    List<CandidateMapResult> sanitized =
        evaluator.validateAndSanitizeResponse(new CandidateMapResponse(List.of(result)), List.of(candidate));

    assertEquals(result.scoreEvidence(), sanitized.getFirst().scoreEvidence());
  }

  @Test
  void dropsUnsupportedOptionalClaimsButKeepsValidCandidateScoreAndEvidence() {
    CandidateMapEvaluator evaluator =
        new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    CandidateMapResult result =
        new CandidateMapResult(
            "one@example.com", 72,
            List.of(
                new EvidenceBackedClaim("Java expert", "chunk-one", "invented quote"),
                new EvidenceBackedClaim("Supported wording", "chunk-one", "resume text")),
            List.of(new EvidenceBackedClaim("Experience", "missing-chunk", "resume text")),
            List.of(new EvidenceCitation("chunk-one", "resume text")));

    CandidateMapResult sanitized =
        evaluator
            .validateAndSanitizeResponse(
                new CandidateMapResponse(List.of(result)), List.of(candidates(1).getFirst()))
            .getFirst();

    assertEquals(72, sanitized.mapScore());
    assertEquals(List.of("Supported wording"), sanitized.matchedSkills().stream().map(EvidenceBackedClaim::text).toList());
    assertTrue(sanitized.relevantWorkExperiences().isEmpty());
    assertEquals(1, sanitized.scoreEvidence().size());
  }

  @Test
  void failsClearlyWhenMapPromptCannotBeRead() {
    CandidateMapEvaluator evaluator =
        new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    ReflectionTestUtils.setField(
        evaluator, "mapPrompt", new FileSystemResource("/tmp/missing-candidate-map-prompt.txt"));

    assertThrows(
        IllegalStateException.class,
        () -> evaluator.evaluateBatch(OFFER, List.of(candidates(1).getFirst())));
  }

  private static void assertInvalid(CandidateMapResult result) {
    CandidateMapEvaluator evaluator = new CandidateMapEvaluator(mock(ChatClient.class), new CandidateMatchingPipelineProperties());
    assertThrows(
        IllegalStateException.class,
        () -> evaluator.validateResponse(new CandidateMapResponse(List.of(result)), List.of(candidates(1).getFirst())));
  }

  private static List<CandidateResumeEvidence> candidates(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(
            index ->
                new CandidateResumeEvidence(
                    index == 0 ? "one@example.com" : "candidate" + index + "@example.com",
                    "Candidate " + index,
                    "resume-" + index,
                    List.of(new ResumeEvidenceChunk("chunk-" + (index == 0 ? "one" : index), "resume text"))))
        .toList();
  }
}
