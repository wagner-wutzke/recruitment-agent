package net.wowdev.recruitment.agent.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "job_offers")
public record JobOffer(
    @Id String id,
    String jobName,
    String jobDescription,
    String requiredQualifications,
    String location,
    String allocation) {}
