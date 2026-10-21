# MapReduce Tiered Ranking Pipeline

I’ll map this onto the project’s existing Spring AI retrieval and ranking design, then outline a
concrete pipeline for selecting the best three candidate matches.

The current design uses `top-k` for both vector retrieval and, effectively, the candidate pool. A
MapReduce and tiered pipeline would separate those stages so retrieval can gather enough evidence
before the system selects the best three unique candidates.

```mermaid
flowchart LR
  A[Job offer] --> B[Build retrieval question]
  B --> C[Retrieve top N resume chunks]
  C --> D[Map: score each chunk against job]
  D --> E[Group scores by candidate]
  E --> F[Reduce: combine evidence per candidate]
  F --> G[Select and rerank top M candidates]
  G --> H[Final ranking]
  H --> I[Return top 3]
```

1. **Retrieve broadly.** Query the vector store with the offer’s position, required skills,
   responsibilities, location, and working mode. Retrieve a configurable pool—perhaps 30–50 resume
   chunks—instead of only 3. The exact value should be tuned against vector store quality, latency,
   and model context limits.

2. **Map over chunks.** For each retrieved chunk, assess relevance to the job and extract only
   evidence present in that chunk: candidate identifier, skills, relevant experience, location, and
   work mode. Keep the vector similarity score as a retrieval signal; don’t treat it as the final
   match score.

3. **Group by candidate.** Use a stable candidate ID in document metadata, if available. Group
   chunks by that ID so multiple chunks from one resume don’t crowd out distinct candidates. If
   metadata lacks an ID, adding one at resume ingestion is a useful prerequisite.

4. **Reduce evidence per candidate.** Combine a candidate’s chunks, remove duplicates, and produce a
   compact evidence summary with source chunk references. Score each candidate against required
   criteria and nice-to-haves. Missing evidence should stay unknown rather than being treated as
   either a match or a mismatch.

5. **Use tiers to spend model effort selectively.** Apply inexpensive filtering or scoring first,
   then ask the model for a deeper structured evaluation of a smaller shortlist—say the top 10–15
   candidates. A final comparison pass can order that shortlist and return up to three candidates.
   Keep the required and preferred criteria distinct, and validate identities and evidence against
   the retrieved documents.

6. **Return three unique candidates.** Sort by the final evaluation, deduplicate by candidate
   ID/email, assign positions, then take the first three. Return fewer when fewer than three
   candidates have sufficient retrieved evidence; never fill empty slots by inventing candidates.

For this repo, I’d split the work across a retrieval component, a candidate evidence reducer, and a
ranker/orchestrator. The current `OpenAiCandidateRanker` can remain the orchestration entry point,
but its single advisor call would need to become explicit retrieval plus staged ranking—or use
advisor/query expansion carefully if it can preserve per-candidate provenance. The current
`CandidateMatchingService` already sorts and deduplicates but doesn’t enforce a result limit, so the
final top-three limit belongs there or in the ranker contract.

I’d make the controls explicit, for example `candidate.matching.retrieval-top-k`,
`candidate.matching.shortlist-size`, and `candidate.matching.result-limit=3`; the existing
`candidate.matching.top-k` currently defaults to 5 in `application.properties`, despite the spec
describing 3. For evaluation, track recall of known relevant candidates, duplicate rate, grounding
errors, latency, and model/token cost before settling on pool and shortlist sizes.