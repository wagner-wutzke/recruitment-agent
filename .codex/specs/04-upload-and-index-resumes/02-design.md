# Technical Design: Upload and Index Resumes

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Keep job offers and resume metadata in MongoDB. Use Spring Data MongoDB for both collections;
  resume metadata has no JPA, H2, SQL, or relational persistence dependency.
- Use Spring AI 2.0.1 with its Chroma vector-store and Transformers (ONNX) embedding starters.
- Connect to the local Chroma service at `http://localhost:8000` by default, configurable through
  `CHROMA_HOST` and `CHROMA_PORT`.
- Use the local `all-MiniLM-L6-v2` ONNX embedding model. Model files may be downloaded and cached
  on first use; resume text itself is never sent to an external model provider.
- Parse PDF bytes using Spring AI `PagePdfDocumentReader` and split extracted text with
  `TokenTextSplitter` before indexing. Bind splitter settings from `resume.indexing` properties;
  defaults preserve the current values.
- Retain original PDF bytes in `${user.home}/data/recruitment-agent/resume/` and serve them through
  the API download route in this design.

## 2. MongoDB Document

Map `ResumeDocument` to the `resumes` collection:

| Field            | Java type | Mongo field      | Constraint                       |
|------------------|-----------|------------------|----------------------------------|
| `candidateEmail` | `String`  | `_id`            | Required, unique identifier      |
| `candidateName`  | `String`  | `candidateName`  | Required                         |
| `fileName`       | `String`  | `fileName`       | Required, safe retained filename |
| `uploadDatetime` | `Instant` | `uploadDatetime` | Required, set by server in UTC   |
| `documentId`     | `String`  | `documentId`     | Required, generated UUID string  |

Use `MongoRepository<ResumeDocument, String>`. Saving a document with an existing candidate email
replaces that candidate's metadata while retaining the same `_id`.

Every Chroma chunk receives `candidateEmail`, `documentId`, and `fileName` metadata. `documentId`
groups chunks belonging to one upload and is used to remove the previous upload's chunks.

Bind `resume.indexing` to a `ResumeIndexingProperties` configuration-properties class:

| Property                                    | Type      | Default |
|---------------------------------------------|-----------|---------|
| `resume.indexing.chunk-size`                | `int`     | `800`   |
| `resume.indexing.min-chunk-size-chars`      | `int`     | `350`   |
| `resume.indexing.min-chunk-length-to-embed` | `int`     | `20`    |
| `resume.indexing.max-num-chunks`            | `int`     | `10000` |
| `resume.indexing.keep-separator`            | `boolean` | `true`  |

## 3. API Contract

### `POST /api/resumes`

Accept `multipart/form-data` with `candidateName`, `candidateEmail`, and `file` parts.

- Return `201 Created` for a new email and `200 OK` when replacing an existing email.
- Return `candidateName`, `candidateEmail`, `fileName`, `uploadDatetime`, and `documentId`.
- Return `400 Bad Request` for missing fields, unsafe candidate names, unreadable PDFs, or PDFs
  without extractable text.
- Return `503 Service Unavailable` if local file retention, MongoDB persistence, or Chroma indexing
  fails.

### `GET /api/resumes/{candidateEmail}/file`

- Look up the resume document by candidate email and stream the retained PDF as
  `application/pdf` with `Content-Disposition: attachment; filename="<fileName>"`.
- Return `404 Not Found` when no resume metadata exists for the email.
- Return `503 Service Unavailable` if the metadata exists but the retained file cannot be read.
- Never expose the server filesystem path in the response.

No list, delete, candidate-search, or matching endpoints are added.

## 4. Upload and Index Flow

1. Validate required multipart fields and reject candidate names containing `/`, `\\`, or NUL.
2. Parse the uploaded bytes and reject invalid PDFs or PDFs with no extractable text.
3. Split extracted text into vector documents and attach the candidate email, generated UUID string,
   and renamed disk filename as metadata.
4. Retain the original bytes as `[CANDIDATE_NAME].pdf`, replacing spaces with `_`. Resolve the path
   under the configured resume directory and reject any path that escapes it.
5. Add the chunks to Chroma using the local ONNX embedding model.
6. Save the MongoDB `ResumeDocument` keyed by candidate email with the generated ID and current UTC
   time.
7. On a successful replacement, delete old Chroma chunks using the previous document ID.
8. If Chroma insertion or MongoDB persistence fails, attempt to remove newly added chunks and roll
   back the retained file to its previous contents. Preserve the previous MongoDB document when its
   save has not completed. Log compensation failures for operator follow-up.

MongoDB and Chroma do not share a transaction; compensation is best effort and must be covered by
service tests.

## 5. File Storage and Download

- Configure the storage directory with `resume.storage.directory`; default it to
  `${user.home}/data/recruitment-agent/resume`.
- Derive the disk filename from the submitted candidate name by replacing spaces with underscores
  and appending `.pdf`.
- The MongoDB `fileName` field stores the disk filename, not the original multipart filename.
- The download handler obtains the filename from the MongoDB document and resolves it safely under
  the configured directory. It must not accept a caller-supplied path or filename.

## 6. SPA Behavior

- Keep the **Upload Resume** button below the Offers panel.
- Keep the **Resume Data** Bootstrap modal with required Candidate Name, Candidate Email, and PDF
  fields plus Cancel and Save actions.
- Submit with `FormData` to `POST /api/resumes`.
- Close the modal on successful upload; keep it open and show an error after a failed upload.
- Do not persist resume form data in browser storage.
- Downloads are available through the API route; no resume list or download-management screen is
  introduced.

## 7. Verification Design

- Mongo mapping/repository tests verify the `resumes` collection, candidate-email `_id`, saved
  metadata, and replacement by email. Use an isolated MongoDB test database or a focused converter
  test where no Mongo service is available; never use H2/JPA for resume persistence tests.
- Service tests verify PDF extraction, chunk metadata, local indexing, Mongo save/update calls,
  failure compensation, and replacement cleanup.
- Configuration binding tests verify all five `resume.indexing` values, defaults, and custom
  overrides passed to `TokenTextSplitter`.
- Controller tests verify multipart statuses, validation/dependency errors, attachment headers,
  unknown-email 404, and safe handling of missing files.
- jsdom tests verify upload form/cancel/save behavior and failed-upload feedback.
- The local Chroma check verifies add, replacement, and deletion in a dedicated test collection.
- Run the complete Maven and npm suites and record actual results in `03-tasks.md`.

## 8. Configuration

- MongoDB uses the existing Spring Data Mongo configuration. Do not add a relational datasource.
- Keep MongoDB credentials and service-specific values out of tracked secrets.
- Chroma defaults to localhost port 8000 and local ONNX embeddings.
- The feature does not impose a custom 5 MB limit; framework multipart request limits may still
  apply.
