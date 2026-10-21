# Task List: Upload and Index Resumes

- **Status:** [x] Active | [ ] Paused | [ ] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: MongoDB Persistence

- [x] **Task 1.1:** Replace JPA `ResumeEntity`/repository with a MongoDB `ResumeDocument` mapped to
  `resumes`, keyed by candidate email; cover persistence and replacement behavior (**AC2–3**).
- [x] **Task 1.2:** Remove resume-specific JPA/H2 dependencies and datasource configuration; verify
  application/test contexts use MongoDB only for metadata (**AC2, AC5**).

## Phase 2: File Retention and Download

- [x] **Task 2.1:** Retain PDFs using the safe candidate-name filename rule and MongoDB metadata;
  test rollback, collisions, and filesystem errors (**AC2–5**).
- [x] **Task 2.2:** Add `GET /api/resumes/{candidateEmail}/file` with attachment headers, 404
  behavior,
  safe path resolution, and controller/service tests (**AC7**).

## Phase 3: Local PDF Indexing

- [x] **Task 3.1:** Configure Chroma and local ONNX embeddings with the local service endpoint.
- [x] **Task 3.2:** Verify PDF extraction, configurable text splitting, per-chunk metadata,
  indexing,
  and invalid or empty PDF handling (**AC2, AC4, AC6, AC8**).
- [x] **Task 3.3:** Orchestrate create/replacement by candidate email across MongoDB, files, and
  Chroma, including failure compensation (**AC2–5**).

## Phase 4: Upload API

- [x] **Task 4.1:** Verify multipart create/update, validation, MongoDB/Chroma failures, download
  behavior, and response contracts (**AC2–5, AC7**).

## Phase 5: Frontend

- [x] **Task 5.1:** Add the Upload Resume button, modal controls, cancellation, and required-field
  validation (**AC1**).
- [x] **Task 5.2:** Verify multipart submission, success close, failure feedback, and no browser
  persistence (**AC1–5**).

## Phase 6: Integration and Review

- [x] **Task 6.1:** Run Java, browser, MongoDB, and local Chroma verification; map all acceptance
  criteria to evidence and record reviewer findings (**AC1–8**).

## Phase 7: Extract Candidate Email from PDF

- [ ] **Task 7.1:** Remove Candidate Email from the upload form and multipart API contract; extract
  the first valid email address in document order from parsed PDF text and use it as the MongoDB
  identity and Chroma metadata. Reject PDFs without a valid email. Add unit, controller, and browser
  tests for extraction, missing email, replacement, and submission without an email form field
  (**AC1–5**).
- [ ] **Task 7.2:** Run the relevant Java and browser suites, verify upload/replacement and failure
  behavior against the updated criteria, and record verification/reviewer findings (**AC1–8**).

## Phase 8: Tune Indexing Defaults for Standard Resume Length

- [x] **Task 8.1:** Align text-splitting defaults across application properties, configuration
  properties, and tests for resumes around 20,000 extracted characters; verify documented defaults
  and existing indexing behavior. Evidence: defaults are 800-token chunks, minimum 350 chars,
  minimum embed length 20, maximum 100 chunks, and separator preservation; property tests and the
  full Java suite (126 tests) pass.

## Phase 9: Normalize Extracted PDF Text

- [x] **Task 9.1:** Add a document transformer between PDF extraction and token splitting to collapse
  repeated horizontal whitespace and excessive blank lines while preserving word separation,
  section breaks, metadata, and page boundaries. Evidence: `ResumePdfIndexerTest` passes, including
  a normalization-before-indexing case.
- [x] **Task 9.2:** Run the focused indexer tests and full Java suite; record verification evidence.
  Evidence: focused indexer tests pass; clean JaCoCo verify passes all 129 tests across 27 suites,
  `ResumeWhitespaceNormalizer` has 100% line coverage, and `git diff --check` passes.

## Review Report

- **Spec compliance:** Resume metadata is stored only in MongoDB, keyed by candidate email. The
  implementation retains and downloads the renamed PDF, indexes locally embedded PDF chunks in
  ChromaDB, and binds all five text-splitter settings from Spring configuration.
- **Acceptance-criteria coverage:** AC1 is covered by jsdom open/cancel tests. AC2–3 are covered by
  Mongo repository tests and live upload/replacement integration tests. AC4–6 are covered by
  controller, PDF indexer, service compensation, local ONNX, and Chroma integration tests. AC7 is
  covered by service/controller and live attachment-download integration tests. AC8 is covered by
  default and custom property-binding tests and the configured indexer constructor.
- **Verification:** `npm test` passes all 16 browser tests. `mvn -q test` passes all 66 Java tests
  against the local MongoDB test database and Chroma service. Focused JaCoCo line coverage for
  changed resume classes is at least 90% (minimum: `ResumeFileStorage.StoredFile`, 90.0%). No
  datasource, JPA, or H2 dependency/configuration remains in the application or resume tests.
- **Gaps:** None found against the approved MongoDB-only requirements and design.
- **Improvement suggestions:** None required for this feature scope.
- **Reviewer decision:** APPROVED. Feature status: Completed.

## Follow-up Review Report

- **Spec compliance:** Pending email-from-PDF implementation.
- **Acceptance-criteria coverage:** Pending.
- **Gaps:** Pending.
- **Improvement suggestions:** Pending.
- **Reviewer decision:** Pending.
