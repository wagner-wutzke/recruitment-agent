# Task List: Rank Candidates

- **Status:** [ ] Draft | [ ] Active | [ ] Paused | [x] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: Matching Configuration and External Prompt

- [x] **Task 1.1:** Configure `CandidateMatchingChatConfiguration` to load the configured prompt as
  a UTF-8 Spring `Resource`, reject unreadable or empty prompt files, and install the system prompt
  plus `SimpleLoggerAdvisor` at `Ordered.LOWEST_PRECEDENCE`. Keep Vector Store/advisor wiring out of
  the default ChatClient. Add configuration tests. Evidence: `CandidateMatchingChatConfigurationTest`
  covers classpath/filesystem prompts, invalid resources, and the default logger-only advisor.
- [x] **Task 1.2:** Bind and validate `candidate.matching.model` and
  `candidate.matching.similarity-threshold`; preserve defaults `gpt-6-luna` and `0.6`, property
  overrides, and the inclusive `[0,1]` threshold bounds. Test defaults, overrides, and invalid
  values. Evidence: `CandidateMatchingPropertiesTest` passes 6 tests, including application
  property placeholders and boundary/range behavior.
- [x] **Task 1.3:** Keep ranking instructions in the configured external system-prompt file. Ensure
  the prompt covers semantic job/resume comparison, all specified profile factors, untrusted source
  text, evidence-grounded candidate name/email fields, finite `[0,1]` probabilities, and an empty
  result when there is no supported match. Test the configured resource contents. Evidence:
  `CandidateMatchingChatConfigurationTest` asserts the required prompt instructions.

## Phase 2: Ranker-Owned Offer Loading and Retrieval-Augmented Ranking

- [x] **Task 2.1:** Keep `CandidateRanker.loadJobOffer` and its `JobOfferRepository` lookup in
  `OpenAiCandidateRanker`; preserve missing-offer behavior. Add/retain ranker tests for found and
  missing offers. Evidence: `loadsSelectedOfferFromMongoAndRejectsUnknownOffers` passes.
- [x] **Task 2.2:** Construct one ranker-owned Spring AI retrieval advisor, backed by the local
  `VectorStore` and configured inclusive similarity threshold; reuse and attach that instance to each
  ranking request. Spring AI 2.0.1's `QuestionAnswerAdvisor` resets its `SearchRequest` while building
  each retrieval call, discarding the configured threshold. Use the supported
  `RetrievalAugmentationAdvisor`/`VectorStoreDocumentRetriever` API, which preserves the threshold.
  Do not install retrieval on the default ChatClient. Evidence: ranker tests verify advisor type,
  repeated request attachment and instance reuse, threshold propagation, and retrieval failures.
- [x] **Task 2.3:** Build the request query from job position/name, description, required
  qualifications, responsibilities, and nice-to-have criteria. Ground the model request in advisor-
  retrieved resume evidence and ensure the model evaluates position, technical skills, country,
  city, working mode, and professional experience. Evidence: `attachesRequestRetrievalAdvisorAndSubmitsOfferCriteria`
  verifies the job fields and evaluation factors.
- [x] **Task 2.4:** Ensure retrieval with no eligible resume evidence produces an empty ranking and
  no invented candidates. Evidence: ranker tests verify empty retrieval rejection and empty result;
  browser test `communicates when the vector search returns no candidates` verifies the no-match
  message (**AC2**).
- [x] **Task 2.5:** Validate model ranking responses: reject null/malformed responses, missing or
  blank candidate names/emails, and probabilities outside `[0,1]` or non-finite values. Accept only
  identities supported by retrieved Vector Store evidence and preserve model-returned identity.
  Evidence: `rejectsNullOrMalformedStructuredResponse` and service identity tests cover validation;
  the retrieval advisor supplies the only evidence available to the model, and no identity is
  resolved from MongoDB (**AC1, AC4**).

## Phase 3: Matching Service and API

- [x] **Task 3.1:** Have `CandidateMatchingService` delegate offer loading and ranking to
  `CandidateRanker`; validate ranked values, sort probability descending, assign one-based
  positions, and preserve the LLM-returned identity rather than resolving identity from MongoDB.
  Evidence: `CandidateMatchingServiceTest` (6 tests) covers delegation, identity, ordering,
  validation, empty results, and dependency failures (**AC1, AC3–4**).
- [x] **Task 3.2:** Implement/verify `POST /api/candidate-matches` with non-blank `jobOfferId`
  validation, ranked candidate name/email/position/probability response fields, and documented
  errors for invalid requests, missing offers, and matching failures. Evidence:
  `CandidateMatchingControllerTest` (6 tests) and `CandidateMatchingIntegrationTest` (1 test)
  cover the API contract (**AC1–4**).

## Phase 4: Matches Panel

- [x] **Task 4.1:** Keep **Find Candidates** unavailable until an offer is selected; show pending
  state in the Matches Panel and replace Details during search. Render API matches in ranking order
  with position, model-returned name/email, and probability using safe text insertion. Evidence:
  browser tests cover gating, pending state, ordering, values, and safe rendering (**AC3–4**).
- [x] **Task 4.2:** Show a no-match message for empty successful results and a visible error on request
  failure without presenting failure as success. Evidence: browser tests cover empty and error states
  (**AC2**).

## Phase 5: Integration and Review

- [x] **Task 5.1:** After Tasks 1.1–2.5, run focused Java matching tests, browser tests, and the full
  Maven suite. Verify each acceptance criterion against test evidence, document unrelated failures,
  and complete the review report (**AC1–5**). Evidence: ranker tests (8), service tests (6),
  controller tests (6), integration test (1), and browser tests (21) pass; `mvn test` passes all
  reported suites with zero failures or errors.

## Acceptance-Criteria Evidence Map

- **AC1:** Tasks 1.2, 2.1–2.3, 3.1–3.2, and 5.1.
- **AC2:** Tasks 2.2, 2.4, 3.1–3.2, 4.2, and 5.1.
- **AC3:** Tasks 3.1–3.2, 4.1, and 5.1.
- **AC4:** Tasks 1.3, 2.5, 3.1–3.2, 4.1, and 5.1.
- **AC5:** Tasks 1.1, 1.3, and 5.1.

## Review Report

- **Spec compliance:** Offer loading and ranker-owned retrieval are implemented. Retrieval uses
  Spring AI 2.0.1 `RetrievalAugmentationAdvisor` because `QuestionAnswerAdvisor` in this version
  discards configured similarity thresholds when creating the retrieval request. The default
  ChatClient remains retrieval-free.
- **Acceptance-criteria coverage:** AC1–5 are covered by ranker, service, API, integration, config,
  and browser tests referenced above.
- **Gaps:** Automated tests verify returned identities are not replaced by MongoDB identities and
  the prompt limits output to retrieved evidence; they cannot independently prove an LLM follows
  those grounding instructions.
- **Improvement suggestions:** Revisit `QuestionAnswerAdvisor` if a Spring AI release exposes a
  builder path that preserves the similarity threshold.
- **Reviewer decision:** Implementation and tests pass; reviewer approval remains a separate SDD
  role decision.
