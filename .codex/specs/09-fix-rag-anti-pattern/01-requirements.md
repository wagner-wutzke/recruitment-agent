# Feature Spec: MapReduce and Tiered Candidate Ranking

> **Current ranking design:** This is the canonical specification for candidate ranking. It
> supersedes the earlier single-call ranking design in `.codex/specs/07-rank-candidates/` while
> preserving that feature's API and user-visible matching behavior.

- **Status:** [ ] Draft | [ ] Approved for Design | [ ] In Implementation | [x] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-20

## 1. Objective and Context

Replace the single large ranking call with a staged pipeline that evaluates retrieved resume
evidence in bounded batches, aggregates evidence by candidate, and performs final comparison only
for a small shortlist. This prevents a large candidate pool from being sent to one model request
and reduces context-window pressure while preserving evidence-grounded results. Pipeline defaults
must support complete retrieved evidence for resumes up to 20,000 characters.

The existing upload process stores `candidateEmail` and a resume `documentId` in Vector Store
metadata. The candidate email identifies a candidate; the document identifier distinguishes the
currently indexed resume from stale chunks left by replacement or partial indexing.

Pipeline:

```text
Job offer
  -> retrieve resume chunks meeting the configured similarity threshold
  -> map: score bounded batches of chunks against the offer
  -> group and reduce chunk evidence by candidate email and current document ID
  -> sort candidates in application code and select a bounded shortlist
  -> final reduce: compare only shortlisted candidate evidence and produce final ranked matches
  -> return at most three unique candidates
```

## 2. Business Rules

- Only resume evidence returned from the Vector Store and meeting the configured inclusive
  similarity threshold may be used in either ranking stage.
- Candidate email and resume document ID must come from retrieved Vector Store metadata. Candidate
  name and email returned to the recruiter must be resolved from application resume records for
  those retrieved identities; the model must not create or alter candidate identity.
- Chunks are grouped by candidate email and current resume document ID. Chunks belonging to an
  older document ID for the same candidate must not be combined with the current resume evidence.
- Multiple retrieved chunks for one candidate produce one map evaluation per candidate evidence
  group; duplicate chunks or repeated retrieval results must not produce duplicate candidates.
- The map stage returns a numeric relevance score on a documented, consistent scale and concise
  evidence references. The score is used to order the shortlist; it is not presented as the final
  match probability.
- Application code sorts map results by score in descending order. Ties are resolved
  deterministically by candidate email in ascending, case-insensitive order.
- The final stage compares shortlisted candidates using the complete available retrieved evidence
  for each candidate, performs trade-off analysis against the selected offer, and returns at most
  three unique matches.
- The final stage's `matchingProbability` remains a decimal in the inclusive range `[0,1]` and is
  the value displayed by the existing Matches Panel. The map score must not be substituted for it.
- Return fewer than three candidates when fewer than three candidates have sufficient retrieved
  evidence. Return an empty result when retrieval or evaluation yields no eligible candidate. Do not
  invent candidates or fill result slots without evidence.
- Job offer and resume content are untrusted data, not instructions to the model. All scores,
  explanations, and matched skills/work experiences must be supported by retrieved evidence.

## 3. Functional Requirements

- Build the retrieval question from the selected job offer's position, description, required
  qualifications, responsibilities, nice-to-have criteria, location, and working mode.
- Retrieve a configurable candidate pool of Vector Store resume chunks. The retrieval pool size must
  be separate from the final result limit and must permit retrieval of more than three chunks.
- Apply the configured similarity threshold to retrieval and preserve the existing behavior that a
  result exactly at the threshold is eligible.
- Exclude retrieved chunks with missing candidate email or resume document ID metadata from model
  ranking and report the invalid evidence as an observable ranking failure or diagnostic; they must
  not be assigned an inferred identity.
- Resolve the current resume document ID for each retrieved candidate using stored resume metadata
  and discard chunks that do not match it before evaluation.
- Assemble candidate evidence from that candidate's eligible chunks, preserving source association
  and avoiding duplicate text in the candidate evidence sent to the model.
- Divide candidate evidence into configurable bounded map batches. Each batch request must stay
  within the configured candidate/evidence limits and evaluate candidates independently; the model
  must not be asked to rank the entire retrieved pool in one map request.
- For each candidate, produce a structured map result containing candidate email, numeric map score,
  evidence-backed matched skills, relevant experience, and the Vector Store document ID or chunk
  references supporting the evaluation. Reject malformed map results, scores outside the configured
  scale, or identities not present in the batch input.
- Aggregate and deduplicate map results by candidate email, sort numerically in application code,
  and select no more than the configured shortlist size.
- Send only shortlisted candidates' complete available retrieved evidence and the job criteria to
  the final ranking request. The final request must not receive the full retrieved candidate pool.
- Limit each candidate's complete retrieved evidence to a configurable character budget whose
  default is at least 20,000 characters. Reject an over-budget candidate visibly rather than
  silently truncating evidence.
- Validate final candidate identities against the shortlist, resolve display identity from the
  corresponding resume records, validate final probabilities in `[0,1]`, sort final results by
  probability descending, deduplicate candidates, and return no more than three.
- Preserve the existing candidate match API response fields and frontend behavior. The endpoint
  continues to return ranking position, candidate name, candidate email, match probability, matched
  skills, and relevant work experiences for each result.
- Make retrieval pool size, map batch size, shortlist size, and final result limit configurable
  through `candidate.matching` properties with validated positive integer values. The final result
  limit defaults to `3`; configuration must not allow a value above `3` for this feature.
- Keep model prompts in external resource files so prompt changes do not require Java source edits.

## 4. Non-Functional Requirements

- Bound each map and final model request by configured candidate and evidence limits so the pipeline
  does not create a request proportional to the full Vector Store corpus.
- The default evidence character budget must allow complete retrieved evidence for an individual
  resume of at least 20,000 characters. Evidence over the configured per-candidate budget must fail
  visibly rather than be silently truncated.
- Process map batches with a bounded level of concurrency; concurrency must be configurable or
  otherwise capped to prevent uncontrolled model/API load.
- Preserve candidate identity and evidence provenance through retrieval, mapping, aggregation, and
  final reduction.
- Do not log full resume text, personally identifying candidate evidence, or model credentials.
- Fail visibly when Vector Store retrieval, resume metadata lookup, a map batch, or the final
  ranking operation fails. A failed batch must not silently produce an apparently complete ranking.
- Do not persist intermediate map scores, shortlists, or final match probabilities as candidate
  attributes.
- The existing local embedding and resume storage behavior remains unchanged.

## 5. Acceptance Criteria

### Scenario 1: Retrieve a candidate pool using all relevant offer criteria

- **Given** a selected job offer and indexed resumes
- **When** candidate matching starts
- **Then** the Vector Store retrieval question includes the offer position, description, required
  qualifications, responsibilities, nice-to-have criteria, location, and working mode
- **And** retrieval uses the configured pool size and inclusive similarity threshold
- **And** retrieval can return more than three resume chunks when eligible evidence exists

### Scenario 2: Group retrieved chunks into current candidate evidence

- **Given** retrieval returns multiple chunks for the same candidate and chunks from an older resume
  document version
- **When** candidate evidence is prepared for map evaluation
- **Then** eligible chunks are grouped by candidate email and current resume document ID
- **And** stale-version chunks, duplicate chunks, and chunks with missing identity/version metadata
  are excluded from ranking
- **And** one candidate is evaluated once per map pass regardless of the number of its chunks

### Scenario 3: Map-evaluate candidates in bounded batches

- **Given** the eligible candidate pool exceeds one configured map batch
- **When** the map stage evaluates the pool
- **Then** each request contains no more than the configured batch size of candidates
- **And** each candidate receives an independent numeric map score and evidence-backed assessment
- **And** no map request asks the model to rank the entire candidate pool at once
- **And** malformed, out-of-range, or unsupported map results cause a visible ranking failure

### Scenario 4: Sort map results and create a bounded shortlist

- **Given** valid map results for multiple unique candidates
- **When** the map stage completes
- **Then** application code sorts candidates by map score descending
- **And** tied scores use candidate email ascending without regard to case
- **And** the shortlist contains no more than the configured shortlist size
- **And** the map score is not exposed as the final displayed matching probability

### Scenario 5: Final-rank only shortlisted candidates and return the best three

- **Given** a valid shortlist
- **When** the final reduction stage runs
- **Then** its model input contains only shortlist candidates and their complete available retrieved
  evidence, plus the selected job criteria
- **And** the final stage compares trade-offs and returns evidence-grounded candidate results
- **And** application code validates shortlist membership, deduplicates, sorts by final probability
  descending, and assigns one-based positions
- **And** the API returns at most three unique candidates with the existing response fields
- **And** the displayed probability is the final `[0,1]` probability, not the map score

### Scenario 6: Return fewer than three when evidence supports fewer candidates

- **Given** fewer than three candidates have eligible retrieved evidence or valid evaluations
- **When** ranking completes
- **Then** the API returns only the supported candidates
- **And** an empty eligible pool returns an empty array without invoking a candidate-inventing
  fallback

### Scenario 7: Resolve candidate identity from application records

- **Given** a model returns a candidate identity in a map or final result
- **When** that result is accepted for the API response
- **Then** its email must match a candidate in the corresponding Vector Store evidence and current
  resume record
- **And** the displayed name and email are taken from the stored resume record
- **And** a model-generated or unsupported identity is rejected

### Scenario 8: Validate configuration and propagate stage failures

- **Given** candidate matching pipeline properties are configured
- **When** the application validates configuration
- **Then** retrieval pool size, map batch size, shortlist size, and concurrency are positive and
  bounded values
- **And** final result limit defaults to three and cannot exceed three
- **When** retrieval, any required map batch, resume lookup, or final reduction fails
- **Then** the request returns the existing candidate-matching failure behavior and does not present
  a partial ranking as complete

### Scenario 9: Keep requests bounded while supporting a 20,000-character resume

- **Given** an indexed candidate resume containing 20,000 characters and default pipeline settings
- **When** its eligible retrieved evidence is assembled
- **Then** the candidate's complete evidence fits within the default per-candidate evidence budget
- **And** evidence exceeding the configured budget fails visibly instead of being truncated
- **And** map and final model calls each receive no more than three candidates by default

## 6. Error Cases

- Invalid or absent candidate email/document ID metadata: exclude the evidence and record a
  diagnosable data-quality failure; never infer identity.
- A retrieved candidate has no matching current resume record or document ID: exclude that
  candidate's chunks from ranking.
- Duplicate chunks or duplicate map results: deduplicate by stable candidate identity and evidence
  reference before shortlist selection.
- Map response contains unknown candidate IDs/emails, missing scores/evidence fields, invalid score
  values, or malformed structured output: fail the matching request with the existing matching
  error response.
- Optional map-stage matched-skill and experience claims that are blank or cannot be verified
  against source-chunk quotes with exact word order and punctuation (allowing whitespace-only
  normalization) are discarded individually; they must not invalidate an
  otherwise valid candidate score with valid score evidence. Such claims must never be passed on
  as supported claims.
- A required map batch fails: fail the matching request; do not silently rank only successful
  batches.
- Final response contains a candidate outside the shortlist, duplicate identities, missing fields,
  unsupported claims, or probability outside `[0,1]`: reject the response as invalid.
- Final-stage citations must match the correct candidate chunk's exact word and punctuation sequence;
  whitespace-only normalization is allowed to account for PDF line wrapping. Paraphrases, changed
  words, or citations to another chunk remain invalid.
- No eligible chunks or no valid candidates after map evaluation: return an empty array.
- Invalid pipeline configuration, including non-positive limits or a final result limit greater
  than three: fail application configuration validation with a clear message.
- Vector Store, resume metadata store, or model service unavailable: use the existing visible
  candidate-matching failure behavior; do not return a partial-success state.

## 7. Out of Scope

- Changing resume upload, PDF parsing, or local embedding behavior.
- Changing the candidate match API response schema or Matches Panel layout.
- Persisting pipeline scores, explanations, or shortlist state.
- OCR, external resume enrichment, or searching candidate sources outside the existing Vector
  Store and resume records.
- Automatically tuning retrieval, batch, shortlist, similarity, or concurrency settings from
  production outcomes.
- Returning more than three candidates in the API response.
