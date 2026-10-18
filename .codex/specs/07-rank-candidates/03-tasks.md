# Task List: Rank Candidates

- **Status:** [ ] Draft | [ ] Active | [ ] Paused | [x] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: Configuration and Chat Client

- [x] **Task 1.1:** Verify matching model and similarity-threshold properties, defaults
  (`gpt-6-luna`, `0.6`), overrides, inclusive `[0,1]` boundaries, and invalid-value rejection;
  add/fix focused tests (**AC1**). Evidence: `CandidateMatchingPropertiesTest` (5 tests) passed.
- [x] **Task 1.2:** Verify `CandidateMatchingChatConfiguration` loads the configured prompt path as a
  Spring `Resource`, rejects unreadable/empty prompt files, uses UTF-8 as the default system prompt,
  and registers the lowest-precedence logger; test classpath and filesystem resources (**AC4**).
  Evidence: `CandidateMatchingChatConfigurationTest` (3 tests) passed.

## Phase 2: Ranker-Owned Retrieval and Ranking

- [x] **Task 2.1:** Make `OpenAiCandidateRanker` the sole owner of selected-offer loading and
  Chroma retrieval: build the retrieval query from job name, description, required qualifications,
  responsibilities, and nice-to-have criteria; call `VectorStore.similaritySearch` using the
  configured threshold. Test query, threshold, offer lookup, missing offers, and retrieval failures
  (**AC1**). Evidence: `OpenAiCandidateRankerTest` (7 tests) and local-Chroma integration passed.
- [x] **Task 2.2:** Group retrieved chunks by candidate email, create opaque candidate references,
  and provide only those references and resume evidence plus offer criteria to the LLM. Ensure the
  prompt covers position, technical skills, country, city, working mode, and professional
  experience, treats source text as untrusted, and is skipped when retrieval has no eligible evidence.
  Test grouping, no-email documents, empty retrieval, and prompt content (**AC1, AC3**). Evidence:
  ranker unit tests and local-Chroma integration passed; email is absent from captured model input.
- [x] **Task 2.3:** Validate model response references against the ranker's retrieved-evidence map;
  reject malformed responses/probabilities and exclude unknown or duplicate references. Return only
  accepted internal candidate emails and probabilities to the service. Test malformed, ungrounded,
  duplicate, and valid rankings (**AC1, AC3**). Evidence: `OpenAiCandidateRankerTest` covers null
  response, invalid probability, unknown/duplicate references, and accepted mappings.

## Phase 3: Matching Service and API

- [x] **Task 3.1:** Make `CandidateMatchingService` delegate offer loading and ranking to the ranker
  without depending on or querying `VectorStore`; resolve returned emails from MongoDB, omit deleted
  resumes, order probabilities descending, and assign one-based positions. Test delegation,
  non-retrieval, identity resolution, ordering, empty results, and failures (**AC1–3**). Evidence:
  `CandidateMatchingServiceTest` (6 tests) passed; service has no Vector Store dependency.
- [x] **Task 3.2:** Verify `POST /api/candidate-matches` request validation and response contract;
  map invalid input to `400 INVALID_CANDIDATE_MATCH_REQUEST`, missing offers to
  `404 JOB_OFFER_NOT_FOUND`, and ranking dependencies/errors to `503 CANDIDATE_MATCHING_FAILED`.
  Add controller/integration tests (**AC1–3**). Evidence: `CandidateMatchingControllerTest` (6
  tests) and `CandidateMatchingIntegrationTest` (API flow) passed.

## Phase 4: Matches Panel

- [x] **Task 4.1:** Keep **Find Candidates** unavailable until an offer is selected; show pending
  state in the Matches Panel and replace Details while the request is active. Add browser tests
  (**AC2**). Evidence: browser tests cover selected-offer gating and pending state.
- [x] **Task 4.2:** Render ranked matches in API order with rank, MongoDB-resolved name/email, and
  matching probability, inserting dynamic values as text. Add browser tests for order, fields, and
  safe rendering (**AC2–3**). Evidence: browser tests cover API ordering, displayed rank/probability,
  identity text, and HTML-looking names rendered safely.
- [x] **Task 4.3:** Show the no-match message with qualification-review guidance for empty success;
  show visible failure feedback without presenting success on request errors. Add browser tests
  (**AC2**). Evidence: browser tests cover empty and error states.

## Phase 5: Integration and Review

- [x] **Task 5.1:** Run focused Java and browser suites, verify each acceptance criterion against
  implementation and test evidence, document unrelated failures, and record reviewer findings
  (**AC1–4**). Evidence: `mvn -q test` passed (95 tests); `npm test` passed (21 tests); local Chroma
  retrieval integration passed; JaCoCo line coverage for the changed matching units is at least
  95.6%; `git diff --check` passed.

## Acceptance-Criteria Evidence Map

- **AC1:** Tasks 1.1, 2.1–2.3, 3.1–3.2, and 5.1.
- **AC2:** Tasks 3.1–3.2, 4.1–4.3, and 5.1.
- **AC3:** Tasks 2.2–2.3, 3.1–3.2, 4.2, and 5.1.
- **AC4:** Tasks 1.2 and 5.1.

## Review Report

- **Spec compliance:** APPROVED. The matching service does not retrieve Chroma evidence; the ranker
  performs one thresholded retrieval, keeps identity mapping internal, and only sends opaque
  candidate references and resume evidence to the model.
- **Acceptance-criteria coverage:** AC1–AC4 have focused Java, browser, and local-Chroma evidence as
  mapped above.
- **Gaps:** None identified in the reviewed scope. OpenAI was intentionally mocked; no external
  model request or credential was needed for verification.
- **Improvement suggestions:** None required for this task set.
- **Reviewer decision:** APPROVED.
