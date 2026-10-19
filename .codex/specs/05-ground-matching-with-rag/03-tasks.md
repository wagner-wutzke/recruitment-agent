# Task List: Ground Matching with RAG

- **Status:** [ ] Draft | [x] Active | [ ] Paused | [ ] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: Matching Service

- [x] **Task 1.1:** Implement the Vector Store search service using a selected job offer's
  description
  and required qualifications; de-duplicate hits by candidate email and resolve MongoDB candidate
  metadata. Add service tests for matches, duplicates, empty hits, missing offers, and failures
  (**AC1, AC3–4**).

## Phase 2: Matching API

- [x] **Task 2.1:** Add the matching request/response DTOs and `POST /api/candidate-matches`
  controller with validation and error mapping; add controller tests (**AC1, AC3–4**).

## Phase 3: Recruiter UI

- [x] **Task 3.1:** Add **Find Candidates** beside the existing actions and implement selected-offer
  enablement plus pending/results/empty/error panel states. Add jsdom tests (**AC2–5**).

## Phase 4: Integration and Review

- [x] **Task 4.1:** Run Java and browser suites and, when local ChromaDB is available, verify a
  real similarity search; record acceptance-criteria evidence and reviewer findings (**AC1–5**).

## Review Report

- **Spec compliance:** The implementation follows the approved design. Matching queries use only
  the selected role's description and qualifications against the local Vector Store; the service
  does not use an LLM. The SPA gates the action on role selection and shows result, empty, pending,
  and error states in place of Details.
- **Acceptance-criteria coverage:** AC1: service query tests and local Chroma/API integration test.
  AC2: jsdom matching-results test. AC3: jsdom empty-results test. AC4: controller dependency-error
  test and jsdom error-state test. AC5: jsdom confirms disabled action before selection and no
  request starts.
- **Gaps:** RAG-specific Java tests (service, controller, local Chroma integration) and all 20
  browser tests pass. Full `mvn -q test` runs 80 tests but has two failures and two errors in the
  existing resume upload/repository integration tests because localhost MongoDB requires
  authentication while those tests use an unauthenticated URI. No RAG test failed.
- **Improvement suggestions:** Configure the existing Mongo integration tests with the local
  authenticated MongoDB URI, then rerun the full Maven suite.
- **Reviewer decision:** APPROVAL WITHHELD until the complete Maven suite passes; the RAG feature's
  focused Java, real-Chroma, and browser checks pass.
