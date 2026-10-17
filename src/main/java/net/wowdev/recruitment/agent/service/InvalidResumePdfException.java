package net.wowdev.recruitment.agent.service;

public class InvalidResumePdfException extends RuntimeException {

  public InvalidResumePdfException(String message) {
    super(message);
  }

  public InvalidResumePdfException(String message, Throwable cause) {
    super(message, cause);
  }
}
