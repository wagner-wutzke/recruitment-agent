# Feature Spec: Upload and Index Resumes

- **Status:** [ ] Draft | [ ] Approved for Design | [x] In Implementation | [ ] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-16

## 1. Objective and Context

Allow recruiters to upload candidate resumes as PDFs, store resume metadata in the existing
MongoDB database, retain the original PDF locally, and index extracted resume content in the local
ChromaDB vector store for later candidate matching.

The application uses Spring Boot, MongoDB, and a static vanilla JavaScript frontend. Job-offer and
resume metadata both use MongoDB. ChromaDB runs in a local Docker container. Relational databases,
JPA, and H2 are not used for resume metadata.

## 2. Business Rules

- Candidate email uniquely identifies a resume document and is its MongoDB `_id`.
- Candidate email is extracted from the uploaded PDF's extractable text; the recruiter does not
  enter
  it manually. When multiple email addresses are present, use the first valid address in document
  order.
- Uploading a resume for an existing candidate email replaces that document rather than creating a
  second record.
- Every successful upload has a generated document identifier saved in the MongoDB resume document
  and in the associated Chroma document metadata.
- Resume contents are embedded locally and are not sent to an external model API.

## 3. Functional Requirements

- Add an **Upload Resume** button below the Offers panel.
- Clicking the button opens a **Resume Data** modal with Candidate Name and PDF file controls, plus
  **Cancel** and **Save** buttons.
- Extract text from the uploaded PDF and derive the candidate email from the first valid email
  address in document order. Reject the upload if no valid email address is found.
- Save Candidate Name, extracted Candidate Email, renamed disk file name, server-generated upload
  datetime, and document identifier in a MongoDB document in the `resumes` collection. Candidate
  Email is the document `_id`.
- If the extracted Candidate Email already exists, replace its metadata and indexed resume content.
- Transform extracted PDF text into vector documents, generate embeddings locally, and store the
  vector documents in the local ChromaDB service.
- Before splitting, normalize extracted page text with a document transformer that collapses
  repeated horizontal whitespace and excessive blank lines while preserving single word spaces,
  section line breaks, page boundaries, and the original uploaded PDF bytes.
- Configure the text splitter through Spring properties under `resume.indexing`:
  `chunk-size` (default `800`), `min-chunk-size-chars` (default `350`),
  `min-chunk-length-to-embed` (default `20`), `max-num-chunks` (default `100`), and
  `keep-separator` (default `true`). These defaults target resumes up to 20,000 extracted
  characters while retaining relevant context in each retrieval unit.
- Save the generated Vector Store document identifier in the corresponding MongoDB resume document.
- Retain the original uploaded PDF under `${user.home}/data/recruitment-agent/resume/` using the
  filename rule below. Recruiters can download a retained PDF through
  `GET /api/resumes/{candidateEmail}/file`.
- Rename the retained PDF to `[CANDIDATE_NAME].pdf`, replacing each space in Candidate Name with
  `_`. Store this disk filename in the MongoDB `fileName` field. Reject names containing path
  separators or NUL characters.
- Add the candidate email and resume document identifier as metadata to each generated vector
  document so indexed documents can be tracked and replaced.

## 4. Non-Functional Requirements

- The default ChromaDB endpoint is `http://localhost:8000`.
- Embedding generation runs locally. Resume text must not be sent to OpenAI or another external
  embedding service.
- A save is successful only after the MongoDB metadata write and Chroma index operation complete.
  Failures must be visible to the recruiter.
- Resume metadata is stored only in MongoDB; do not introduce JPA, H2, or another relational
  persistence path for this feature.

## 5. Acceptance Criteria

These scenarios must be validated by the `@reviewer`.

### Scenario 1: Open and cancel the Resume Data form

- **Given** the recruiter is viewing the Job Offers page
- **When** they select **Upload Resume**
- **Then** the **Resume Data** modal displays Candidate Name and PDF file controls, plus **Cancel**
  and **Save** buttons.
- **And** the form does not request Candidate Email.
- **When** they select **Cancel** without saving
- **Then** the modal closes without creating or changing a resume document.

### Scenario 2: Save a new resume

- **Given** the recruiter enters a candidate name and selects a readable PDF containing a valid
  candidate email address
- **When** they select **Save** and processing succeeds
- **Then** one MongoDB document is saved in `resumes` with the submitted name, email extracted from
  the PDF, renamed disk file name, server-generated upload time, and generated document identifier.
- **And** extracted resume text is embedded locally and stored in ChromaDB.
- **And** the document identifier saved in MongoDB identifies the indexed resume content.
- **And** the modal closes after success.

### Scenario 3: Replace a resume for an existing candidate

- **Given** a resume document and indexed content already exist for a candidate email
- **When** another readable PDF containing that same email is saved
- **Then** the existing `resumes` document is replaced with the new name, file name, upload time,
  and document identifier, with no second document created for that email.
- **And** the candidate's previous indexed content is replaced with content from the new PDF.

### Scenario 4: Reject incomplete or unreadable submissions

- **Given** Candidate Name or PDF file is missing, the selected file cannot be parsed as a PDF with
  extractable text, or the PDF contains no valid email address
- **When** the recruiter selects **Save**
- **Then** the submission is rejected with a visible error and no successful-save state is shown.

### Scenario 5: Handle MongoDB or ChromaDB failure

- **Given** MongoDB or local ChromaDB is unavailable or rejects the save operation
- **When** the recruiter submits a resume
- **Then** the modal remains open and a visible error is shown.
- **And** the application does not show a successful-save state.

### Scenario 6: Keep resume content local

- **Given** a readable PDF is submitted
- **When** its text is transformed and embedded
- **Then** a local embedding model performs the embedding operation and no resume text is sent to an
  external API.

### Scenario 7: Download a retained resume

- **Given** a resume has been successfully uploaded for a candidate email
- **When** the recruiter requests `GET /api/resumes/{candidateEmail}/file`
- **Then** the server returns the retained PDF as an attachment with its stored filename.
- **And** an unknown candidate email returns `404 Not Found`.

### Scenario 8: Configure resume text splitting

- **Given** the application has custom `resume.indexing` splitter properties configured
- **When** a readable PDF is processed
- **Then** `TokenTextSplitter` is created with the configured chunk size, minimum chunk size,
  minimum embedded chunk length, maximum chunk count, and separator behavior.
- **And** when no custom values are configured, the documented defaults are used.

## 6. Error Cases

- Missing required form values: reject the request and identify the invalid fields.
- Candidate names containing path separators or NUL characters: reject the request.
- Failure to retain the uploaded file: show a save error and do not show success.
- Invalid or unreadable PDF: reject the request and show an upload error.
- No extractable text: reject the request and show an upload error; OCR is not attempted.
- No valid email address in the extracted PDF text: reject the request and show an upload error.
- MongoDB or ChromaDB failure: show a save error and do not show success.
- Failure to read a retained PDF: show a download error; do not expose a filesystem path.

## 7. Out of Scope

- Enforcing a 5 MB PDF size limit and showing a custom error for larger files is out of scope.
- OCR for scanned PDFs is out of scope; uploaded PDFs must contain extractable text.
- Candidate search, ranking, recommendations, and matching are future features.
- Listing resumes or adding a resume-management screen is out of scope.
