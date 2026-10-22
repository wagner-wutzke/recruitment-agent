# Tasks: Extract Resume Metadata with an LLM

- **Status:** [x] Draft | [ ] Approved for Implementation | [ ] In Implementation | [ ] Completed
- **Requirements:** `01-requirements.md`
- **Design:** `02-design.md`

## Phase 1: Define the Metadata Contract

- [ ] **Task 1.1:** Define and add a typed resume metadata model with the canonical fields
  `candidateName`, `country`, `position`, `skills`, `professionalExperience`, `allocation`, 
  `relocation`, and `expectedSalary`. Define empty values for missing fields and unit-test 
  serialization/schema behavior (**AC2**).
- [ ] **Task 1.2:** Add the classpath extraction prompt. Specify the structured response,
  evidence-only extraction, empty values for unknown fields, and treatment of resume text as 
  untrusted input (**AC1, AC2, AC4**).

## Phase 2: Implement the Metadata Extractor

- [ ] **Task 2.1:** Add extraction settings to `ResumeIndexingProperties` and
  `application.properties` for enablement, maximum extraction input characters, timeout, and prompt
  resource. Preserve existing indexing defaults and add property binding tests (**AC3**).
- [ ] **Task 2.2:** Implement `ResumeMetadataExtractor` using the existing Spring AI `ChatClient`
  and configured model. Load its prompt from the configured resource and request the typed 
  structured response (**AC1, AC2**).
- [ ] **Task 2.3:** Bound extraction input without truncating indexed page text. Validate response
  shape and vector-store-compatible value types, normalize values, deduplicate skills, constrain
  output size, and retain only values grounded in the supplied text (**AC1, AC2, AC4**).
- [ ] **Task 2.4:** Implement disabled-mode and extraction-failure fallback to empty metadata. Apply
  the configured timeout and emit sanitized failure logs without resume text, personal values, or
  credentials (**AC3**).
- [ ] **Task 2.5:** Ensure Spring AI prompt and completion observation logging does not record
  resume extraction input or output; retain safe diagnostics for unrelated application behavior 
  (**AC3**).

## Phase 3: Integrate Metadata into Resume Indexing

- [ ] **Task 3.1:** Invoke metadata extraction from `ResumePdfIndexer` after page extraction and
  whitespace normalization and before chunk splitting. Preserve email validation and call the
  extractor once per upload (**AC1–3**).
- [ ] **Task 3.2:** Attach the normalized metadata fields to every generated chunk using
  vector-store compatible values. Preserve `candidateEmail`, `documentId`, `fileName`, original 
  chunk text, and existing splitter behavior (**AC1, AC4**).
- [ ] **Task 3.3:** Verify an extraction input limit does not truncate the page text sent through
  the existing chunking and indexing path (**AC1, AC3**).
- [ ] **Task 3.4:** Build initial candidate-search metadata constraints from non-blank offer
  `jobName` and `allocation`, mapping them to resume `position` and `allocation` with trimmed,
  case-insensitive equality. Do not map `location` to `country` or use exact filters for skills or
  professional experience. Preserve semantic similarity and existing current-document and
  candidate-version checks (**AC5**).
- [ ] **Task 3.5:** When applicable constraints exist, supplement a constrained search that
  underfills the configured retrieval limit or cannot execute with unconstrained semantic results.
  Prefer constrained results, deduplicate chunks, and cap the combined seed set at the configured
  limit. Use only unconstrained search when no applicable criteria exist (**AC5**).

## Phase 4: Verify Upload and Replacement Behavior

- [ ] **Task 4.1:** Add extractor unit tests for structured response conversion, missing fields,
  normalization, skill deduplication, grounding, size limits, and prompt construction (**AC1, AC2**).
- [ ] **Task 4.2:** Add extractor tests for disabled mode, provider errors, timeouts, malformed
  responses, and sanitized logging. Verify failures return empty metadata without a live provider or
  API key (**AC3**).
- [ ] **Task 4.3:** Add indexer tests proving valid metadata is attached to every chunk, chunk text
  and tracking metadata remain intact, and extraction failure still produces indexable chunks
  (**AC1–4**).
- [ ] **Task 4.4:** Add or update upload-service tests to verify a successful replacement indexes
  new-version metadata and preserves existing replacement cleanup behavior (**AC6**).
- [ ] **Task 4.5:** Add retriever tests asserting actual vector search requests for position and
  allocation filters, omission of filters for blank criteria, no location-to-country filter,
  constrained-search underfill and failure fallback, result deduplication/cap, and preservation of
  current-document and candidate-version checks (**AC5**).
- [ ] **Task 4.6:** Run focused metadata extraction, PDF indexer, resume upload, and candidate
  retrieval tests. Record results and fix regressions before requesting review (**AC1–6**).

## Phase 5: Review

- [ ] **Task 5.1:** Review implementation against every requirement and acceptance scenario,
  including position/allocation filtering and fallback behavior. Confirm no API, identity, PDF
  retention, or matching evidence contract changed, and report any gaps before marking the feature
  complete.
