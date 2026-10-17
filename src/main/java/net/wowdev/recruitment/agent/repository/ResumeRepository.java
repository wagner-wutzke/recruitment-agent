package net.wowdev.recruitment.agent.repository;

import net.wowdev.recruitment.agent.entity.ResumeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResumeRepository extends MongoRepository<ResumeDocument, String> {}
