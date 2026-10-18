package net.wowdev.recruitment.agent.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import net.wowdev.recruitment.agent.dto.ApiErrorResponse;
import net.wowdev.recruitment.agent.dto.CandidateMatchRequest;
import net.wowdev.recruitment.agent.dto.CandidateMatchResponse;
import net.wowdev.recruitment.agent.service.CandidateMatchingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/candidate-matches")
public class CandidateMatchingController {

  private final CandidateMatchingService candidateMatchingService;

  public CandidateMatchingController(CandidateMatchingService candidateMatchingService) {
    this.candidateMatchingService = candidateMatchingService;
  }

  @PostMapping
  public List<CandidateMatchResponse> findCandidates(
      @Valid @RequestBody CandidateMatchRequest request) {
    try {
      return candidateMatchingService.findCandidates(request.jobOfferId()).stream()
          .map(CandidateMatchResponse::from)
          .toList();
    } catch (NoSuchElementException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new CandidateMatchingUnavailableException(exception);
    }
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiErrorResponse> handleJobOfferNotFound(NoSuchElementException ex) {
    log.error("Job offer not found.  Error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiErrorResponse("JOB_OFFER_NOT_FOUND", "Job offer was not found."));
  }

  @ExceptionHandler(CandidateMatchingUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleMatchingFailure(CandidateMatchingUnavailableException ex) {
    log.error("Candidate matching request failed. Error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            new ApiErrorResponse(
                "CANDIDATE_MATCHING_FAILED", "Candidate matching could not be completed."));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpMessageNotReadableException ex) {
    log.error("Candidate matching request failed. Error: {}", ex.getMessage());
    return ResponseEntity.badRequest()
        .body(
            new ApiErrorResponse(
                "INVALID_CANDIDATE_MATCH_REQUEST", "A job offer ID is required."));
  }

  private static class CandidateMatchingUnavailableException extends RuntimeException {
    private CandidateMatchingUnavailableException(Throwable cause) {
      super(cause);
    }
  }
}
