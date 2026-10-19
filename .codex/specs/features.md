# Feature List: Recruitment Agent

- **Status:** Draft
- **Source:** Revised requirements provided for `01-initial-application`

## F01 — Recruiter application

Provide a web application that helps HR and IT recruiters identify suitable candidates for an open
position.

## F02 — Manage job position

Allow the recruiter to create, view, edit, and delete a job position. Persist the position in the
system database. The initial version supports one active job position at a time.

## F03 — Upload and index candidate resumes

Allow candidate resumes to be uploaded as PDF files. Ingest and index resume content in a vector
database for candidate matching.

## F04 — Ground matching with indexed resume data

Use an AI-powered Retrieval-Augmented Generation (RAG) approach, grounding candidate matching in the
indexed resume data.

## F05 — Match candidates using skills and experience

Compare the skills and previous work experience described in the active job position with the skills
and experience in candidate resumes. Matching must account for semantic similarity and meaning-based
relationships, in addition to exact keyword matches.

## F06 — Rank candidates

Rank candidates by a total match score based on the comparison of job requirements and resume skills
and experience. In the initial version, use this criterion as the default ranking method.

## F07 — Explain candidate recommendations

For each recommended candidate, display the information supporting the ranking: matched skills,
relevant work experience, and overall score.
