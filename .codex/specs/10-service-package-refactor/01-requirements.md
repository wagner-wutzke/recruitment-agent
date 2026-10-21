# Feature Spec: Organize Service Classes by Responsibility

- **Status:** [ ] Draft | [ ] Approved for Implementation | [x] In Implementation | [ ] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-21

## 1. Objective and Context

Organize the flat `service` package into feature-oriented subpackages so resume, job-offer, and
candidate-matching responsibilities are easier to locate and maintain. This is a structural-only
refactor; application behavior and external contracts must remain unchanged.

## 2. Business Rules

- Group classes by business capability, not by technical layer alone.
- Keep existing top-level packages for controllers, configuration, repositories, entities, and API
  DTOs unchanged.
- Do not change service behavior, API schemas, persistence, prompt behavior, or configuration keys.

## 3. Functional Requirements

- Move candidate matching orchestration, retrieval, evidence assembly, map evaluation, final
  reduction, ranking, and matching configuration to `service.matching`.
- Move matching request/result models and evidence records to `service.matching.model`.
- Move resume upload, storage, PDF indexing/normalization, and resume configuration to
  `service.resume`.
- Move resume operation values to `service.resume.model`.
- Move `JobOfferService` to `service.joboffer`.
- Update production and test package declarations and imports to the new locations.

## 4. Non-Functional Requirements

- Preserve Spring component scanning and configuration-properties registration.
- Preserve package-private access where it is intentionally used by tests by moving those tests with
  their implementation packages.
- Keep dependencies one-way where practical: job-offer and resume packages must not depend on
  candidate-matching implementation classes.

## 5. Acceptance Criteria

- **AC1:** All classes are located in the agreed responsibility subpackages.
- **AC2:** Main and test source compile with no references to moved types at the old package paths.
- **AC3:** Existing behavior is unchanged; the full Maven suite passes.
- **AC4:** Browser behavior remains unchanged; focused and full browser tests are run and any known
  unrelated baseline failure is recorded.
- **AC5:** `git diff --check` passes.

## 6. Error Cases

- Missing imports or component registration after a move: build/tests fail and must be corrected.
- Package-private access crossing a package boundary: move the corresponding tests or keep the
  collaborator in the implementation package; do not widen APIs solely for tests.

## 7. Out of Scope

- Renaming classes or changing public method signatures.
- Moving controllers, repositories, entities, configuration classes, or API DTOs.
- Changing runtime behavior or product requirements.
