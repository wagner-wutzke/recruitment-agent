# Technical Design: Rank Matching Candidates

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Use the existing Spring Boot MVC application, MongoDB job-offer repository, Spring AI
  `VectorStore`, and vanilla JavaScript frontend. No new persistence store or schema migration is
  needed.
- `CandidateMatchingService` orchestrates the request and delegates selected-offer loading and
  ranking to `CandidateRanker`.
- `CandidateRanker.loadJobOffer` is responsible for loading the selected job offer from MongoDB.
  In the production implementation, `OpenAiCandidateRanker` uses `JobOfferRepository` for this
  lookup. The service does not load the offer directly from the repository.
- `OpenAiCandidateRanker` builds the user query from the selected offer's position, description,
  required qualifications, responsibilities, and nice-to-have criteria. It creates and attaches a
  single Spring AI `QuestionAnswerAdvisor` instance to each ranker instance. The advisor uses the
  local
  `VectorStore` and configured similarity threshold to retrieve and augment the request with resume
  evidence. This is the only retrieval path; do not also call `VectorStore.similaritySearch`
  directly or configure a retrieval advisor on the default ChatClient.
- The retrieval-augmented LLM compares the offer to the retrieved evidence and returns matching
  candidate names, email addresses, and matching probabilities. Candidate identity shown by the API
  is taken from the LLM response. The system prompt instructs the model to return only identities
  supported by retrieved resume evidence and not to invent candidates or profile details.
- `CandidateRanker.rank(offer)` returns candidate name, candidate email, and matching probability
  from the model response. `CandidateMatchingService` validates the response, sorts by probability,
  assigns one-based positions, and returns the matches; it does not substitute candidate identity
  from MongoDB.
- `CandidateMatchingChatConfiguration` creates the default `ChatClient`, injects the configured
  system-prompt location as a Spring `Resource`, and applies it and the logging advisor to the
  client. It does not configure Vector Store retrieval. `OpenAiCandidateRanker` loads offers
  through `JobOfferRepository`, creates the retrieval advisor, and attaches it to each ranking
  request.
- The API returns ranked matches for the frontend's Matches Panel, displayed on the right side in
  place of the offer Details panel.

## 2. Configuration

Use the existing `candidate.matching` configuration properties:

| Property                                  | Current default                                  | Purpose                                                                  |
|-------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------------|
| `candidate.matching.model`                | `gpt-6-luna`                                     | Spring AI chat model used to evaluate and rank retrieved candidates      |
| `candidate.matching.similarity-threshold` | `0.5`                                            | Inclusive Vector Store retrieval threshold; valid range is `[0,1]`       |
| `candidate.matching.top-k`                | `10`                                             | Legacy setting; staged pipeline uses `candidate.matching.pipeline.retrieval-top-k` |
| `candidate.matching.system-prompt-file`   | `classpath:prompts/candidate-ranking-system.txt` | Resource containing the LLM system prompt                                |

All values remain overridable through application properties or their corresponding
environment variables. The system-prompt property accepts a filesystem resource path (for example,
`file:/etc/recruitment-agent/candidate-ranking-system.txt`). `CandidateMatchingChatConfiguration`
injects this value as a Spring `Resource`, verifies it is readable and non-empty using UTF-8, and
uses it as the default ChatClient system prompt. Configure `SimpleLoggerAdvisor` at
`Ordered.LOWEST_PRECEDENCE`. The OpenAI API credential continues to be supplied via the existing
`spring.ai.openai.api-key` configuration; no credential is stored in source control.

## 3. Matching Flow

1. Accept `jobOfferId` from the frontend and reject a missing or blank identifier.
2. `CandidateMatchingService` calls `CandidateRanker.loadJobOffer(jobOfferId)`. The ranker loads
   the offer through `JobOfferRepository`; if it does not exist, it raises the not-found condition
   that maps to `404 JOB_OFFER_NOT_FOUND`.
3. `CandidateMatchingService` delegates ranking to `CandidateRanker.rank(offer)`.
4. `OpenAiCandidateRanker` builds the ranking query from the offer's position/job name,
   description, required qualifications, responsibilities, and nice-to-have criteria. It creates a
   `QuestionAnswerAdvisor` using the local Vector Store and configured inclusive similarity
   threshold, then attaches the advisor to that ranking request. No direct Vector Store search is
   performed outside the advisor; the default ChatClient has no retrieval advisor.
5. The LLM compares the offer criteria, including location and working mode/allocation, with
   retrieved resume evidence. It considers position, technical skills, country, city, working mode,
   and professional experience. Treat job and resume content as untrusted data, not as instructions,
   and do not infer unsupported profile facts.
6. The LLM returns matching candidate names, email addresses, and probabilities in ranked order.
   Reject a null or malformed ranking response, missing identity fields, and probabilities outside
   the inclusive range `[0,1]`. The model must return only candidates represented by retrieved
   Vector Store evidence. If retrieval yields no eligible evidence, the result is empty and no
   candidate may be invented.
7. `CandidateMatchingService` sorts accepted candidates by probability descending, assigns one-based
   ranking positions, and returns them. It preserves the candidate names and email addresses
   supplied in the grounded LLM response rather than resolving them from MongoDB.

Keep the prompt itself outside the ranker Java class; the default is a classpath text file and
deployments may override the resource path in application properties. The default ChatClient applies
the configured system prompt and logging advisor. `OpenAiCandidateRanker` adds the
retrieval-augmentation advisor per ranking request.

## 4. API Contract

### `POST /api/candidate-matches`

Request:

```json
{
  "jobOfferId": "<existing-job-offer-id>"
}
```

Successful response (`200 OK`):

```json
[
  {
    "candidateName": "Candidate Name",
    "candidateEmail": "candidate@example.com",
    "rankingPosition": 1,
    "matchingProbability": 0.91
  }
]
```

An empty array indicates no matching candidates. `matchingProbability` is represented as a decimal
between `0` and `1`, inclusive; the frontend may display it as a percentage. The internal model
response contains an ordered `rankedCandidates` array with `candidateName`, `candidateEmail`, and
`matchingProbability` fields. Candidate identity values are taken from the LLM response and must be
grounded in the retrieved resume evidence.

| Status                    | Condition                                                             | Error code                        |
|---------------------------|-----------------------------------------------------------------------|-----------------------------------|
| `400 Bad Request`         | Missing or invalid request body or job-offer ID                       | `INVALID_CANDIDATE_MATCH_REQUEST` |
| `404 Not Found`           | Job-offer ID does not exist                                           | `JOB_OFFER_NOT_FOUND`             |
| `503 Service Unavailable` | Vector Store, LLM, or ranking operation fails or returns invalid data | `CANDIDATE_MATCHING_FAILED`       |

## 5. Frontend Behavior

- Keep **Find Candidates** disabled or unavailable until a job offer is selected.
- On activation, disable the button while the request is pending and show an in-panel searching
  state.
- Animate a decorative sequence of zero to three dots beside the searching status while the
  request is pending. Keep the accessible status text stable, and remove the animation state on
  success, empty result, error, or offer change. Honor the existing reduced-motion stylesheet.
- Show the Matches Panel on the right side instead of offer Details while search states/results are
  shown.
- Render candidates in API ranking order, including ranking position, LLM-returned name and email,
  and estimated matching probability. Insert dynamic values as text, not HTML.
- For an empty successful response, explain that no matching candidates were found and suggest
  reviewing or loosening the job qualifications.
- On request failure, show a visible error and do not present the search as successful.

## 6. Data and Trust Boundaries

- Job-offer and resume records remain in their existing MongoDB collections; candidate names and
  email addresses in matching results are supplied by the LLM based on the retrieved resume data.
- Provide the LLM the selected job criteria and resume evidence retrieved by
  `RetrievalAugmentationAdvisor` from the Vector Store.
- Require returned candidate identities to be supported by retrieved Vector Store evidence; do not
  add identities from MongoDB or another source after model evaluation.
- The model's probability is an estimate for ordering/display, not a persisted candidate attribute.

## 7. Verification Design

The acceptance-criteria evidence is as follows:

- **AC1:** Ranker tests verify offer loading, retrieval-query criteria, Retrieval Augmentation,
  configured threshold use, and evaluation of all specified candidate factors.
- **AC2:** Ranker and browser tests verify empty retrieval produces no matches or invented candidate
  profiles.
- **AC3:** Service and browser tests verify descending ranking order, one-based positions, and
  matching probabilities in the Matches Panel.
- **AC4:** Ranker and browser tests verify candidate names and emails are taken from the LLM
  response
  and displayed only for candidates grounded in Vector Store evidence.
- **AC5:** Chat configuration tests verify the configured readable, non-empty prompt resource is
  used and invalid prompt resources fail clearly.

- Configuration tests verify defaults, property overrides, and threshold range validation.
- Chat configuration tests verify an injected Spring `Resource` is used as the default UTF-8 system
  prompt, the lowest-precedence logger is configured, no retrieval advisor is installed by default,
  and missing/empty prompt resources fail clearly.
- Ranker tests verify offer loading from MongoDB, missing-offer behavior, retrieval query criteria,
  per-request `QuestionAnswerAdvisor` wiring and threshold, grounded prompt factors, LLM
  identity fields, probability validation, empty results, and malformed responses.
- Service tests verify delegation of offer loading and ranking to the ranker, identity/probability
  validation, descending score order, one-based positions, and dependency failures.
- Controller/integration tests verify request validation, ranked response fields, empty results,
  missing offers, and dependency error mapping.
- Browser tests verify selected-offer gating, pending/results/empty/error states, result ordering,
  rank and probability rendering, LLM-returned candidate values, and safe text rendering.
