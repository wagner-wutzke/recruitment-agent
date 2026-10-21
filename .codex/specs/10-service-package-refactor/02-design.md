# Technical Design: Service Package Organization

- **Status:** [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Package Layout

```text
service/
├── matching/
│   ├── CandidateMatchingService, CandidateRanker, OpenAiCandidateRanker
│   ├── CandidateRankingPipeline, CandidateResumeRetriever, CandidateEvidenceAssembler
│   ├── CandidateMapEvaluator, CandidateFinalReducer, matching properties
│   └── model/ (matching results, DTO-like records, and evidence records)
├── resume/
│   ├── ResumeUploadService, ResumeFileStorage, ResumePdfIndexer
│   ├── ResumeWhitespaceNormalizer, ResumeIndexingProperties, resume exceptions
│   └── model/ (ResumeDownload and ResumeUploadResult)
└── joboffer/
    └── JobOfferService
```

`ResumePdfIndexer.PreparedResume` remains nested in `ResumePdfIndexer`. `ResumeFileStorage.StoredFile`
remains nested in `ResumeFileStorage`.

## 2. Migration Steps

1. Move matching implementation and model files; update implementation and test packages/imports.
2. Move resume implementation and models; update implementation and test packages/imports.
3. Move `JobOfferService` and update its callers/tests.
4. Update application configuration imports and any API/controller/repository test imports.
5. Run Maven and npm verification, then inspect package references and diffs.

## 3. Compatibility

Spring Boot's component scan remains rooted at `net.wowdev.recruitment.agent`; moving annotated
services beneath that root preserves discovery. Configuration properties remain explicitly
registered from the application class. No endpoint, bean behavior, or serialized response changes.
