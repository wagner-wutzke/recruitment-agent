# Feature Spec: Rank Matching Candidates

- **Status:** [ ] Draft | [ ] Approved for Design | [ ] In Implementation | [x] Completed
- **Author/Agent:** @architect
- **Date:** 2026-10-17

## 1. Objective and Context

Rank candidates by a total match score based on the comparison of job requirements against resume
skills and work experiencse.
The LLM is to be used to find the matches and rank them.
The candidates to be matched are to be found in the vector store.

## 2. Business Rules

After searching for matching candidates for the selected job offer, the list is shown on the right
side panel **Matches Panel** with the respective ranking position and matching probability.
The candidates list is ordered with the highest-ranked candidate first.

## 3. Functional Requirements

- Following Candidate information is to be taken into account:
    - Position
    - Technical Skills
    - Country
    - City
    - Working mode
    - Professional Experiences
- The LLM is responsible for matching candidates and ranking them based on grounded prompts.
- Retrieval Augmentation is to be used to find the matches.
- The grounding is based on the candidates Vector Store documents. Candidate names and
  email addresses returned to the frontend are resolved from the LLM response.
- Store the LLM system prompt in a separate text file. Configure its resource path through
  `candidate.matching.system-prompt-file` in application properties, so the prompt can be changed
  without modifying Java source.
- Configure the maximum number of resume documents retrieved per matching query through the
  `candidate.matching.top-k` application property. It must default to the existing value of `3`
  and accept only positive integers.

## 4. Non-Functional Requirements

- Nothing to declare

## 5. Acceptance Criteria

### Scenario 1: Retrieve and rank candidates for the selected job offer

- **Given** the recruiter has selected a job offer
- **When** they select **Find Candidates**
- **Then** Retrieval Augmentation retrieves candidate resume evidence from the Vector Store to
  ground the matching process
- **And** the LLM compares the selected job offer's requirements with retrieved resume skills and
  professional experience
- **And** the LLM considers each candidate's position, technical skills, country, city, working
  mode, and professional experience
- **And** the LLM returns matching candidates ranked from highest to lowest relevance

### Scenario 2: Return no matches when no eligible resume evidence is retrieved

- **Given** the selected offer has no resume evidence meeting the configured similarity threshold
- **When** candidate matching is requested
- **Then** no matching candidates are returned
- **And** no candidate profile is invented or returned

### Scenario 3: Display candidates in ranked order

- **Given** the matching model has ranked candidates represented by retrieved Vector Store
  documents
- **When** the ranking is displayed
- **Then** the Matches Panel appears on the right side of the page
- **And** candidates are displayed in ranking order, with the highest-ranked candidate first
- **And** each candidate displays its ranking position and matching probability

### Scenario 4: Ground rankings and candidate identity in application data

- **Given** the LLM has returned matching candidates grounded in retrieved Vector Store documents
- **When** the Matches Panel is rendered
- **Then** only candidates represented by the retrieved documents are included
- **And** candidate names and email addresses shown in the Matches Panel are taken from the LLM
  response

### Scenario 5: Load the ranking system prompt from a configured file

- **Given** the application has a configured system-prompt resource path that points to a readable,
  non-empty file
- **When** the candidate ranker is initialized and evaluates candidates
- **Then** it uses the contents of that file as the LLM system prompt
- **And** changing the prompt file does not require changing Java source
- **And** an unreadable or empty prompt file causes a clear initialization failure

## Acceptance-Criteria Evidence Map

- **AC1:** Retrieval and ranker tests verify the Vector Store grounds the LLM matching request and
  that every required job/resume comparison factor is considered.
- **AC2:** Matching and browser tests verify empty retrieved evidence produces no invented candidate
  profiles or displayed matches.
- **AC3:** Service and browser tests verify the Matches Panel location, descending ranking order,
  ranking positions, and matching probabilities.
- **AC4:** Matching and browser tests verify displayed candidate names and email addresses come from
  the LLM response and that only Vector Store-grounded candidates are included.
- **AC5:** Chat configuration tests verify configured prompt resource loading and failure for
  unreadable or empty prompt resources.
