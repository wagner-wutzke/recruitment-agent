package net.wowdev.recruitment.agent.service;

public class ResumeNotFoundException extends RuntimeException {

  public ResumeNotFoundException(String candidateEmail) {
    super("No resume found for candidate email: " + candidateEmail);
  }
}
