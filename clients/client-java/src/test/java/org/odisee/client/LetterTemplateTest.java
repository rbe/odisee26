package org.odisee.client;

// id: ADR-0001

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Test;

public class LetterTemplateTest {

  @Test
  public void templateDeclaresTheHalloField() throws Exception {
    final Path file = Files.createTempFile("Letter", ".ott");
    try {
      LetterTemplate.write(file);
      try (ZipFile zip = new ZipFile(file.toFile())) {
        final String content =
            new String(
                zip.getInputStream(zip.getEntry("content.xml")).readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(content.startsWith("<?xml"));
        assertTrue(content.contains("text:name=\"Hallo\""));
      }
    } finally {
      Files.deleteIfExists(file);
    }
  }
}
