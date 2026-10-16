# Feature Spec: UI Enhancements 01

- **Status:** [ ] Draft | [ ] Approved for Design | [x] In Implementation | [ ] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-15

## 1. Overview

Add a list of enhancements for better view of information in the job offers list and details.

## 2. Feature Scope

### In Scope

#### New text area fields

- [x] Item 1: Add a new field `Responsibilities` to the `JobOffer` document.
- [x] Item 2: Add a new field `Benefits` to the `JobOffer` document.
- [x] Item 3: Add a new field `Company Name` to the `JobOffer` document.
- [x] Item 4: Add a new field `Nice to Have` to the `JobOffer` document.
- [x] Item 5: Add a new text area field `Compensation` to the `JobOffer` document.

#### Markdown text for text areas

- [x] Item 6: When showing the `jobDescription`, `requiredQualifications`, `Benefits`,
  `Responsibilities` and `Nice to Have` fields in **Details**, the content must be formatted from
  Markdown content.

#### Job Offer list fields

- [x] Item 7: The `Company Name` field must be shown in the Job Offers list entries

#### UI changes

- [x] Item 8: Main page content wrapper must have a maximum width of 1480 px and fit narrower
  viewports.
- [x] Item 9: Details panel must be 600 px wide on desktop, capped at the available parent width on
  smaller viewports, and right-aligned when beside the offer list.
- [x] Item 10: **Buttons** Edit and **New Job Offer** must be contained by a new panel with max 1480
  px.
- [x] Item 11: Arrange the `Description`, `Responsibilities`, `Required qualifications`,
  `Nice to Have`, and `Benefits` fields vertically (one per row). Apply this specific order to both
  the Details panel and the modal form.

### Out of Scope

Nothing to describe.

## 3. Business Rules

Nothing to describe.

## 4. Acceptance Criteria

These scenarios must be validated and verified by the `@reviewer`.

### New text area fields

### Scenario 1: Adding the Responsibilities field

- **Given** the recruiter is creating or editing a job offer
- **When** the job offer form is displayed
- **Then** a new text area field named **Responsibilities** must be available.
- **And** the entered content must be stored in the `Responsibilities` field of the `JobOffer`
  document.
- **And** the saved content must be loaded when editing the job offer.

### Scenario 2: Adding the Benefits field

- **Given** the recruiter is creating or editing a job offer
- **When** the job offer form is displayed
- **Then** a new text area field named **Benefits** must be available.
- **And** the entered content must be stored in the `Benefits` field of the `JobOffer` document.
- **And** the saved content must be loaded when editing the job offer.

### Scenario 3: Adding the Company Name field

- **Given** the recruiter is creating or editing a job offer
- **When** the job offer form is displayed
- **Then** a new field named **Company Name** must be available.
- **And** the entered content must be stored in the `Company Name` field of the `JobOffer` document.
- **And** the saved content must be loaded when editing the job offer.

### Scenario 4: Adding the Nice to Have field

- **Given** the recruiter is creating or editing a job offer
- **When** the job offer form is displayed
- **Then** a new text area field named **Nice to Have** must be available.
- **And** the entered content must be stored in the `Nice to Have` field of the `JobOffer` document.
- **And** the saved content must be loaded when editing the job offer.

### Scenario 5: Adding the Compensation field

- **Given** the recruiter is creating or editing a job offer
- **When** the job offer form is displayed
- **Then** a new text area field named **Compensation** must be available.
- **And** the entered content must be stored in the `Compensation` field of the `JobOffer` document.
- **And** the saved content must be loaded when editing the job offer.

### Markdown text for text areas

### Scenario 6: Rendering Job Description as Markdown

- **Given** a job offer contains Markdown-formatted content in the `jobDescription` field
- **When** the recruiter selects the job offer and views its **Details** panel
- **Then** the `jobDescription` content must be rendered as formatted Markdown instead of raw
  Markdown syntax.

### Scenario 7: Rendering Required Qualifications as Markdown

- **Given** a job offer contains Markdown-formatted content in the `requiredQualifications` field
- **When** the recruiter selects the job offer and views its **Details** panel
- **Then** the `requiredQualifications` content must be rendered as formatted Markdown instead of
  raw Markdown syntax.

### Scenario 8: Rendering Responsibilities as Markdown

- **Given** a job offer contains Markdown-formatted content in the `Responsibilities` field
- **When** the recruiter selects the job offer and views its **Details** panel
- **Then** the `Responsibilities` content must be rendered as formatted Markdown instead of raw
  Markdown syntax.

### Scenario 9: Rendering Nice to Have as Markdown

- **Given** a job offer contains Markdown-formatted content in the `Nice to Have` field
- **When** the recruiter selects the job offer and views its **Details** panel
- **Then** the `Nice to Have` content must be rendered as formatted Markdown instead of raw Markdown
  syntax.

### Scenario 10: Supporting Markdown formatting

- **Given** a job offer contains Markdown syntax in any of the supported fields (`jobDescription`,
  `requiredQualifications`, `Responsibilities`, or `Nice to Have`)
- **When** the recruiter views the job offer in the **Details** panel
- **Then** Markdown elements such as headings, bold text, italic text, bullet lists, numbered lists,
  and links must be rendered with their corresponding formatting.
- **And** the original Markdown syntax must not be displayed as plain text.

### Job Offer list fields

### Scenario 11: Displaying Company Name in the Job Offers list

- **Given** the recruiter is viewing the Job Offers list
- **And** a job offer has a value stored in the `Company Name` field
- **When** the job offer entry is displayed
- **Then** the company name must be visible as part of the job offer entry.

### Scenario 12: Updating Company Name in the Job Offers list

- **Given** an existing job offer has a company name displayed in the Job Offers list
- **When** the recruiter edits the job offer and saves a different value in the `Company Name` field
- **Then** the Job Offers list must display the updated company name when the updated entry is
  loaded.

### Panel Layout and Dimensions

### Scenario 13: Setting the main parent panel width

- **Given** the recruiter is viewing the Job Offers page
- **When** the page is displayed
- **Then** the main page content wrapper must be **1480 px** wide when the viewport permits and must
  fit within narrower viewports without horizontal overflow.

### Scenario 14: Setting the Details panel width

- **Given** the recruiter is viewing the Job Offers page
- **When** the **Details** panel is displayed
- **Then** the Details panel must be **600 px** wide on desktop and no wider than its available
  parent width on smaller viewports.
- **And** when the Details panel is beside the offer list, it must be right-aligned with no right
  margin.

### Scenario 15: Adding a panel for action buttons

- **Given** the recruiter is viewing the Job Offers page
- **When** the page is displayed
- **Then** a new panel with a maximum width of **1480 px**, fitted to the available viewport, must
  contain the **Edit** and **New Job Offer** buttons.
- **And** both buttons must be positioned within the boundaries of the new panel.

### Scenario 16: Displaying action buttons based on job offer selection

- **Given** the recruiter is viewing the Job Offers page
- **When** no job offer is selected
- **Then** the **New Job Offer** button must be visible within the new action buttons panel.
- **And** the **Edit** button must be hidden.
- **When** the recruiter selects a job offer
- **Then** both the **Edit** and **New Job Offer** buttons must be visible within the new action
  buttons panel.

### Scenario 17: Arranging fields vertically in the Details panel

- **Given** the recruiter is viewing the Job Offers page
- **When** a job offer is selected and its details are displayed in the **Details** panel
- **Then** the **Description**, **Responsibilities**, **Required Qualifications**, **Nice to Have**,
  and **Benefits** fields must be arranged vertically, with one field per row.
- **And** the fields must appear in the following order:
    1. Description
    2. Responsibilities
    3. Required Qualifications
    4. Nice to Have
    5. Benefits

### Scenario 18: Arranging fields vertically in the Job Offer modal form

- **Given** the recruiter is creating or editing a job offer
- **When** the Job Offer modal form is displayed
- **Then** the **Description**, **Responsibilities**, **Required Qualifications**, **Nice to Have**,
  and **Benefits** fields must be arranged vertically, with one field per row.
- **And** the fields must appear in the following order:
    1. Description
    2. Responsibilities
    3. Required Qualifications
    4. Nice to Have
    5. Benefits
