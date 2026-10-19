# Task List: Manage Job Position

- **Status:** [ ] Active | [ ] Paused | [x] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: API and Persistence

- [x] **Task 1.1:** Add a validated request DTO for the five required offer fields and allocation
  values (**AC10, AC16**).
- [x] **Task 1.2:** Add service create and update operations, including update-id existence
  behavior, with focused unit tests (**AC13–15**).
- [x] **Task 1.3:** Add POST and PUT controller routes, validation/error responses, and controller
  tests (**AC13–15**).

## Phase 2: Offer Selection Controls

- [x] **Task 2.1:** Add New Job Offer and conditional Edit controls below the details panel (**AC1–4
  **).
- [x] **Task 2.2:** Add accessible modal form markup with required fields, allocation choices, and
  save/cancel actions (**AC5, AC16**).

## Phase 3: Form Behavior

- [x] **Task 3.1:** Implement blank create form, prefilled edit form, and delayed required-field
  validation (**AC5, AC10–12, AC16**).
- [x] **Task 3.2:** Implement clean cancel and custom discard/continue confirmation (**AC6–9**).
- [x] **Task 3.3:** Implement API save, failure retention, list refresh, and saved-offer
  selection/details (**AC13–15**).

## Phase 4: Review

- [x] **Task 4.1:** Add browser-level coverage for each UI acceptance criterion and run the existing
  Java and JS suites.
- [x] **Task 4.2:** Review implementation against requirements/design; record approval or required
  corrections.

## Review Report

- **Spec compliance:** APPROVED for the detailed acceptance criteria. All sixteen scenarios are
  implemented. The overview mentions delete, but the scope and acceptance criteria define no delete
  behavior; delete remains out of scope as documented in the design.
- **Acceptance-criteria coverage:** Scenarios 1–16 are covered by the jsdom interaction tests and
  Java controller/service tests. `npm test` passes all eight browser tests; `mvn test -q` passes all
  Java tests.
- **Coverage:** JaCoCo reports 100% line coverage for `JobOfferService`, 100% for the changed DTOs,
  and 21/23 lines (91%) for `JobOfferController`.
- **Gaps:** No acceptance-criteria gaps found. The repository checkout has no valid Git metadata, so
  a Git diff/status review was unavailable.
- **Improvement suggestions:** None required for this feature scope.
- **Reviewer decision:** APPROVED.
