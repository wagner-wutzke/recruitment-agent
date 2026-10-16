# Task List: Recruiter Application

- **Status:** [x] Active | [ ] Paused | [x] Completed
- **Target Agent:** @engineer
- **Validation Agent:** @reviewer
- **References:** `01-requirements.md`, `02-design.md`

## Phase 1: Database & Model Layers

- [x] **Task 1.1:** Initialize the Maven build for Java 21 and Spring Boot 4.1.1 with Spring Web, Spring Data MongoDB, and test dependencies.
- [x] **Task 1.2:** Configure `spring.mongodb.uri` to read `MONGODB_URI`, with the local development URI as a fallback and no committed credentials.
- [x] **Task 1.3:** Extend `document/JobOffer` to map MongoDB `_id` to `id` and include `jobName`, `jobDescription`, `requiredQualifications`, `location`, and `allocation`; add a focused mapping test (**Requirement Item 6**).
- [x] **Task 1.4:** Add a `MongoRepository<JobOffer, String>` for read-only access to all records in `job_offers`.
- [x] **Task 1.5:** Add the `@SpringBootApplication` entry point in `net.wowdev.recruitment.agent` so the web application can start on port 8080 (**AC01**).

## Phase 2: Services & Business Logic

- [x] **Task 2.1:** Place `JobOfferService` in the `service` package and have it retrieve records through `JobOfferRepository`.
- [x] **Task 2.2:** Preserve the repository's empty result as an empty list from the service (**AC02, AC04**).
- [x] **Task 2.3:** Update service unit tests to cover populated and empty results using all fields in the current Job offer model.

## Phase 3: API Controllers & Routing

- [x] **Task 3.1:** Add a response DTO containing `id`, `jobName`, `jobDescription`, `requiredQualifications`, `location`, and `allocation`.
- [x] **Task 3.2:** Add a controller for `GET /api/job-offers` that calls the service and maps each document to the response DTO (**AC04**).
- [x] **Task 3.3:** Return `200 OK` with an empty JSON array when the service returns no records (**AC02, AC04**).
- [x] **Task 3.4:** Map MongoDB retrieval failures to the design's `503 Service Unavailable` error response (`JOB_OFFERS_UNAVAILABLE`); do not return an empty successful response.
- [x] **Task 3.5:** Add controller tests for populated results, the empty result, response field names, and database failure handling.

## Phase 4: Integration & End-to-End Testing

- [x] **Task 4.1:** Create the static SPA entry page under `src/main/resources/static` and verify Spring Boot serves it at `http://localhost:8080` (**AC01**).
- [x] **Task 4.2:** Build the page structure with application name “Recruitment Agent”, visible title “Recruitment made easy”, and left list/right details panels.
- [x] **Task 4.3:** Style the page with the specified dark blue and lilac palette.
- [x] **Task 4.4:** Fetch `GET /api/job-offers` on page load and render returned job names in the left panel (**AC04**).
- [x] **Task 4.5:** Render an empty list for an empty successful response and a distinct load-error state for a failed request (**AC02** and design error behavior).
- [x] **Task 4.6:** Keep the details panel empty before selection; on selection, render the job name, description, required qualifications, location, and allocation as text (**AC03, AC05**).
- [x] **Task 4.7:** Add integration or browser-level coverage for application startup, populated and empty responses, selection details, and load failure (**AC01–AC05**).
- [x] **Task 4.8:** Confirm the feature exposes no Job offer create, update, or delete UI or API operations (Out of Scope).
