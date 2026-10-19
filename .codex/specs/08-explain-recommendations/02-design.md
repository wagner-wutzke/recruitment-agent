# Technical Design: Display Candidate Match Explanations

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Date:** 2026-10-19
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Extend the existing Spring MVC candidate-matching flow and vanilla JavaScript interface. Keep
  MongoDB and Vector Store persistence unchanged; explanation data is generated and returned with
  each matching response and is not persisted.
- Keep `CandidateMatchingController` and `POST /api/candidate-matches` as the single request path.
  `CandidateMatchingService` continues validating, sorting by `matchingProbability` descending,
  removing duplicate candidate emails, and assigning one-based ranking positions.
- Extend `RankedCandidate`, the internal `CandidateMatch`, and `CandidateMatchResponse` to carry
  the explanation fields defined in the API contract. Candidate name/email continue to come from
  the grounded LLM response. Do not look up or replace identity from MongoDB.
- Extend the existing structured LLM response and external candidate-ranking prompt with the
  explanation fields. The existing ranker-owned `RetrievalAugmentationAdvisor` remains the sole
  resume retrieval path. The model must derive explanation content from retrieved resume evidence
  and compare it with the selected offer criteria.
- Render the explanation link and result details in each candidate match block. Use a Bootstrap
  modal, consistent with the existing frontend dependency, to display the selected candidate's
  explanation. Dynamic model content must be inserted as text, not HTML.
- Render a per-candidate PDF resume link immediately below the explanation control. Reuse the
  existing `GET /api/resumes/{candidateEmail}/file` endpoint and URL-encode the email path segment;
  do not expose the configured storage directory or add a new endpoint.
- Retain an accessible section name for the results panel while removing the visible **Matches
  Panel** title. Remove the heading **Candidate matches**.

## 2. Data and API Contract

### Internal LLM response

Continue using `CandidateRankingResponse` with its `rankedCandidates` array. Each candidate item
adds these fields to the existing `candidateName`, `candidateEmail`, and `matchingProbability`:

| Field                     | Type             | Meaning                                                                        |
|---------------------------|------------------|--------------------------------------------------------------------------------|
| `matchedSkills`           | array of strings | Skills evidenced in the candidate's retrieved resume and relevant to the offer |
| `relevantWorkExperiences` | array of strings | Resume-supported experience descriptions and how they relate to the offer      |

The existing probability remains a finite decimal in the inclusive range `[0,1]`. It is the only
match score; do not add a second score field. Prompt instructions must treat offer and resume text
as untrusted data, prohibit invented candidate details, and request empty arrays where retrieved
evidence does not support a skill or relevant experience. Candidate email and name remain grounded
in retrieved evidence as required by the ranking feature.

### `POST /api/candidate-matches`

Keep the existing request:

```json
{
  "jobOfferId": "<existing-job-offer-id>"
}
```

Extend each successful response item as follows:

```json
[
  {
    "candidateName": "Candidate Name",
    "candidateEmail": "candidate@example.com",
    "rankingPosition": 1,
    "matchingProbability": 0.91,
    "matchedSkills": [
      "Java",
      "Spring Boot"
    ],
    "relevantWorkExperiences": [
      "Built Spring Boot services for a production recruitment platform."
    ]
  }
]
```

An empty top-level array continues to mean no matching candidates. The response remains backward
compatible for existing clients that ignore additional JSON fields. Do not add another endpoint,
database collection, or database migration.

### Validation and errors

- Preserve current request validation and status mappings: `400 INVALID_CANDIDATE_MATCH_REQUEST`,
  `404 JOB_OFFER_NOT_FOUND`, and `503 CANDIDATE_MATCHING_FAILED`.
- Reject a null/malformed model result, null candidate entries, missing/blank identity, invalid
  probability, or missing/null explanation arrays as an invalid ranking response. An empty array
  is valid and represents no supported evidence in that explanation category.
- Existing service sorting, deduplication, and ranking-position assignment must preserve each
  candidate's explanation fields without associating them with another candidate.

## 3. Matching Flow

1. The frontend submits the selected offer ID through the existing `POST /api/candidate-matches`
   request and shows the existing pending state.
2. `CandidateMatchingService` loads the offer through `CandidateRanker`, then calls `rank(offer)`.
3. `OpenAiCandidateRanker` submits the offer criteria with its existing retrieval advisor and the
   configured prompts. The prompt requests candidate identity, probability, matched skills, and
   relevant work experiences in the structured response.
4. The ranker validates identity, probability, and explanation fields. Invalid structured output
   follows the existing matching-failure path; no partial or invented explanation is returned.
5. The service sorts and deduplicates candidates using the existing rules, assigns positions, and
   preserves the explanation fields in each candidate result.
6. The API returns the ranked results with explanations. The frontend creates each result block
   and an **Explanations** control tied to that result's data.
7. Activating the control populates and opens the explanation modal for that candidate. Closing the
   modal leaves the current result list and rank positions intact.
8. Selecting another offer clears the previous results using the existing selection behavior. A
   pending response for an offer that is no longer selected must not repopulate the panel; retain
   the existing selected-offer identity check.

## 4. Frontend Behavior

- Preserve the existing offer-selection gate, pending, empty-result, and request-error states.
- In each candidate block, keep the ranking position at the upper left and stack the **Explanations**
  control and a PDF-icon resume link at the upper right. Display candidate name and email in the center content
  area; place the existing estimated match probability at the right side of the block. On narrow
  screens, allow the block contents to wrap without hiding or overlapping controls.
- Remove the visible **Matches Panel** heading while retaining a useful accessible name for the
  results section.
- Use a real button or link with an accessible name for **Explanations**. Populate the modal title
  with the candidate name and show the matching probability, matched skills, and relevant work
  experiences. Display a clear empty-state label for either explanation list when its array is
  empty; do not fabricate content.
- Build modal content using DOM text nodes/`textContent`; never interpret LLM-provided strings as
  HTML or Markdown. Bootstrap handles modal focus management. Provide a labelled close button and
  retain the project's existing modal policy of requiring the explicit close control rather than
  closing on Escape or backdrop click.
- Keep explanation content attached to the candidate result object and selected offer context.
  Opening one candidate's modal must not reuse fields from another candidate or a prior search.

## 5. Trust Boundaries and Privacy

- The LLM explanation is an evidence-based summary, not independently verified fact. The only
  allowed candidate evidence is resume content retrieved by the existing Vector Store advisor.
- Candidate identity must remain supported by retrieved evidence and supplied by the model, in
  accordance with the candidate-ranking design.
- Do not persist explanations or expose additional resume data beyond the skills and work
  experience summaries required by the feature.
- Render all candidate-provided and model-generated strings as text to prevent markup/script
  injection.

## 6. Out of Scope

- Changing candidate retrieval, eligibility thresholds, matching model configuration, rank ordering,
  deduplication rules, or probability calculation.
- Persisting explanations, adding a standalone explanation endpoint, or adding database schema.
- Changing the job-offer workflow or the existing no-match/error messaging beyond retaining its
  current behavior.

## 7. Verification Design

- **AC1–2:** Browser tests verify each candidate block shows the correct email and formatted
  probability in their specified areas.
- **AC3, AC13:** Service/API/browser tests verify the existing descending order, one-based
  positions,
  probabilities, and selected-offer request flow are unchanged.
- **AC4, AC6–8:** Ranker tests verify prompt instructions, response mapping, and validation for
  grounded `matchedSkills` and `relevantWorkExperiences`; API tests verify the fields and the
  unchanged probability reach the client; browser tests verify the modal content and empty-array
  presentation.
- **AC5, AC9–11:** Browser tests verify the per-candidate control opens the matching explanation,
  the modal shows only that candidate's fields, closing preserves the list, and changing offers
  cannot show stale explanation data.
- **AC12:** Browser tests verify the visible title is absent and the results region retains an
  accessible name.
- **AC14:** Browser tests verify each resume link is directly below its candidate's explanation
  control, includes an accessible PDF label/icon, and targets the existing download route with an
  encoded candidate email.
- Security-focused browser assertions verify model-provided strings render literally and do not
  create DOM elements.

## 8. Review Items

- Confirm whether empty `matchedSkills` or `relevantWorkExperiences` arrays should show an explicit
  “No supporting evidence identified” message or omit that section. This draft proposes an explicit
  empty-state label.
- Confirm the proposed modal close policy (explicit close button only) against the desired recruiter
  interaction. It follows the current offer/resume modal behavior.
- Confirm the proposed API field names and the interpretation of each experience string before
  implementation.
