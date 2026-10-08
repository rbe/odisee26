package org.odisee.client;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertTrue;

public class LetterTemplateTest {

    @Test
    public void templateDeclaresTheHalloField() throws Exception {
        final Path file = Files.createTempFile("Letter", ".ott");
        try {
            LetterTemplate.write(file);
            try (ZipFile zip = new ZipFile(file.toFile())) {
                final String content = new String(zip.getInputStream(zip.getEntry("content.xml")).readAllBytes(),
                        StandardCharsets.UTF_8);
                assertTrue(content.startsWith("<?xml"));
                assertTrue(content.contains("text:name=\"Hallo\""));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

}
