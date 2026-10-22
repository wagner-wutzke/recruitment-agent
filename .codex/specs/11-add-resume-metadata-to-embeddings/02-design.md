# Technical Design: Extract Resume Metadata with an LLM

- **Status:** [x] Draft | [ ] Approved for Design | [ ] In Implementation | [ ] Completed
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

Keep the upload path and vector-store ownership in `ResumePdfIndexer`. Add a dedicated
`ResumeMetadataExtractor` service that calls the existing Spring AI `ChatClient`, validates the
structured response, and returns a normalized metadata value. The indexer invokes it after PDF
text extraction and whitespace normalization, before splitting the normalized pages into chunks.

The extractor receives bounded normalized resume text. It does not replace the extracted text,
perform PDF parsing, set candidate identity, or persist vector documents. The existing
`ResumeUploadService` continues to own vector-store insertion, MongoDB persistence, and replacement
compensation.

## 2. Extracted Metadata Contract

Represent extraction internally with a typed Java record, for example:

```text
ResumeMetadata(
  candidateName: String?,
  country: String?,
  position: String?,
  skills: List<String>,
  professionalExperience: String?,
  allocation: String?,
  relocation: String?,
  expectedSalary: String?
)
```

- Null scalar values and an empty `skills` list mean the resume does not provide a supported value.
- Keep extracted values concise and in the resume's wording where possible; do not generate a
  summary or infer unsupported values.
- Validate the response schema and remove blank strings, blank skill entries, and duplicate skills
  (case-insensitive, preserving first-seen order).
- Because vector-store metadata values must be scalar/serializable, write scalar values as strings
  and `skills` as a JSON string array. Use a stable property key for each field, including
  `professionalExperience` for `professional_experience`.
- Keep these fields separate from `candidateEmail`, `documentId`, and `fileName`. The email
  extracted by the existing first-valid-email parser remains the candidate key; the submitted name
  remains `ResumeDocument.candidateName`.
- Candidate email is not an LLM-extracted field. Extracted `candidateName` remains descriptive
  metadata and must not replace the submitted name stored as `ResumeDocument.candidateName`.

## 3. Extraction Prompt and Validation

Use an `@Value`-injected classpath prompt resource, following the existing prompt-resource pattern
used by `CandidateMapEvaluator`. The prompt instructs the configured model to treat resume text as
untrusted data, extract only the requested fields, use null/empty values when evidence is absent,
and return the required structured schema.

Call the existing Spring AI `ChatClient` with `.entity(ResumeMetadata.class)` (or an equivalent
Spring AI structured-output converter supported by the pinned dependency). Validate that:

- the response object is non-null;
- all required response fields are present according to the schema;
- each non-empty value is grounded in the bounded resume text;
- scalar and list values fit configured size limits before metadata insertion.

Grounding validation should use normalized text comparison, allowing harmless whitespace and
case differences. A value that cannot be supported by source text is cleared rather than trusted.
For `skills`, validate each item independently and keep only supported entries. Do not log prompts,
completions, source text, or extracted personal data.

## 4. Indexing Flow

1. Extract PDF pages with the existing `PagePdfDocumentReader`; retain current invalid-PDF, empty
   text, and first-valid-email validation behavior.
2. Normalize page text with the existing `ResumeWhitespaceNormalizer`.
3. Concatenate normalized page text in page order for extraction input, preserving page separators.
   Limit only the extraction input using `resume.indexing.metadata-extraction-max-chars`; do not
   truncate the page documents used for normal indexing.
4. Invoke `ResumeMetadataExtractor` once per upload. On timeout, provider error, invalid response,
   or validation failure, use an all-empty metadata value and continue.
5. Apply the existing `TokenTextSplitter` to the complete normalized pages with the existing
   settings. Attach `candidateEmail`, `documentId`, and `fileName` as today, plus the normalized
   metadata fields from section 2 to every chunk.
6. Build metadata filter criteria from non-blank offer values only: map `jobName` to `position`
   and `allocation` to `allocation`. Do not map `location` to `country`, because location may be a
   city, region, or country. Compare trimmed, case-insensitive values for equality. Do not apply
   exact metadata filters to skills or professional experience.
7. If at least one applicable criterion exists, run a constrained semantic vector search. When it
   returns fewer than the configured retrieval limit, or cannot be executed, run the existing
   unconstrained semantic search as fallback. Prefer constrained results, append non-duplicate
   fallback chunks, and cap the combined seed set at the configured retrieval limit. If no
   applicable criterion exists, run only the unconstrained search. Preserve the existing
   current-document validation and candidate-version filter (`candidateEmail` plus `documentId`)
   when hydrating selected candidates.
8. Return the prepared chunks to `ResumeUploadService`, which continues its existing add/save/
   replacement/compensation flow.

Metadata copied to every chunk allows vector-store filtering at candidate-retrieval time. It is an
additional coarse constraint, not proof of suitability or a replacement for semantic similarity
or current-document checks. The configured retrieval limit bounds the combined seed set; it does
not guarantee inclusion of every candidate whose metadata is missing. The original chunk text
remains the only resume quotation/evidence text consumed by candidate matching.

## 5. Configuration

Extend `ResumeIndexingProperties` and `application.properties` with:

| Property                                          |       Type |                                   Proposed default | Purpose                                 |
|---------------------------------------------------|-----------:|---------------------------------------------------:|-----------------------------------------|
| `resume.indexing.metadata-extraction-enabled`     |  `boolean` |                                             `true` | Enable LLM metadata extraction          |
| `resume.indexing.metadata-extraction-max-chars`   |      `int` |                                            `20000` | Bound normalized text sent to the model |
| `resume.indexing.metadata-extraction-timeout`     | `Duration` |                                              `30s` | Bound time spent waiting for extraction |
| `resume.indexing.metadata-extraction-prompt-file` | `Resource` | `classpath:prompts/resume-metadata-extraction.txt` | Extraction instructions                 |

Use the existing configured Spring AI `ChatClient` and `candidate.matching.model`; do not add a
provider-specific client or credential. When extraction is disabled, use empty metadata and index
the resume normally. The timeout must be applied to the extraction operation without changing the
existing matching timeouts.

## 6. Failure Handling

Treat extraction as optional enrichment:

- Model unavailable, timeout, empty response, malformed output, or schema conversion error returns
  empty metadata and emits a sanitized warning with no resume identity or content.
- A response with individual ungrounded fields clears those fields; valid grounded fields may be
  retained.
- Vector-store insertion or MongoDB persistence errors continue to fail the upload and invoke the
  existing compensation behavior.
- The extractor must not swallow PDF parsing, email validation, or indexing failures.
- If a constrained retrieval request returns no results or cannot execute, attempt the existing
  unconstrained search. If that fallback also fails, propagate the existing retrieval failure
  behavior rather than silently returning an empty candidate set.

## 7. Testing Design

- Unit-test prompt request construction, typed response conversion, field normalization, grounding,
  configured text bound, disabled mode, timeout/provider failure fallback, and sanitized logging.
- Unit-test `ResumePdfIndexer` to verify that valid metadata is attached to each chunk, original
  chunk text is unchanged, candidate tracking metadata remains unchanged, and extraction failure
  still produces indexable chunks with empty metadata.
- Verify text beyond the extraction character limit remains present in indexed chunk content.
- Update service/upload tests only where needed to prove failed metadata extraction does not break
  upload and successful replacement continues to replace the previous version.
- Test metadata-filtered retrieval request construction and outcomes for position/allocation
  criteria, blank offer values, missing resume metadata, constrained-search underfill, constrained
  search failure, duplicate fallback chunks, and the configured combined-result cap.
- Use a mocked `ChatClient` for unit tests; tests must not require an API key or contact a live
  provider.

## 8. Scope Boundaries

- No changes to upload API fields, MongoDB resume identity, retained PDF naming, or matching/ranking
  evidence contracts.
- No LLM-generated summaries, rewritten chunks, or inferred match claims.
- No new provider or credentials; extraction uses the application's configured Spring AI model.
- Existing vector records are updated only when a resume is uploaded again.
