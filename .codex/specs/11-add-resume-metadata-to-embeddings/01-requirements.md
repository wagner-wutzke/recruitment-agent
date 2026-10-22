# Feature Spec: Extract Resume Metadata with an LLM

- **Status:** [ ] Draft | [ ] Approved for Implementation | [x] In Implementation | [ ] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-22

## 1. Objective and Context

Resume PDFs are currently normalized and indexed as generic text chunks. Add optional LLM-based
metadata extraction, attach the extracted values to each chunk, and use supported metadata to
reduce irrelevant candidates returned by vector retrieval. Original resume text remains the source
for matching evidence.

## 2. Business Rules

- Extract only values supported by the extracted resume text; do not infer or fabricate missing
  information.
- The canonical extracted fields are `candidate_name`, `country`, `position`, `skills`,
  `professional_experience`, `allocation`, `relocation`, and `expected_salary`.
- Represent an absent or uncertain scalar as null and an absent or uncertain `skills` value as an
  empty list.
- Candidate identity remains governed by existing upload behavior: candidate email comes from the
  existing first-valid-email parser, and the submitted name remains the stored display name.
  Extracted `candidate_name` is descriptive metadata only; candidate email is not an LLM-extracted
  field.
- Preserve existing `candidateEmail`, `documentId`, and `fileName` tracking metadata on every
  indexed chunk.
- Metadata filtering is a coarse retrieval constraint, not proof of candidate suitability and not
  a replacement for semantic similarity or source-grounded matching evidence.
- Map job-offer `jobName` to resume metadata `position` and job-offer `allocation` to resume
  metadata `allocation`. Do not map `location` to `country`: the existing location field may
  describe a city, region, or country, so the values are not reliably equivalent. Apply a
  constraint only when its offer value is non-blank and compare trimmed, case-insensitive values
  for equality. Do not apply exact metadata filters to skills or professional experience.
- Run the existing unconstrained semantic search as a fallback when the constrained search returns
  fewer than the configured retrieval limit or cannot be executed. Prefer constrained results,
  append non-duplicate results from the fallback, and cap the combined seed set at the configured
  retrieval limit. The limit remains a retrieval bound; it does not guarantee inclusion of every
  candidate with missing metadata.
- Send resume text to an LLM only through a provider already configured for the application. Do
  not add a provider or credentials as part of this feature.

## 3. Functional Requirements

- After PDF text extraction and whitespace normalization, use the configured Spring AI model
  integration to extract the canonical fields using structured output.
- Validate the structured response and each non-empty value against the bounded text supplied to
  the model before using it.
- Store extracted metadata on every vector-store chunk using supported, serializable metadata
  values. Keep `skills` as a list in the extraction contract and serialize it in a vector-store-
  compatible representation.
- Preserve the complete normalized resume text, existing chunking settings, tracking metadata,
  and `documentId` association. Do not replace source chunks with LLM summaries or paraphrases.
- Apply the business-rule metadata constraints to the initial candidate vector search. If no
  applicable offer metadata is present, use the existing unconstrained search directly. Otherwise,
  use the fallback and merge behavior defined above. Retain the existing current-document
  validation and candidate-version chunk retrieval behavior.
- If extraction fails, times out, returns invalid structured data, or yields unsupported values,
  continue indexing with empty/unknown extracted metadata. Extraction failure alone must not fail
  an otherwise valid upload.
- Re-uploading a resume extracts metadata for the new version. Existing vector records are not
  automatically reprocessed.

## 4. Non-Functional Requirements

- Use the application's configured Spring AI model integration; do not hard-code another provider
  or credential.
- Bound extraction input, model output, and extraction time. Limiting extraction input must not
  truncate text sent through the normal indexing path.
- Do not expose credentials in source, logs, or API responses. Extraction-failure logs must not
  contain resume text, extracted personal data, or credentials.
- Metadata validation and filtering must not alter the original text used as matching evidence.

## 5. Acceptance Criteria

### Scenario 1: Extract supported metadata

- **Given** a readable resume containing one or more canonical metadata values
- **When** indexing uses the configured LLM and it returns valid structured output
- **Then** supported values are attached to every indexed chunk and absent values remain unknown.
- **And** existing tracking metadata and original chunk text are preserved.

### Scenario 2: Reject unsupported or absent values

- **Given** a resume omits a requested field or the model returns a value not supported by the
  supplied text
- **When** the extraction response is validated
- **Then** that field is null or empty, as appropriate, and no unsupported value is stored.

### Scenario 3: Continue after extraction failure

- **Given** a valid resume and an unavailable, failing, timed-out, or invalid-output LLM response
- **When** the resume is uploaded
- **Then** the resume is indexed with its original text and existing tracking metadata, with
  extracted fields unknown.
- **And** the normal successful upload response is returned if all other upload operations succeed.

### Scenario 4: Preserve identity and evidence

- **Given** extracted `candidate_name` differs from the submitted display name
- **When** the resume is indexed and later used for matching
- **Then** the submitted name remains the stored display name and the existing parsed email remains
  the candidate identity.
- **And** matching evidence comes from original resume text, not generated summaries.

### Scenario 5: Use metadata to constrain retrieval with fallback

- **Given** a job offer with position and/or allocation criteria and resumes with matching,
  non-matching, or missing corresponding metadata
- **When** candidate retrieval runs
- **Then** it applies the specified equality constraints to a constrained vector search.
- **And** when the constrained search underfills the configured retrieval limit or cannot execute,
  it supplements results with unconstrained semantic results, removes duplicate chunks, prefers
  constrained results, and caps the combined seed set at the configured retrieval limit.
- **And** current-document validation and candidate-version retrieval remain in effect.

### Scenario 6: Replace a resume

- **Given** a candidate uploads a replacement resume
- **When** indexing succeeds
- **Then** the new document version has metadata extracted from the new PDF and existing replacement
  and cleanup behavior remains unchanged.

## 6. Error Cases

- Invalid PDF, no extractable text, or no valid candidate email continues to fail under existing
  upload behavior.
- LLM unavailability, timeout, malformed output, schema violation, or unsupported metadata values
  results in empty extracted metadata and does not by itself fail indexing.
- Vector-store indexing or persistence failures continue to follow existing upload failure and
  compensation behavior.
- Extraction failures must not expose resume text, personal metadata, credentials, or local
  filesystem paths in logs or API responses.
- If metadata-constrained retrieval returns no results or cannot execute, attempt the existing
  unconstrained semantic retrieval path. If that fallback also fails, propagate the existing
  retrieval failure behavior rather than silently reporting no candidates.

## 7. Out of Scope

- Replacing original resume chunks with LLM-generated summaries or paraphrases.
- Using LLM output as candidate identity or as proof for match claims without source-text evidence.
- Changing the upload form, upload API contract, or retained PDF behavior.
- Automatically reprocessing existing vector-store records.
- Introducing a new LLM provider, account, or credential configuration.
