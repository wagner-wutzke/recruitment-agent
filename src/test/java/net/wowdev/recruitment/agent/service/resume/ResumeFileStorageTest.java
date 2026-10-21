package net.wowdev.recruitment.agent.service.resume;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumeFileStorageTest {

  @TempDir Path tempDirectory;

  @Test
  void storesPdfUsingCandidateNameWithSpacesReplaced() throws IOException {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    byte[] pdf = "%PDF-test".getBytes();

    String filename = storage.store("Ada Lovelace", pdf);

    assertEquals("Ada_Lovelace.pdf", filename);
    assertArrayEquals(pdf, Files.readAllBytes(tempDirectory.resolve("resumes/Ada_Lovelace.pdf")));
  }

  @Test
  void readsRetainedPdfBySafeFilename() throws IOException {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory.resolve("resumes"));
    byte[] pdf = "%PDF-download".getBytes();
    storage.store("Ada Lovelace", pdf);

    assertArrayEquals(pdf, storage.read("Ada_Lovelace.pdf"));
  }

  @Test
  void rejectsUnsafeOrMissingRetainedFilenames() {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory.resolve("resumes"));

    assertThrows(IOException.class, () -> storage.read("../outside.pdf"));
    assertThrows(IOException.class, () -> storage.read("missing.pdf"));
  }

  @Test
  void replacesExistingFileForCandidateName() throws IOException {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory);
    storage.store("Ada Lovelace", "old".getBytes());

    storage.store("Ada Lovelace", "new".getBytes());

    assertArrayEquals(
        "new".getBytes(), Files.readAllBytes(tempDirectory.resolve("Ada_Lovelace.pdf")));
  }

  @Test
  void rejectsCandidateNamesThatCouldEscapeResumeDirectory() {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory);

    assertThrows(IllegalArgumentException.class, () -> storage.store("../outside", new byte[1]));
  }

  @Test
  void rejectsMissingPdfAndBlankCandidateName() {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory);

    assertThrows(IllegalArgumentException.class, () -> storage.store("Ada", null));
    assertThrows(IllegalArgumentException.class, () -> storage.store("  ", new byte[1]));
  }

  @Test
  void restoresOldFileWhenNewFilenameCannotBeWritten() throws IOException {
    ResumeFileStorage storage = new ResumeFileStorage(tempDirectory);
    String longCandidateName = "A".repeat(300);

    assertThrows(IOException.class, () -> storage.store(longCandidateName, new byte[1]));
  }

  @Test
  void surfacesFilesystemWriteFailures() throws IOException {
    Path notDirectory = tempDirectory.resolve("file");
    Files.writeString(notDirectory, "not a directory");
    ResumeFileStorage storage = new ResumeFileStorage(notDirectory.resolve("resumes"));

    assertThrows(IOException.class, () -> storage.store("Ada", new byte[1]));
  }
}
