package net.wowdev.recruitment.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.wowdev.recruitment.agent.RecruitmentAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = RecruitmentAgentApplication.class,
    properties = {
      "spring.ai.vectorstore.chroma.collection-name=resume-task-3-1-check",
      "spring.ai.vectorstore.chroma.initialize-schema=true",
      "spring.ai.openai.api-key=test-only-not-a-real-openai-key"
    })
class ResumeVectorStoreConfigurationTest {

  @Autowired private EmbeddingModel embeddingModel;
  @Autowired private VectorStore vectorStore;

  @Test
  void embedsLocallyAndReplacesCandidateDocumentsInChroma() {
    float[] embedding = embeddingModel.embed("Local resume embedding smoke test");
    String candidateEmail = "task-3-1@example.test";
    UUID oldDocumentId = UUID.randomUUID();
    UUID newDocumentId = UUID.randomUUID();
    Document oldDocument =
        new Document(
            "Old resume describes legacy database support.",
            Map.of("candidateEmail", candidateEmail, "documentId", oldDocumentId.toString()));
    Document newDocument =
        new Document(
            "Replacement resume describes Java application development.",
            Map.of("candidateEmail", candidateEmail, "documentId", newDocumentId.toString()));

    try {
      vectorStore.add(List.of(oldDocument));
      vectorStore.add(List.of(newDocument));
      vectorStore.delete(
          new Filter.Expression(
              Filter.ExpressionType.EQ,
              new Filter.Key("documentId"),
              new Filter.Value(oldDocumentId.toString())));

      List<Document> results =
          vectorStore.similaritySearch(
              SearchRequest.builder()
                  .query("Replacement resume describes Java application development.")
                  .topK(10)
                  .similarityThreshold(0)
                  .build());

      assertEquals(384, embedding.length);
      assertTrue(
          results.stream()
              .anyMatch(
                  result ->
                      candidateEmail.equals(result.getMetadata().get("candidateEmail"))
                          && newDocumentId.toString()
                              .equals(result.getMetadata().get("documentId"))));
      assertTrue(
          results.stream()
              .noneMatch(
                  result -> oldDocumentId.toString().equals(result.getMetadata().get("documentId"))));
    } finally {
      vectorStore.delete(List.of(oldDocument.getId(), newDocument.getId()));
    }

  }
}
