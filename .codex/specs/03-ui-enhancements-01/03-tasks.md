# Task List: UI Enhancements 01

- **Status:** [x] Active | [ ] Paused | [ ] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: Persistence and API Field Propagation

- [x] **Task 1.1:** Add the five optional properties to `JobOffer`, request/response DTOs, and
  focused document/DTO mapping tests (**AC1–5**).
- [x] **Task 1.2:** Preserve all five properties through service create/update and add focused
  service tests (**AC1–5**).
- [x] **Task 1.3:** Verify POST and PUT accept, persist, and return the five properties; add
  controller or integration coverage (**AC1–5, AC12**).

## Phase 2: Form and Offer List

- [x] **Task 2.1:** Add the five controls to the create/edit form, including edit prefill and save
  payload coverage (**AC1–5**).
- [x] **Task 2.2:** Display Company Name in offer entries and verify updated names appear after
  save (**AC11–12**).

## Phase 3: Details Rendering

- [x] **Task 3.1:** Render the specified Details fields as sanitized Markdown with the required
  formatting and safe links (**AC6–10**).
- [x] **Task 3.2:** Display Responsibilities, Benefits, Nice to Have, and Compensation in Details;
  cover empty and legacy-null values (**AC1–5**).
- [x] **Task 3.3:** Arrange the specified Details and modal fields vertically in the required order
  (**AC17–18**).

## Phase 4: Page Layout

- [x] **Task 4.1:** Set the responsive 1480 px content/action panel widths and 600 px Details width;
  place both actions in the dedicated panel (**AC13–15**).
- [x] **Task 4.2:** Verify action button visibility and panel containment at wide and narrow
  viewports (**AC13–16**).

## Phase 5: Review

- [x] **Task 5.1:** Run Java and browser suites, map each acceptance criterion to evidence, and
  record reviewer findings.

## Review Report

- **Spec compliance:** The implementation covers the API fields, form behavior, Markdown rendering,
  and offer-list criteria. Layout work remains: the current Details width diverges from AC14 and
  Tasks 4.1–4.2 are unchecked.
- **Acceptance-criteria coverage:** AC1–5: `JobOfferMappingTest`, `JobOfferDtoMappingTest`,
  `JobOfferServiceTest`, `JobOfferControllerTest`, and form/prefill/payload tests in
  `src/test/js/app.test.js`. AC6–10: Markdown formatting and unsafe markup/link tests in
  `src/test/js/app.test.js`. AC11–12: company name display and edited-name update test in
  `src/test/js/app.test.js`. AC13 and AC15–16: main width rule, action-panel containment, and
  selection-visibility checks in `src/test/js/app.test.js`. The existing AC14 assertion still
  expects 1024 px and must be updated to the 600 px requirement. AC17–18: field order assertions in
  `src/test/js/app.test.js`.
- **Verification:** `npm test` passes all 12 tests. The feature Java tests and
  `RecruitmentApplicationIntegrationTest` pass. `mvn -q test` runs 24 tests but fails two tests in
  `MongoConnectionPropertiesTest`, which is outside this feature; they expect
  `spring.mongodb.uri`, while the corresponding setting in `application.properties` is commented
  out.
- **Gaps:** The Details panel implementation and its current test assertion use 1024 px, while the
  aligned requirements and design specify 600 px on desktop. Tasks 4.1–4.2 remain unchecked until
  the layout and its verification are brought into line. The full Maven suite also fails the two
  unrelated Mongo URI configuration tests.
- **Improvement suggestions:** Correct the Mongo URI configuration/test mismatch in its own scope,
  complete Tasks 4.1–4.2, then rerun the complete Maven suite.
- **Reviewer decision:** APPROVAL WITHHELD pending completion of Tasks 4.1–4.2 and a passing full
  Maven suite. Feature status remains Active.
