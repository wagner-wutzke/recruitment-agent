# Implementation Tasks: Service Package Organization

- **Status:** [ ] Draft | [ ] Approved for Implementation | [x] In Implementation | [ ] Completed
- **Reference:** `01-requirements.md`, `02-design.md`

## Ordered Tasks

- [x] **Task 1 — Matching package:** Move candidate matching services/configuration to
  `service.matching`, matching value records to `service.matching.model`, and move matching tests
  with package-private collaborators.
- [x] **Task 2 — Resume package:** Move resume services/configuration/exceptions to `service.resume`,
  resume operation values to `service.resume.model`, and update callers/tests.
- [x] **Task 3 — Job-offer package:** Move `JobOfferService` to `service.joboffer` and update callers
  and tests.
- [ ] **Task 4 — Verification:** Run all Maven and npm tests, inspect package references and
  configuration registration, and record results for AC1–5. `mvn -q clean test-compile` and
  `git diff --check` pass. Full Maven verification currently exposes two failures in the
  pre-existing `CandidateResumeRetrieverTest`: null vector results trigger `List.copyOf` and the
  retrieval query omits the job title. Browser tests have one unrelated existing responsive-layout
  assertion failure. These behavior changes are outside this structural refactor.
