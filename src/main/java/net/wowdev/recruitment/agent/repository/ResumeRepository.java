package net.wowdev.recruitment.agent.repository;

import java.util.Optional;
import net.wowdev.recruitment.agent.entity.ResumeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ResumeRepository extends MongoRepository<ResumeDocument, String> {

  Optional<ResumeDocument> findByCandidateEmailIgnoreCase(String candidateEmail);
}
