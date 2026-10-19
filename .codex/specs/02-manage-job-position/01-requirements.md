# Feature Spec: 02-manage-job-position

- **Status:** [ ] Draft | [ ] Approved for Design | [ ] In Implementation | [x] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-15

## 1. Overview

Allow the recruiter to create, view, edit, and delete a job position. Persist the position in the
system database. The initial version supports one active job position at a time.

## 2. Feature Scope

### In Scope

#### Job offer list and details

- [ ] Item 1: Selecting a job offer in the list displays its details in the **Details panel**
  (right-side panel).
- [ ] Item 2: When a job offer is selected, display an **Edit** button below the Details panel. When
  no job offer is selected, hide the **Edit** button.
- [ ] Item 3: Display a New Job Offer button below the Details panel. This button is available
  whether a job offer is selected.

#### Create and edit form

- [ ] Item 4: Clicking **New Job Offer** opens a modal containing an empty form for a new job offer.
- [ ] Item 5: The form provides Cancel and Save buttons. Clicking Cancel closes the modal without
  saving. A custom confirmation prompt is to be displayed, before discarding unsaved changes.
- [ ] Item 6: All form fields are required. If the recruiter clicks **Save** while any required
  field is empty, do not save the job offer. Highlight each empty field with a red border and show
  an accessible indication that the field is required. Validation error appear only after clicking *
  *Save**.
- [ ] Item 7: Clicking **Edit** opens the same modal with the selected job offer’s existing values
  loaded into the form. The recruiter can change those values and save the changes.
- [ ] Item 8: After a successful save, close the modal.
- [ ] Item 9: After a successful save, update the job offer list to reflect the saved job offer. If
  the saved offer is selected, update the Details panel as well.
- [ ] Item 10: After a successful save, the job offer must be selected in the list.
- [ ] Item 11: A failed save keeps the modal open and shows an error message.

#### Additional fields

- [ ] Item 12: The Allocation field is required and offers these values: **Remote**, **Hybrid**, and
  **On-site**.

### Out of Scope

- Nothing to describe now.

## 3. Business Rules

Nothing to declare.

## 4. Acceptance Criteria

These scenarios must be validated and verified by the `@reviewer`.

### Scenario 1: Selecting a job offer

- **Given** the recruiter is viewing the job offer list
- **When** they select a job offer
- **Then** the selected job offer’s details must appear in the right-side **Details** panel.

### Scenario 2: Displaying Edit for a selected job offer

- **Given** a job offer is selected in the list
- **When** its details are displayed in the Details panel
- **Then** an **Edit** button must appear below the panel.

### Scenario 3: Hiding Edit when no job offer is selected

- **Given** no job offer is selected in the list
- **When** the recruiter views the Details panel
- **Then** the **Edit** button must not be displayed.

### Scenario 4: Displaying New Job Offer

- **Given** the recruiter is viewing the job offer list
- **When** a job offer is selected or no job offer is selected
- **Then** a **New Job Offer** button must appear below the Details panel.

### Scenario 5: Opening a blank form for a new job offer

- **Given** the recruiter is viewing the job offer list
- **When** they click **New Job Offer**
- **Then** the system must open a modal containing a blank form for a new job offer
- **And** display **Cancel** and **Save** buttons in the form.

### Scenario 6: Canceling without unsaved changes

- **Given** the recruiter has opened the new job offer or edit form
- **And** has made no changes to the form
- **When** they click **Cancel**
- **Then** the system must close the modal without saving.

### Scenario 7: Requesting confirmation before discarding changes

- **Given** the recruiter has entered or changed form data
- **When** they click **Cancel**
- **Then** the system must display a custom confirmation prompt before discarding the changes.

### Scenario 8: Confirming discard of unsaved changes

- **Given** the custom confirmation prompt is displayed
- **When** the recruiter confirms that they want to discard the changes
- **Then** the system must close the modal without saving the changes.

### Scenario 9: Continuing to edit after cancel

- **Given** the custom confirmation prompt is displayed
- **When** the recruiter chooses to continue editing
- **Then** the system must close the prompt and keep the form open with its current values.

### Scenario 10: Validating required fields on save

- **Given** the recruiter is creating or editing a job offer
- **And** one or more required fields are empty
- **When** they click **Save**
- **Then** the system must not save the job offer
- **And** highlight each empty required field with a red border
- **And** show an accessible indication that each highlighted field is required.

### Scenario 11: Showing validation errors only after Save

- **Given** one or more required fields are empty
- **When** the recruiter has not clicked **Save**
- **Then** the system must not display required-field validation errors.

### Scenario 12: Editing a selected job offer

- **Given** a job offer is selected in the list
- **When** the recruiter clicks **Edit**
- **Then** the system must open the job offer form in a modal
- **And** preload the form with the selected job offer’s existing values
- **And** allow the recruiter to edit those values.

### Scenario 13: Saving a new job offer successfully

- **Given** the recruiter is creating a job offer
- **And** all required fields contain valid values
- **When** they click **Save**
- **And** the save succeeds
- **Then** the system must close the modal
- **And** update the job offer list to include the new offer
- **And** select the new offer in the list
- **And** display its details in the Details panel.

### Scenario 14: Saving changes to an existing job offer successfully

- **Given** the recruiter is editing an existing job offer
- **And** all required fields contain valid values
- **When** they click **Save**
- **And** the save succeeds
- **Then** the system must close the modal
- **And** update the job offer list to reflect the changes
- **And** select the updated offer in the list
- **And** display its updated details in the Details panel.

### Scenario 15: Handling a failed save

- **Given** the recruiter is creating or editing a job offer with valid form values
- **When** they click **Save**
- **And** the save fails
- **Then** the system must keep the modal open
- **And** display an error message.

### Scenario 16: Choosing an allocation value

- **Given** the recruiter is creating or editing a job offer
- **When** they view the required **Allocation** field
- **Then** the available values must be **Remote**, **Hybrid**, and **On-site**.