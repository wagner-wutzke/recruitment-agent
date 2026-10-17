package net.wowdev.recruitment.agent;

import net.wowdev.recruitment.agent.service.ResumeIndexingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ResumeIndexingProperties.class)
public class RecruitmentAgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(RecruitmentAgentApplication.class, args);
  }
}
