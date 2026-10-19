# Technical Design: Recruiter Application

- **Status:** [ ] Draft | [x] Approved for Implementation
- **Author/Agent:** @architect
- **Reference Spec:** `01-requirements.md`

## 1. Architecture & Tech Stack

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Boot Web MVC
- **Frontend:** Single-page interface using HTML, vanilla JavaScript, CSS, and Bootstrap
- **Database:** MongoDB through Spring Data MongoDB
- **External Services:** none
- **Base package:** `net.wowdev.recruitment.agent`

Use a layered package structure:

```text
net.wowdev.recruitment.agent
├── RecruitmentAgentApplication.java
├── controller/
├── dto/
├── document/
├── repository/
└── service/
```

Serve the static SPA from `src/main/resources/static`. Spring Boot serves it at
`http://localhost:8080`; the backend API is under `/api`.

Configure MongoDB using `spring.mongodb.uri`. Read the URI from the `MONGODB_URI` environment
variable, with `mongodb://localhost:27017/recruitment_agent` as the local development default.
Do not put credentials in source-controlled configuration.

## 2. Data Model & MongoDB

Use the existing MongoDB `job_offers` collection. The read-only document contains only the fields
defined by the approved requirements:

```json
{
  "_id": "MongoDB ObjectId",
  "jobName": "Backend Engineer",
  "jobDescription": "Job description text",
  "requiredQualifications": "5 years experience Java Backend development, 3 years experience microservice on AWS",
  "location": "Dublin",
  "allocation": "Remote"
}
```

Map the document to `net.wowdev.recruitment.agent.entity.JobOffer` with `@Document(collection =
"job_offers")` and these Java fields: `String id`, `String jobName`, `String jobDescription`,
`String requiredQualifications`, `String location`, and `String allocation`. Map MongoDB `_id` to
`id` using Spring Data's `@Id`. Job offers are read through a `MongoRepository<JobOffer, String>`
in the repository layer. MongoDB does not require a relational schema migration for this feature.

This feature does not seed, create, update, or delete Job offers. The collection must be populated
by an existing or external process. An empty collection is a valid state and produces an empty
list.

## 3. API Contract

### `GET /api/job-offers`

Returns all Job offers for display in the SPA. No request body or query parameters are required.

**Success — `200 OK`**

```json
[
  {
    "id": "6650c4c93f1b8f0a6f8a1234",
    "jobName": "Backend Engineer",
    "jobDescription": "Job description text",
    "requiredQualifications": "5 years experience Java Backend development, 3 years experience microservice on AWS",
    "location": "Dublin",
    "allocation": "Remote"
  }
]
```

When no records exist, return `200 OK` with `[]`. Each response object exposes `id`, `jobName`,
`jobDescription`, `requiredQualifications`, `location`, and `allocation`.

**Database unavailable — `503 Service Unavailable`**

```json
{
  "code": "JOB_OFFERS_UNAVAILABLE",
  "message": "Job offers could not be loaded."
}
```

Do not convert database errors into an empty successful response; the client needs to distinguish
an empty collection from a failed request.

## 4. Request Flow & Error Handling

1. The SPA requests `GET /api/job-offers` once when the page loads.
2. The controller calls the Job offer service.
3. The service retrieves all records through `JobOfferRepository`.
4. The controller returns the response fields defined above.
5. The SPA displays the returned job names in the left panel. Selecting a name displays that
   record's job name, description, required qualifications, location, and allocation in the right
   panel.

If the repository returns no records, the service and API return an empty list. If MongoDB cannot
be reached, the API returns the unavailable response and the SPA displays a load error, not the
empty state. Render database text as text in the page; do not interpret job descriptions as HTML.

## 5. SPA Layout & State

- Display the application name **Recruitment Agent** and the visible title **Recruitment made easy
  **.
- Use the specified dark blue and lilac palette.
- Place the Job offers list in the left panel and selected-job details in the right panel.
- Start with no selected offer and an empty details panel.
- Show an empty left list when the API returns `[]`.
- After a successful response with records, list job names; on selection, show the selected name,
  description, required qualifications, location, and allocation.
- On request failure, show a distinct load-error state.

The requirements do not define exact color values, typography, responsive breakpoints, or
empty/error
copy. Keep those presentation choices consistent with the stated palette and behavior without
changing the API or adding Job offer management controls.
