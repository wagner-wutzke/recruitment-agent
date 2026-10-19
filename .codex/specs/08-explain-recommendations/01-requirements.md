# Feature Spec: Display Candidate Match Explanations

- **Status:** [ ] Draft | [ ] Approved for Design | [ ] In Implementation | [x] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-19

## 1. Objective and Context

Enhance the existing candidate matching results on the Job Offers page by displaying the
candidate's email and estimated match probability in each result block, and by providing an
**Explanations** link that opens a modal with the selected candidate's matched skills, relevant
work experience, and existing match probability.

Candidate search, ranking, and the current match probability are existing behavior. This feature
adds explanation data and changes how the results are presented; it must not change candidate
eligibility, ranking order, or the calculated match probability.

## 2. Business Rules

After matching candidates for a selected job offer, the ranked results are shown in the right-side
panel. Each result retains its ranking position and existing match probability. As specified by the
candidate-ranking feature, candidate identity displayed in each result (including email) comes from
the matching result returned by the LLM and grounded in retrieved resume evidence. Explanations
must use retrieved resume evidence and remain associated with the same candidate and selected job
offer.

## 3. Functional Requirements

- Each candidate match block must show its existing overall match probability on the right side.
- Each candidate match block must show the candidate's email in the middle of the block.
- Each candidate match block must provide an **Explanations** link at the right top edge of the
  panel (at the same vertical level of the ranking line).
- Each candidate match block must provide a link to that candidate's stored PDF resume directly
  below the **Explanations** link, with a visible PDF icon. The link uses the existing resume
  download route and must identify the same candidate as the match block.
- Activating a candidate's **Explanations** link must open a modal showing that candidate's
  explanation for the currently selected job offer.
- The explanation must include the existing match probability, matched skills, and relevant work
  experiences, with their relationship to the job offer requirements. The match probability is the
  same `[0,1]` value displayed in the candidate match block; it is not a separate score.
- Explanation claims must be supported by information from that candidate's retrieved resume; the
  LLM must not invent candidate attributes or work history.
- The right-side panel must not display the **Matches Panel** title.
- Changes to the result layout or explanation display must not change the existing candidate order,
  ranking positions, or calculated match probabilities.

## 4. Non-Functional Requirements

- Explanation data must remain associated with the candidate and job offer for which it was
  generated; it must not be shown for another candidate or offer.
- Resume links must use the existing resume-download endpoint and must not expose storage paths.

## 5. Acceptance Criteria

### Scenario 1: Displaying the candidate's email address

- **Given** the recruiter has selected a job offer
- **And** the candidate matching process has returned a ranked list of candidates
- **When** the matching results are displayed in the right-side panel
- **Then** each candidate match block must display the candidate's email address in the middle of
  the block.
- **And** the displayed email address must correspond to the respective candidate.

### Scenario 2: Displaying the estimated match probability

- **Given** the candidate matching process has returned an estimated match probability for each
  candidate
- **When** the matching results are displayed in the right-side panel
- **Then** each candidate match block must display the estimated match probability on the right side
  of the block.
- **And** the displayed probability must correspond to the value returned by the existing matching
  process.

### Scenario 3: Preserving the candidate's ranking position

- **Given** the candidate matching process has returned a ranked list of candidates
- **When** the matching results are displayed in the right-side panel
- **Then** each candidate match block must continue displaying its respective ranking position.
- **And** the ranking positions must correspond to the order returned by the existing ranking
  process.

### Scenario 4: Generating grounded explanations for candidate matches

- **Given** the existing matching process has evaluated a candidate against the selected job offer
- **When** the LLM generates the candidate's matching result
- **Then** the LLM must also provide an explanation supporting the candidate's existing match
  probability.
- **And** the explanation must identify the skills matching the job offer requirements.
- **And** the explanation must describe relevant work experiences supporting the match.
- **And** the explanation must be based only on information retrieved from that candidate's resume.
- **And** the explanation must not invent candidate skills, qualifications, or work experiences.

### Scenario 5: Opening the selected candidate's explanation

- **Given** the recruiter is viewing matching candidates in the right-side panel
- **When** they activate the **Explanations** link in a candidate's match block
- **Then** a new modal window must open.
- **And** the modal must display the matching explanation for the selected candidate.
- **And** the explanation must correspond to the selected job offer and candidate.

### Scenario 6: Displaying matched skills in the explanation modal

- **Given** the recruiter has opened the matching explanation modal for a candidate
- **When** the explanation is displayed
- **Then** the modal must show the candidate's skills that match the selected job offer
  requirements.
- **And** the matched skills must be supported by information retrieved from the candidate's resume.

### Scenario 7: Displaying relevant work experiences in the explanation modal

- **Given** the recruiter has opened the matching explanation modal for a candidate
- **When** the explanation is displayed
- **Then** the modal must show the candidate's relevant work experiences supporting the match.
- **And** the explanation must describe how those experiences relate to the selected job offer
  requirements.
- **And** the displayed information must be based on the candidate's resume.

### Scenario 8: Displaying the existing match probability in the explanation modal

- **Given** the recruiter has opened the matching explanation modal for a candidate
- **When** the explanation is displayed
- **Then** the modal must display the candidate's existing match probability.
- **And** the displayed probability must be the same `[0,1]` value returned by the existing
  matching process and displayed in that candidate's result block.
- **And** the explanation must describe how the candidate's skills and work experiences support
  that match probability without presenting it as a separately calculated score.

### Scenario 9: Displaying the Explanations link for each candidate

- **Given** the candidate matching process has returned a ranked list of candidates
- **When** the matching results are displayed in the right-side panel
- **Then** each candidate match block must provide an **Explanations** link at its upper-right
  edge, aligned vertically with the candidate's ranking position.
- **And** activating the link must open the explanation modal for that same candidate.

### Scenario 10: Displaying explanations for the correct candidate and job offer

- **Given** the right-side panel contains multiple matching candidates
- **And** each candidate has different matching information and explanations
- **When** the recruiter opens the explanation modal for a specific candidate
- **Then** the modal must display only the matching information associated with the selected
  candidate.
- **And** the displayed skills, work experiences, and match probability must not contain information
  belonging to another candidate or another job offer.

### Scenario 11: Closing the candidate match explanation modal

- **Given** the recruiter has opened the matching explanation modal for a candidate
- **When** they close the modal
- **Then** the modal must disappear.
- **And** the matching candidates list must remain visible with its existing results and ranking
  positions preserved.

### Scenario 12: Removing the Matches Panel title

- **Given** the recruiter is viewing the Job Offers page
- **When** the right-side candidate matching panel is displayed
- **Then** the text **"Matches Panel"** must not be visible.
- **And** the candidate match blocks must remain visible and accessible.

### Scenario 13: Preserving the existing candidate matching functionality

- **Given** the candidate search and ranking functionality has already been implemented
- **When** the recruiter requests candidate matching for a selected job offer
- **Then** the existing candidate search and ranking process must continue functioning.
- **And** the matching results must continue to be displayed in the right-side panel.
- **And** the new layout and explanations must not alter the existing candidate ranking order or
  calculated match probabilities.

### Scenario 14: Opening the stored candidate PDF

- **Given** the candidate matching process has returned a candidate with a stored PDF resume
- **When** the matching results are displayed
- **Then** the candidate match block must show a PDF icon link immediately below its **Explanations**
  link.
- **And** activating the link must request the existing resume-download route for that candidate's
  email address.
- **And** the link must not point to a local storage path.
