package net.wowdev.recruitment.agent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResumeFileStorage {

  private final Path directory;

  public ResumeFileStorage(
      @Value("${resume.storage.directory:${user.home}/data/recruitment-agent/resume}")
          Path directory) {
    this.directory = directory.toAbsolutePath().normalize();
  }

  private static void restore(Path target, Path backup) throws IOException {
    Files.deleteIfExists(target);
    if (backup != null && Files.exists(backup)) {
      Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public String store(String candidateName, byte[] pdf) throws IOException {
    try (StoredFile storedFile = storeForUpload(candidateName, pdf)) {
      String filename = storedFile.fileName();
      storedFile.commit();
      return filename;
    }
  }

  public byte[] read(String fileName) throws IOException {
    if (fileName == null || fileName.isBlank()) {
      throw new IOException("Resume filename is missing");
    }
    Path file = directory.resolve(fileName).normalize();
    if (!file.startsWith(directory) || !file.getFileName().toString().equals(fileName)) {
      throw new IOException("Resume filename is invalid");
    }
    return Files.readAllBytes(file);
  }

  public StoredFile storeForUpload(String candidateName, byte[] pdf) throws IOException {
    if (pdf == null) {
      throw new IllegalArgumentException("PDF file is required");
    }
    if (candidateName == null || candidateName.isBlank()) {
      throw new IllegalArgumentException("Candidate name is required");
    }
    if (candidateName.contains("/")
        || candidateName.contains("\\")
        || candidateName.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Candidate name contains invalid filename characters");
    }

    String filename = candidateName.replace(" ", "_") + ".pdf";
    Path target = directory.resolve(filename).normalize();
    Files.createDirectories(directory);
    Path backup =
        Files.exists(target) ? Files.createTempFile(directory, ".resume-backup-", ".tmp") : null;
    if (backup != null) {
      Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
    }
    try {
      Files.write(
          target,
          pdf,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      return new StoredFile(target, backup, filename);
    } catch (IOException exception) {
      restore(target, backup);
      throw exception;
    }
  }

  public static class StoredFile implements AutoCloseable {

    private final Path target;
    private final Path backup;
    private final String fileName;
    private boolean committed;

    private StoredFile(Path target, Path backup, String fileName) {
      this.target = target;
      this.backup = backup;
      this.fileName = fileName;
    }

    public String fileName() {
      return fileName;
    }

    public void commit() {
      committed = true;
      if (backup == null) {
        return;
      }
      try {
        Files.deleteIfExists(backup);
      } catch (IOException exception) {
        System.getLogger(ResumeFileStorage.class.getName())
            .log(System.Logger.Level.WARNING, "Unable to delete resume file backup", exception);
      }
    }

    public void rollback() throws IOException {
      if (!committed) {
        restore(target, backup);
        committed = true;
      }
    }

    @Override
    public void close() throws IOException {
      rollback();
    }
  }
}
