package net.wowdev.recruitment.agent.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import net.wowdev.recruitment.agent.entity.JobOffer;
import net.wowdev.recruitment.agent.dto.ApiErrorResponse;
import net.wowdev.recruitment.agent.dto.JobOfferRequest;
import net.wowdev.recruitment.agent.dto.JobOfferResponse;
import net.wowdev.recruitment.agent.service.JobOfferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-offers")
public class JobOfferController {

  private final JobOfferService jobOfferService;

  public JobOfferController(JobOfferService jobOfferService) {
    this.jobOfferService = jobOfferService;
  }

  @GetMapping
  public List<JobOfferResponse> findAll() {
    try {
      return jobOfferService.findAll().stream().map(JobOfferResponse::from).toList();
    } catch (RuntimeException exception) {
      throw new JobOffersUnavailableException(exception);
    }
  }

  @PostMapping
  public ResponseEntity<JobOfferResponse> create(@Valid @RequestBody JobOfferRequest request) {
    try {
      JobOffer saved = jobOfferService.create(request.toDocument(null));
      return ResponseEntity.status(HttpStatus.CREATED).body(JobOfferResponse.from(saved));
    } catch (RuntimeException exception) {
      throw new JobOfferSaveFailedException(exception);
    }
  }

  @PutMapping("/{id}")
  public JobOfferResponse update(
      @PathVariable String id, @Valid @RequestBody JobOfferRequest request) {
    try {
      return JobOfferResponse.from(jobOfferService.update(id, request.toDocument(id)));
    } catch (NoSuchElementException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new JobOfferSaveFailedException(exception);
    }
  }

  @ExceptionHandler(JobOffersUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handleUnavailable() {
    return new ApiErrorResponse("JOB_OFFERS_UNAVAILABLE", "Job offers could not be loaded.");
  }

  @ExceptionHandler(JobOfferSaveFailedException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handleSaveFailed() {
    return new ApiErrorResponse("JOB_OFFER_SAVE_FAILED", "Job offer could not be saved.");
  }

  @ExceptionHandler(NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiErrorResponse handleNotFound() {
    return new ApiErrorResponse("JOB_OFFER_NOT_FOUND", "Job offer was not found.");
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handleInvalidRequest() {
    return new ApiErrorResponse("INVALID_JOB_OFFER", "All fields must be valid and complete.");
  }

  private static class JobOffersUnavailableException extends RuntimeException {
    private JobOffersUnavailableException(Throwable cause) {
      super(cause);
    }
  }

  private static class JobOfferSaveFailedException extends RuntimeException {
    private JobOfferSaveFailedException(Throwable cause) {
      super(cause);
    }
  }
}
