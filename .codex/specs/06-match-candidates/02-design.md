# Technical Design: Match Candidates

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Keep MongoDB as the source of job-offer and candidate identity metadata.
- Use the existing local Chroma VectorStore and local transformer embeddings to retrieve
  candidate resume evidence.
- Use Spring AI's OpenAI chat integration to perform semantic profile comparison and ranking over
  the retrieved evidence. The default API model is `gpt-6-luna`.
- Use the existing matching controller and SPA flow; successful results remain backend-resolved
  candidate names and email addresses. No new persistence or endpoint is required.
- Do not issue an LLM call when Vector Store retrieval returns no qualifying resume evidence.

## 2. Matching Configuration

Configure matching with Spring application properties:

| Property                                  | Default                       | Purpose                                                                 |
|-------------------------------------------|-------------------------------|-------------------------------------------------------------------------|
| `candidate.matching.model`                | `gpt-6-luna`                  | OpenAI chat model used for candidate comparison                         |
| `candidate.matching.similarity-threshold` | `0.6`                         | Minimum normalized Vector Store similarity, inclusive, in range `[0,1]` |
| `spring.ai.openai.api-key`                | `${OPENAI_API_KEY:}`          | API credential supplied by deployment configuration; never committed    |
| `spring.ai.openai.chat.model`             | `${candidate.matching.model}` | Spring AI OpenAI chat model selection                                   |

The deployment OpenAI API key must be supplied for the OpenAI chat integration to initialize.
The OpenAI API key is not required to run unit tests. Spring integration test contexts provide a
non-secret test-only credential solely to construct provider beans; matching tests mock the chat
integration and do not make live API requests. Invalid deployment credentials during a real
matching call are handled as a matching service failure.

## 3. Matching Flow

1. Load the requested job offer from MongoDB; retain the current not-found behavior.
2. Build the retrieval query from job name, description, required qualifications,
   responsibilities, and Nice to Have content.
3. Search Chroma with the configured similarity threshold. The threshold is applied inclusively;
   below-threshold documents are excluded.
4. Discard vector documents without a usable `candidateEmail`, and group the remaining retrieved
   text chunks by candidate email while preserving retrieval order.
5. If there are no groups, return an empty result without calling the LLM.
6. Send the job criteria and grouped retrieved resume text to the configured OpenAI chat model.
   Ask it to assess semantic equivalence as well as exact keyword overlap, considering position,
   technical skills, country, city, working mode, and professional experience. Unknown facts must
   not be inferred as present. Require an ordered structured result containing only supplied
   candidate references.
7. Validate every returned reference against the groups supplied to the model. Ignore unknown or
   duplicate references. Resolve candidate name and email from MongoDB and return only those
   backend-verified candidates in the model's ranking order.
8. If no candidate remains after validation, return an empty list. The frontend's empty state will
   explain that no candidates met the criteria and suggest reviewing or loosening qualifications.
9. Propagate Vector Store, OpenAI, parsing, and MongoDB failures through the existing matching
   service-unavailable error path; do not return partial or invented results.

## 4. API and UI

- Keep `POST /api/candidate-matches` request body `{ "jobOfferId": "..." }`.
- Keep successful response as an ordered JSON array of `{ "candidateName": "...",
  "candidateEmail": "..." }` records.
- Keep existing selected-offer enablement, loading, result, and error states.
- Update the no-results message to recommend reviewing or loosening current job qualifications.

## 5. Verification Design

- Unit tests cover property defaults/overrides and threshold validation.
- Service tests cover complete job criteria in the retrieval query, configured threshold, grouping
  and deduplication, no-call behavior for no hits, constrained/ranked model output, backend identity
  resolution, hallucinated-reference rejection, and downstream failures.
- Controller and jsdom tests cover existing success/failure contracts and the revised no-match
  message.
- Integration tests mock the OpenAI chat model and use the configured Chroma collection; no test
  requires an API key or sends resume text to OpenAI.
