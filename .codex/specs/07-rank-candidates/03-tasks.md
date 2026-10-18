# Task List: Rank Candidates

- **Status:** [ ] Draft | [ ] Active | [ ] Paused | [x] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: Retrieval-Augmented Chat Configuration

- [x] **Task 1.1:** Configure `CandidateMatchingChatConfiguration` with a single
  `RetrievalAugmentationAdvisor` backed by the local `VectorStore` and the configured inclusive
  `candidate.matching.similarity-threshold`; retain the configured system prompt and lowest-priority
  logger. Add focused configuration tests for advisor type, vector-store wiring, threshold defaults
  and overrides, and ensure no parallel direct-search advisor is configured (**AC1–2, AC5**).
  Evidence: `CandidateMatchingChatConfigurationTest` (4 tests) passes, verifying advisor wiring,
  the `0.6` default and `0.82` override are propagated to the retriever, classpath prompt loading,
  and invalid prompt rejection. The full Maven test compilation now succeeds; the latest focused
  matching suite passes. The only full-suite failure is the unrelated resume-upload assertion
  documented under Task 5.1.

## Phase 2: Ranker Request and Response Contract

- [x] **Task 2.1:** Keep selected-offer loading in `OpenAiCandidateRanker`; build the request query
  from position, description, required qualifications, responsibilities, and nice-to-have criteria,
  then use the configured ChatClient default system prompt and retrieval advisor. Remove direct
  `VectorStore.similaritySearch` and per-request retrieval-advisor duplication. Test offer lookup,
  request content, advisor-backed invocation, and retrieval failures (**AC1–2**). Evidence:
  `OpenAiCandidateRankerTest` (5 tests) and `CandidateMatchingChatConfigurationTest` (4 tests) pass;
  the ranker now has no Vector Store dependency and relies on the configured ChatClient default
  advisors. Maven test compilation and `git diff --check` pass.
- [x] **Task 2.2:** Change the model response contract to ranked candidate name, email, and
  probability
  values. Validate required identity fields and finite probabilities in `[0,1]`; ensure prompt
  criteria cover position, technical skills, country, city, working mode, and professional
  experience, and constrain identities to retrieved evidence. Test response mapping and malformed
  responses (**AC1, AC3–4**). Evidence: `OpenAiCandidateRankerTest` (5 tests) passes for response
  fields, required identity validation, probability boundaries/invalid values, empty results, and
  retrieval failure propagation. The external ranking prompt now requests only evidence-grounded
  names/emails and probabilities; Maven test compilation passes.

## Phase 3: Matching Service and API

- [x] **Task 3.1:** Have `CandidateMatchingService` delegate offer loading and ranking to
  `CandidateRanker`, validate ranked model values, sort by probability descending, and assign
  one-based positions without resolving candidate identity from MongoDB. Add service tests for
  delegation, validation, ordering, empty results, and failures (**AC1–4**). Evidence:
  `CandidateMatchingServiceTest` (6 tests) passes; matching returns the LLM-supplied identity,
  deduplicates candidate emails after sorting, and does not inject or call `ResumeRepository`.
- [x] **Task 3.2:** Align `POST /api/candidate-matches` response and error mapping with the design;
  cover successful LLM-returned identity fields, empty results, invalid requests, missing offers,
  and ranking dependency failures with controller/integration tests (**AC1–4**). Evidence:
  `CandidateMatchingControllerTest` (6 tests) and `CandidateMatchingIntegrationTest` (1 test) pass;
  the integration response uses the model-returned name/email, and request errors map to the
  documented 400/404/503 responses.

## Phase 4: Matches Panel

- [x] **Task 4.1:** Ensure the Matches Panel renders API-returned names/emails, ranking position,
  and probability in ranking order using safe text insertion; verify selected-offer, pending, and
  result states with browser tests (**AC3–4**). Evidence: browser test
  `searches the selected job offer and displays matching candidates safely` verifies API order,
  displayed identity/rank/probability, and text-only insertion; the pending-state test verifies
  selected-offer gating and in-panel searching. `npm test` passes (21 tests).
- [x] **Task 4.2:** Verify empty retrieval/results display no candidates and a clear no-match
  message;
  verify failures do not appear successful. Add/update browser tests (**AC2**). Evidence: browser
  tests `communicates when the vector search returns no candidates` and `shows a visible search
  error instead of successful results when the API fails` pass; `npm test` passes (21 tests).

## Phase 5: Integration and Review

- [x] **Task 5.1:** Run focused Java and browser suites, verify each acceptance criterion against
  implementation and test evidence, document unrelated failures, and record reviewer findings
  (**AC1–5**). Evidence: matching configuration, ranker, service, controller, and integration tests
  pass; `npm test` passes all 21 browser tests; `git diff --check` passes. Full `mvn -q test`
  executes 96 tests with 96 passing, no failures or errors. The previously failing resume-upload
  test fixture was corrected so the no-email PDF no longer appends an email address.

## Acceptance-Criteria Evidence Map

- **AC1:** Tasks 1.1, 2.1–2.2, 3.1–3.2, and 5.1.
- **AC2:** Tasks 1.1, 2.1, 2.2, 3.1–3.2, 4.2, and 5.1.
- **AC3:** Tasks 2.2, 3.1–3.2, 4.1, and 5.1.
- **AC4:** Tasks 2.2, 3.1–3.2, 4.1, and 5.1.
- **AC5:** Tasks 1.1 and 5.1.

## Review Report

- **Spec compliance:** Matching implementation follows the current requirements and design:
  retrieval is configured once through `RetrievalAugmentationAdvisor`, the configured threshold is
  propagated, the ranker supplies selected-offer criteria, model-returned candidate identities are
  validated and probability-ranked, and the UI renders values as text.
- **Acceptance-criteria coverage:** AC1–AC5 have matching Java/configuration and browser evidence as
  mapped above. Matching-specific controller, service, ranker, configuration, integration, and
  browser tests pass.
- **Gaps:** No test failures remain. The external LLM is mocked; no live model call was made.
- **Improvement suggestions:** None required for the verified scope.
- **Reviewer decision:** APPROVED.
