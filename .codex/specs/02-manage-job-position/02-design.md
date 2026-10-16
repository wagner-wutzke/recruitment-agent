# Technical Design: Manage Job Position

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Backend: Java 21, Spring Boot 4.1.1, Spring Web MVC and Spring Data MongoDB.
- Frontend: Existing static HTML, vanilla JavaScript, CSS, and Bootstrap SPA.
- Persistence: Existing `job_offers` MongoDB collection through `JobOfferRepository`.
- No database migration is required; the existing `JobOffer` document fields remain unchanged.

The detailed acceptance criteria define list, detail, create, and edit behavior. Delete is not
included because no delete interaction or acceptance criterion is specified.

## 2. API Contract

### `GET /api/job-offers`

Keep the existing response contract: `200 OK` with an array of offers, or `503 Service Unavailable`
with `{ "code": "JOB_OFFERS_UNAVAILABLE", "message": "Job offers could not be loaded." }`.

### `POST /api/job-offers`

Create and persist an offer. The JSON body contains required, non-blank `jobName`,
`jobDescription`, `requiredQualifications`, `location`, and `allocation`. Allocation is restricted
to `Remote`, `Hybrid`, or `On-site`.

On success return `201 Created` with the saved offer response fields: `id`, `jobName`,
`jobDescription`, `requiredQualifications`, `location`, and `allocation`. On invalid input return
`400 Bad Request` with a stable error code/message. On persistence failure return `503 Service
Unavailable` with `JOB_OFFER_SAVE_FAILED` and a user-safe message.

### `PUT /api/job-offers/{id}`

Update the existing offer identified by `id`, using the same required body fields and response
shape as POST. Return `200 OK` on success, `400 Bad Request` for invalid input, `404 Not Found` if
the id does not exist, and `503 Service Unavailable` with `JOB_OFFER_SAVE_FAILED` if persistence
fails.

## 3. Service Behavior

- Keep listing through `findAll()`.
- Add a create operation that stores a new document and returns the repository's saved document.
- Add an update operation that checks existence, keeps the requested id, replaces editable field
  values, and returns the saved document.
- Do not swallow repository exceptions; the controller maps save failures to the documented API
  error.

## 4. SPA Behavior

- Keep the existing left offer list and right details panel.
- Show New Job Offer below the details panel regardless of selection; show Edit there only while
  an offer is selected.
- Use one Bootstrap modal form for create and edit. The fields are job name, description, required
  qualifications, location, and allocation. All are required; allocation options are exactly
  Remote, Hybrid, and On-site.
- Do not show required errors until Save is attempted. Then mark every empty required field with a
  red border and accessible required/invalid description.
- Cancel with an unchanged form closes immediately. Cancel with changed values opens a custom
  in-page confirmation dialog with discard and continue-editing actions.
- A successful POST or PUT refreshes the list, selects the saved offer, displays its details, and
  closes the modal. A failed request leaves the modal open and displays an error message.
- Render server-provided values as text, never HTML.

## 5. Error Handling and Validation

- Request DTO validation rejects null, blank, or whitespace-only required fields and allocations
  outside the three allowed values.
- The client performs the specified required-field feedback after Save; the server remains the
  source of truth and returns 400 for invalid requests.
- Missing update ids return 404. Database retrieval errors preserve the existing list error
  contract; create/update storage errors return the documented save error.

## 6. Verification

- Java tests cover service create/update/existence behavior and controller status/body contracts.
- Browser-level jsdom tests cover selection controls, blank/edit forms, cancel confirmation,
  required validation timing, allowed allocation values, successful create/update selection, and
  failed-save modal retention.
