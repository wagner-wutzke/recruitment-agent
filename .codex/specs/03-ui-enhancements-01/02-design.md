# Technical Design: UI Enhancements 01

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture

- Backend remains Spring Boot 4.1.1, Spring Web MVC, and Spring Data MongoDB.
- The existing `JobOffer` MongoDB document, request/response DTOs, and static SPA remain the
  persistence and presentation boundaries.
- New descriptive fields are optional strings. Existing MongoDB documents and clients that omit
  these properties remain readable; no migration or new endpoint is required.
- The browser remains vanilla JavaScript, HTML, CSS, and Bootstrap. Markdown is rendered in the
  details view using a Markdown parser and an HTML sanitizer loaded from pinned CDN versions, in
  keeping with the existing Bootstrap CDN usage. Sanitization is mandatory before inserting parsed
  content into the DOM.

## 2. Data Model and API

Add the following nullable string properties to `JobOffer`, `JobOfferRequest`, and
`JobOfferResponse`, using lower camel case in Java and JSON:

| User-facing field | Property name      | Form control |
|-------------------|--------------------|--------------|
| Responsibilities  | `responsibilities` | Text area    |
| Benefits          | `benefits`         | Text area    |
| Company Name      | `companyName`      | Text input   |
| Nice to Have      | `niceToHave`       | Text area    |
| Compensation      | `compensation`     | Text area    |

The existing `GET /api/job-offers`, `POST /api/job-offers`, and `PUT /api/job-offers/{id}`
contracts retain their current routes, status codes, and error handling. The five new properties are
included in request and response JSON and persisted with the document. They are optional and do not
change existing required-field validation. Updates replace the document using all existing and new
editable properties, so omitted optional properties are saved as `null` in the replacement document.

## 3. SPA Behavior

- Add the five controls to the existing create/edit modal. Populate all five from the selected
  offer during edit and include them in POST and PUT request bodies.
- Show `companyName` in each job-offer list entry. Refreshing or selecting the saved offer uses the
  response returned by the existing save flow so the displayed company name reflects edits.
- Add Details sections for Responsibilities, Benefits, Nice to Have, and Compensation. Keep
  Company Name represented in the offer list; it need not be duplicated in Details unless the
  existing layout requires it.
- Render `jobDescription`, `requiredQualifications`, `responsibilities`, `benefits`, and
  `niceToHave` as Markdown in Details. `compensation` remains plain text because the requirements do
  not request Markdown rendering for it.
- Support headings, bold, italics, unordered and ordered lists, and links. Sanitize generated HTML
  before DOM insertion, and allow only safe link protocols (`http`, `https`, and `mailto`) to avoid
  script execution through stored Markdown. Empty or null fields render as empty content.
- Keep list and Details content sourced from the existing API response; do not add client-side
  persistence.

## 4. Layout

- Set the main page content wrapper to `width: min(1480px, 100%)` so it is 1480 px on sufficiently
  wide viewports and fits narrower screens without horizontal overflow.
- Set the Details panel to a 600 px desktop width, capped at the available parent width on smaller
  screens. Align it to the right when it sits beside the offer list, and stack the panels at the
  existing responsive breakpoint when they do not fit.
- Move Edit and New Job Offer into a distinct action panel following the main content. Give that
  panel the same `min(1480px, 100%)` width and keep both buttons inside its boundaries.
- Preserve the existing selection rule: New Job Offer is always visible; Edit is visible only when
  an offer is selected.

## 5. Compatibility and Error Handling

- Missing values in older MongoDB documents map to `null`; the SPA displays empty content for them.
- The existing save error behavior and API error contracts are unchanged.
- Markdown output is untrusted input. Never assign unsanitized parser output to `innerHTML`.
- If the Markdown library is unavailable, show the field as text rather than inserting unparsed
  HTML.

## 6. Verification Design

- Java mapping tests verify all five new properties survive document/request/response conversion.
- Controller or integration tests verify POST and PUT persist and return the added properties while
  requests omitting them remain valid.
- jsdom tests verify the five form controls, edit prefill and payload inclusion, company name in
  list entries after create/update, Markdown formatting for each required field, and safe handling
  of HTML/script and unsafe links.
- Browser layout checks verify the 1480 px maximum main/action panel widths, 600 px desktop Details
  width and narrow-screen cap, action button containment, and selection-dependent Edit visibility
  at desktop and narrow viewport sizes. They also verify that the five specified fields remain
  vertically ordered in both Details and the modal form.
