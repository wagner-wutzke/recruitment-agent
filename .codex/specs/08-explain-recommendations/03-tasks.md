# Task List: Display Candidate Match Explanations

- **Status:** [ ] Draft | [ ] Active | [ ] Paused | [x] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Follow-up: Stored Resume Link

- [x] Add an accessible PDF-icon link under each candidate's **Explanations** control using the
  existing `GET /api/resumes/{candidateEmail}/file` endpoint. Encode the candidate email as a URL
  path segment and verify per-candidate association, stacked placement, and icon accessibility in
  browser tests. Evidence: browser assertions verify each candidate's URL, including a `+` address
  character encoded as `%2B`, link placement beneath Explanations, accessible labeling, and icon
  CSS;
  `npm test` passes all 25 tests and `git diff --check` is clean. No backend endpoint or storage
  path was added.

## Phase 1: Extend the Ranking Response Model and Prompt

- [x] **Task 1.1:** Extend the structured candidate-ranking model and `RankedCandidate` to carry
  `matchedSkills` and `relevantWorkExperiences` as lists of strings. Preserve the existing identity
  and `[0,1]` probability fields. Add focused tests for Jackson/model mapping. Evidence:
  `OpenAiCandidateRankerTest.mapsExplanationFieldsFromStructuredJsonResponse` maps both explanation
  arrays and existing fields; `mvn -q -Dtest=OpenAiCandidateRankerTest test` passes.
- [x] **Task 1.2:** Update the configured candidate-ranking prompt to request grounded matched
  skills
  and relevant work experiences, require empty arrays where retrieved evidence does not support
  content, and retain the untrusted-source and no-invention instructions. Evidence: prompt tests
  `CandidateMatchingChatConfigurationTest.acceptsConfiguredClasspathPromptResource` asserts the
  grounding, field names, and empty-array instructions; `mvn -q
  -Dtest=CandidateMatchingChatConfigurationTest test` passes.
- [x] **Task 1.3:** Validate that each candidate ranking response has non-null explanation arrays
  while allowing empty arrays; preserve current identity and probability validation. Evidence:
  ranker tests cover null/missing arrays, empty arrays, and valid populated arrays; malformed
  results
  follow the existing matching failure path. `OpenAiCandidateRankerTest` covers null arrays,
  empty and populated arrays; `mvn -q -Dtest=OpenAiCandidateRankerTest test` passes.

## Phase 2: Carry Explanations Through the Service and API

- [x] **Task 2.1:** Extend `CandidateMatch` and `CandidateMatchResponse` with both explanation
  fields. Update mapping so the matching service preserves each candidate's arrays through
  descending-probability sorting, duplicate-email removal, and one-based position assignment.
  Evidence: service and DTO tests verify fields stay with the correct candidate and existing order,
  deduplication, positions, and probability behavior remain unchanged.
  `CandidateMatchingServiceTest`
  and `CandidateMatchDtoMappingTest` pass via `mvn -q
  -Dtest=CandidateMatchingServiceTest,CandidateMatchDtoMappingTest test`.
- [x] **Task 2.2:** Verify the existing `POST /api/candidate-matches` contract returns both
  explanation arrays without changing request shape or status/error mappings. Evidence: controller
  and integration tests assert the new response fields and existing empty/error behavior.
  `CandidateMatchingControllerTest` and `CandidateMatchingIntegrationTest` pass via `mvn -q
  -Dtest=CandidateMatchingControllerTest,CandidateMatchingIntegrationTest test`.

## Phase 3: Build the Candidate Match Results UI

- [x] **Task 3.1:** Update each candidate result block to show the ranking position at upper left,
  the **Explanations** control at upper right on the same row, candidate name/email in the center,
  and estimated match probability on the right side. Remove the visible **Matches Panel** heading
  while keeping an accessible name for the results section without adding a visible replacement
  heading. Evidence: browser tests verify values, placement hooks, no visible heading, and
  accessible
  region naming. `npm test` passes all 21 tests.
- [x] **Task 3.2:** Add the explanation modal markup and styles using the existing Bootstrap
  version.
  Include a candidate-specific title, match probability, matched-skills section, relevant-work-
  experiences section, and labelled explicit close control. Configure the existing modal policy:
  close via the explicit control, not Escape or backdrop. Evidence: browser tests verify modal
  structure, accessible labels, and close behavior. `npm test` passes all 22 tests.
- [x] **Task 3.3:** Wire each **Explanations** control to the explanation fields on its own
  candidate
  result; render all model-provided content with `textContent` or text nodes. Show the agreed empty
  state for empty explanation arrays, keep results intact after modal close, and clear results when
  another offer is selected. Preserve the selected-offer response guard against stale results.
  Evidence: browser tests cover candidate isolation, literal rendering of markup-like strings,
  empty arrays, close/list preservation, and stale-response behavior. `npm test` passes all 24
  tests.
- [x] **Task 3.4:** Make the match block layout responsive so its ranking/control row, identity,
  probability, and explanation access do not overlap or disappear on narrow viewports. Evidence:
  browser layout checks at representative desktop and narrow viewport sizes verify all fields and
  controls remain reachable. CSS contract assertions run in the browser suite at 1600px and 375px;
  rendered Chrome screenshots were also inspected at 1440px and 375px, confirming the centered
  identity, right-aligned probability, and non-overlapping narrow layout. `npm test` passes all 25
  tests.

## Phase 4: End-to-End Acceptance Coverage and Review

- [x] **Task 4.1:** Add or update end-to-end/browser coverage for showing the candidate email and
  existing probability, preserving ranking, opening the correct explanation, rendering matched
  skills and relevant experiences, and closing the modal without losing results. Evidence: browser
  tests cover AC1–3 and AC5–11. `npm test` passes all 25 tests.
- [x] **Task 4.2:** Verify API/model grounding and error behavior: explanations are requested from
  retrieved resume evidence, malformed explanation response data returns through the existing
  matching-failure behavior, and no explanation content is persisted. Evidence: ranker/API tests
  cover AC4 and the design trust boundary. `OpenAiCandidateRankerTest` validates malformed arrays;
  `CandidateMatchingIntegrationTest` verifies malformed explanations map to
  `503 CANDIDATE_MATCHING_FAILED`. Explanation records are request-scoped and no persistence
  writes were added.
- [x] **Task 4.3:** Verify removal of the visible **Matches Panel** label, retention of accessible
  region naming, and unchanged matching behavior and probabilities. Evidence: browser and service
  tests cover AC12–13. Browser tests assert the heading is absent and the results section has an
  accessible name; service tests verify ranking/probability behavior remains intact.
- [x] **Task 4.4:** Run the focused Java matching tests, browser tests, and full relevant project
  test suite; document results and have @reviewer compare every acceptance criterion with evidence.
  Do not mark this task complete until the checks pass and the reviewer approves the implementation.
  Evidence: `mvn -q test` passes 101 tests with zero failures/errors; `npm test` passes 25 tests;
  `git diff --check` is clean. The rendered match layout was reviewed in Chrome at desktop and
  narrow widths. Review report below covers AC1–13.

## Acceptance-Criteria Evidence Map

- **AC1–2:** Tasks 2.1, 3.1, and 4.1.
- **AC3:** Tasks 2.1, 3.1, and 4.1.
- **AC4, AC6–8:** Tasks 1.1–1.3, 2.1–2.2, 3.2–3.3, and 4.2.
- **AC5, AC9–11:** Tasks 3.2–3.3 and 4.1.
- **AC12:** Tasks 3.1 and 4.3.
- **AC13:** Tasks 2.1–2.2, 3.3, and 4.3–4.4.

## Review Report

- **Review status:** APPROVED.
- **Spec compliance:** The API carries grounded explanation lists without persistence; matching
  eligibility, descending probability order, ranking positions, and probabilities remain governed
  by the existing matching flow. The panel displays candidate email and probability, removes the
  visible Matches Panel title while retaining an accessible region name, and opens a candidate-
  specific modal with safe text rendering.
- **Acceptance coverage:** AC1–3 are covered by service/API/browser checks; AC4 and AC6–8 by prompt,
  ranker, API, and modal tests; AC5 and AC9–11 by candidate-specific modal and close-preservation
  tests; AC12 by accessible-region/heading assertions; AC13 by service ordering, probability, and
  browser regression tests; AC14 by per-candidate resume-link URL, placement, accessible-label, and
  PDF-icon assertions.
- **Resume-link follow-up:** Reuses `GET /api/resumes/{candidateEmail}/file`, URL-encodes the
  candidate email, and adds no storage-path exposure or backend endpoint.
- **Verification:** Maven: 101 tests, zero failures/errors. Browser suite: 25 tests, zero failures.
  Rendered Chrome review at 1440px and 375px showed no overlap and kept all result fields and
  controls reachable.
- **Limitations:** No live external LLM request was made. Grounding is enforced through the existing
  retrieval path and prompt instructions; the tests verify prompt and data-path behavior, not the
  factual accuracy of a live model response.
