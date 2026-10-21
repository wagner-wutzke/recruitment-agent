package net.wowdev.recruitment.agent.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.wowdev.recruitment.agent.dto.ApiErrorResponse;
import net.wowdev.recruitment.agent.dto.ResumeResponse;
import net.wowdev.recruitment.agent.service.resume.InvalidResumePdfException;
import net.wowdev.recruitment.agent.service.resume.model.ResumeDownload;
import net.wowdev.recruitment.agent.service.resume.ResumeDownloadException;
import net.wowdev.recruitment.agent.service.resume.ResumeNotFoundException;
import net.wowdev.recruitment.agent.service.resume.ResumeStorageException;
import net.wowdev.recruitment.agent.service.resume.model.ResumeUploadResult;
import net.wowdev.recruitment.agent.service.resume.ResumeUploadService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

  private final ResumeUploadService resumeUploadService;

  public ResumeController(ResumeUploadService resumeUploadService) {
    this.resumeUploadService = resumeUploadService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ResumeResponse> upload(
      @RequestParam(required = false) String candidateName,
      @RequestParam(required = false) MultipartFile file) {
    if (candidateName == null || candidateName.isBlank()) {
      throw new InvalidResumeRequestException("Candidate name is required.");
    }
    if (file == null || file.isEmpty()) {
      throw new InvalidResumeRequestException("A PDF file is required.");
    }

    ResumeUploadResult result;
    try {
      result = resumeUploadService.upload(candidateName, file.getBytes());
    } catch (IOException exception) {
      throw new InvalidResumeRequestException("The uploaded PDF could not be read.");
    }
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ResumeResponse.from(result.resume()));
  }

  @GetMapping("/{candidateEmail}/file")
  public ResponseEntity<byte[]> download(@PathVariable String candidateEmail) {
    ResumeDownload resume = resumeUploadService.download(candidateEmail);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(resume.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(resume.contents());
  }

  @ExceptionHandler(ResumeNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleResumeNotFound(ResumeNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiErrorResponse("RESUME_NOT_FOUND", "No resume was found for that candidate."));
  }

  @ExceptionHandler(ResumeDownloadException.class)
  public ResponseEntity<ApiErrorResponse> handleDownloadFailure() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            new ApiErrorResponse("RESUME_DOWNLOAD_FAILED", "Resume file could not be downloaded."));
  }

  @ExceptionHandler({
    InvalidResumeRequestException.class,
    InvalidResumePdfException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ApiErrorResponse> handleInvalidUpload(RuntimeException exception) {
    return ResponseEntity.badRequest()
        .body(new ApiErrorResponse("INVALID_RESUME_UPLOAD", exception.getMessage()));
  }

  @ExceptionHandler(ResumeStorageException.class)
  public ResponseEntity<ApiErrorResponse> handleStorageFailure() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiErrorResponse("RESUME_UPLOAD_FAILED", "Resume could not be saved."));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ApiErrorResponse> handleDependencyFailure() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiErrorResponse("RESUME_UPLOAD_FAILED", "Resume could not be saved."));
  }

  private static class InvalidResumeRequestException extends RuntimeException {
    private InvalidResumeRequestException(String message) {
      super(message);
    }
  }
}
