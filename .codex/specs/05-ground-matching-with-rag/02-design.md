# Technical Design: Ground Matching with RAG

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Reuse the existing Spring Boot application, MongoDB repositories, Spring AI `VectorStore`,
  local ChromaDB collection, and local embedding model.
- Candidate matching calls the Vector Store directly; it does not call an LLM or use generated
  candidate facts.
- The existing static SPA invokes a new matching API using the selected job offer ID and displays
  the response in the existing details panel area.

## 2. API Contract

### `POST /api/candidate-matches`

Request:

```json
{ "jobOfferId": "<job-offer-id>" }
```

The service loads the job offer from MongoDB and creates a search query from its `jobDescription`
and `requiredQualifications` values. The controller returns `200 OK` with a JSON array of matched
candidate summaries. Each summary contains `candidateName` and `candidateEmail`, resolved from
MongoDB resume metadata using the candidate email attached to matching Vector Store documents.
Candidates are unique in the response even when multiple chunks match.

An empty array indicates no matches. Unknown job-offer IDs return `404 Not Found`. Vector Store or
MongoDB lookup failures return `503 Service Unavailable` with a stable matching error code and
message. Invalid/missing request data returns `400 Bad Request`.

The Vector Store uses Spring AI similarity search with its default result limit; no LLM generation
or candidate ranking stage is introduced.

## 3. Search Flow

1. The SPA enables **Find Candidates** only when a job offer is selected.
2. The SPA posts the selected offer ID to `/api/candidate-matches` and displays a pending state.
3. The backend retrieves the offer, combines its description and required qualifications, and
   searches the existing resume Vector Store.
4. The backend reads each hit's `candidateEmail` metadata, removes duplicate candidate emails, and
   resolves candidate names from `ResumeRepository`.
5. The backend returns candidate summaries. It returns an empty array when there are no usable hits.
6. On success, the SPA shows a **Search Results** panel in place of the offer Details panel. An
   empty array displays a no-matches message. On failure, it displays a visible error and does not
   present results as a successful search.

## 4. Data and Security

- No MongoDB schema or migration is required. The existing `ResumeDocument` stores candidate name
  and email, and indexed vector chunks already carry `candidateEmail` metadata.
- Do not expose resume file paths or full resume text in the matching response.
- Treat all candidate values as text in the SPA; render with `textContent`.
- Do not add an LLM client, prompt, or external network call for matching.

## 5. Frontend Behavior

- Place **Find Candidates** in the existing action panel beside Edit and New Job Offer.
- Keep it disabled until an offer is selected.
- Show pending, results, empty, and error states in the panel occupying the current Details area.
- Selecting a different offer returns the panel to that offer's Details view.

## 6. Verification Design

- Unit tests cover query construction, Vector Store search, result de-duplication, candidate metadata
  resolution, empty results, missing offers, and dependency failures.
- Controller tests cover the request/response contract and HTTP error mapping.
- jsdom tests cover button alignment/presence, disabled/enabled selection state, request payload,
  successful and empty results, search errors, and safe text rendering.
- Integration coverage verifies matching against the configured local ChromaDB collection when the
  dedicated local ChromaDB service is available.
