package net.wowdev.recruitment.agent.service.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import net.wowdev.recruitment.agent.repository.ResumeRepository;
import net.wowdev.recruitment.agent.service.matching.model.CandidateResumeEvidence;
import net.wowdev.recruitment.agent.service.matching.model.ResumeEvidenceChunk;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class CandidateEvidenceAssembler {

  private static final System.Logger LOGGER =
      System.getLogger(CandidateEvidenceAssembler.class.getName());

  private final ResumeRepository resumeRepository;
  private final CandidateMatchingPipelineProperties pipelineProperties;

  public CandidateEvidenceAssembler(ResumeRepository resumeRepository, CandidateMatchingPipelineProperties pipelineProperties) {
    this.resumeRepository = resumeRepository;
    this.pipelineProperties = pipelineProperties;
  }

  public List<CandidateResumeEvidence> assemble(List<Document> retrievedDocuments) {
    Map<String, CandidateGroupBuilder> candidates = new LinkedHashMap<>();
    Map<String, Optional<ResumeDocument>> resumeCache = new HashMap<>();
    Set<String> seenChunkIds = new LinkedHashSet<>();
    int invalidMetadata = 0;
    int unknownOrStaleResume = 0;
    int duplicateChunks = 0;

    for (Document document : retrievedDocuments) {
      String email = metadataText(document, "candidateEmail");
      String documentId = metadataText(document, "documentId");
      String chunkId = document.getId();
      if (email == null || documentId == null || chunkId == null || chunkId.isBlank()) {
        invalidMetadata++;
        continue;
      }

      String normalizedEmail = normalizeEmail(email);
      Optional<ResumeDocument> currentResume =
          resumeCache.computeIfAbsent(
              normalizedEmail,
              key -> resumeRepository.findByCandidateEmailIgnoreCase(key));
      if (currentResume.isEmpty()
          || !documentId.equals(currentResume.get().getDocumentId())
          || document.getText() == null
          || document.getText().isBlank()) {
        unknownOrStaleResume++;
        continue;
      }
      if (!seenChunkIds.add(chunkId)) {
        duplicateChunks++;
        continue;
      }

      ResumeDocument resume = currentResume.get();
      String groupKey = normalizeEmail(resume.getCandidateEmail()) + "\u0000" + documentId;
      CandidateGroupBuilder candidate =
          candidates.computeIfAbsent(groupKey, ignored -> new CandidateGroupBuilder(resume));
      if (!candidate.seenText.add(document.getText())) {
        duplicateChunks++;
        continue;
      }
      candidate.chunks.add(new ResumeEvidenceChunk(chunkId, document.getText()));
    }

    if (invalidMetadata > 0 || unknownOrStaleResume > 0 || duplicateChunks > 0) {
      LOGGER.log(
          System.Logger.Level.WARNING,
          "Excluded Vector Store evidence chunks: invalidMetadata="
              + invalidMetadata
              + ", unknownOrStaleResume="
              + unknownOrStaleResume
              + ", duplicateChunks="
              + duplicateChunks);
    }

    List<CandidateResumeEvidence> evidence = new ArrayList<>();
    for (CandidateGroupBuilder candidate : candidates.values()) {
      long evidenceChars = candidate.chunks.stream().mapToLong(chunk -> chunk.text().length()).sum();
      if (evidenceChars > pipelineProperties.getMaxEvidenceCharsPerCandidate()) {
        throw new IllegalStateException("Candidate resume evidence exceeds configured character limit.");
      }
      evidence.add(
          new CandidateResumeEvidence(
              candidate.resume.getCandidateEmail(),
              candidate.resume.getCandidateName(),
              candidate.resume.getDocumentId(),
              candidate.chunks));
    }
    return List.copyOf(evidence);
  }

  private String metadataText(Document document, String key) {
    Object value = document.getMetadata().get(key);
    if (!(value instanceof String text) || text.isBlank()) {
      return null;
    }
    return text.trim();
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static final class CandidateGroupBuilder {

    private final ResumeDocument resume;
    private final List<ResumeEvidenceChunk> chunks = new ArrayList<>();
    private final Set<String> seenText = new LinkedHashSet<>();

    private CandidateGroupBuilder(ResumeDocument resume) {
      this.resume = resume;
    }
  }
}
