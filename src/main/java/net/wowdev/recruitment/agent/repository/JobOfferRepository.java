package net.wowdev.recruitment.agent.repository;

import net.wowdev.recruitment.agent.document.JobOffer;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobOfferRepository extends MongoRepository<JobOffer, String> {}
