# Implementation Tasks: MapReduce and Tiered Candidate Ranking

- **Status:** [ ] Draft | [ ] Approved for Implementation | [ ] In Implementation | [x] Completed
- **Reference:** `01-requirements.md`, `02-design.md`

## Ordered Tasks

- [x] **Task 1 — Pipeline configuration and retrieval:** Add validated pipeline properties and
  environment-backed defaults, implement offer-specific Vector Store retrieval with configured
  threshold/top K, and verify property bounds plus captured search arguments. Evidence: focused
  `CandidateMatchingPipelinePropertiesTest` and `CandidateResumeRetrieverTest` pass.
- [x] **Task 2 — Candidate evidence assembly:** Resolve current resume versions, exclude invalid or
  stale chunks, deduplicate by vector document ID, group chunks by candidate, and verify the
  metadata, replacement, and grouping cases. Evidence: `CandidateEvidenceAssemblerTest` passes.
- [x] **Task 3 — Map evaluation:** Add map-stage DTOs and external prompt, batch candidate evidence,
  evaluate each batch with bounded concurrency, validate identities/scores/evidence references,
  and verify batch boundaries and failures. Evidence: `CandidateMapEvaluatorTest` passes.
- [x] **Task 3 follow-up — Tolerate unsupported optional claims safely:** Drop optional map claims
  unless their word/punctuation sequence is verifiable (with whitespace-only normalization), while
  retaining strict score, identity, and score-evidence checks. Evidence:
  `dropsUnsupportedOptionalClaimsButKeepsValidCandidateScoreAndEvidence` passes.
- [x] **Task 4 — Shortlist reduction:** Aggregate map results, deterministically sort and shortlist,
  add the final prompt and response validation, and verify shortlist-only input, final probabilities,
  and grounding references. Evidence: `CandidateFinalReducerTest` passes.
- [x] **Task 5 — API pipeline integration:** Replace the existing single-call ranker flow, resolve
  result identities from current resume records, preserve the API contract, cap to three, and verify
  empty, fewer-than-three, ordering, and failure behavior. Evidence: focused pipeline, ranker, and
  final reducer tests pass, including the four-candidate cap case.
- [x] **Task 6 — Acceptance verification:** Run the focused Java tests and full relevant build/test
  suite, inspect coverage and diffs, and record evidence for each acceptance criterion. Evidence:
  latest clean JaCoCo verify passed all 127 tests across 27 suites; line coverage for new pipeline
  classes is 92–100%; `git diff --check` passed. Scenarios 1–9 are covered by the retriever, assembler,
  map evaluator, final reducer, pipeline, property, and repository tests listed above.
- [x] **Task 7 — Tune matching defaults for 20,000-character resumes:** Align the map batch,
  shortlist, per-candidate evidence budget, similarity threshold, and legacy retrieval default;
  verify that 20,000-character evidence fits, over-budget evidence fails visibly, and the default
  final model request contains only the top three shortlisted candidates. Evidence:
  defaults are retrieval top K 120, map batch 3, shortlist 3, result limit 3, concurrency 2, and
  evidence budget 24,000 characters; matching defaults are threshold 0.5 and legacy top K 10.
  The 20,000-character assembler, over-budget, and default shortlist request tests pass; clean
  verify passed all 127 tests.
