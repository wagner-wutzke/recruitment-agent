package net.wowdev.recruitment.agent;

import net.wowdev.recruitment.agent.service.matching.CandidateMatchingProperties;
import net.wowdev.recruitment.agent.service.matching.CandidateMatchingPipelineProperties;
import net.wowdev.recruitment.agent.service.resume.ResumeIndexingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
  ResumeIndexingProperties.class,
  CandidateMatchingProperties.class,
  CandidateMatchingPipelineProperties.class
})
public class RecruitmentAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(RecruitmentAgentApplication.class, args);
  }
}
