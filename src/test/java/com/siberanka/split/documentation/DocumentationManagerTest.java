package com.siberanka.split.documentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsMissingDocumentationAndLeavesCurrentCopyUntouched() throws Exception {
        Path destination = temporaryDirectory.resolve("plugins/Split/wiki.yml");

        assertTrue(sync("version-one", destination));
        assertEquals("version-one", Files.readString(destination));
        assertFalse(sync("version-one", destination));
    }

    @Test
    void replacesOutdatedOrEditedDocumentation() throws Exception {
        Path destination = temporaryDirectory.resolve("wiki.yml");
        Files.writeString(destination, "old-or-edited");

        assertTrue(sync("bundled-current", destination));
        assertEquals("bundled-current", Files.readString(destination));
    }

    @Test
    void restoresDocumentationAfterDeletion() throws Exception {
        Path destination = temporaryDirectory.resolve("wiki.yml");
        assertTrue(sync("bundled", destination));
        Files.delete(destination);

        assertTrue(sync("bundled", destination));
        assertEquals("bundled", Files.readString(destination));
    }

    @Test
    void migratesLegacyMarkdownOnlyOnFirstYamlSync() throws Exception {
        Path destination = temporaryDirectory.resolve("wiki.yml");
        Path legacyDestination = temporaryDirectory.resolve("WIKI.md");
        Files.writeString(legacyDestination, "legacy-generated-guide");

        assertTrue(DocumentationManager.syncWithLegacyMigration(
                new ByteArrayInputStream("comment-only-yaml".getBytes(StandardCharsets.UTF_8)),
                destination,
                legacyDestination
        ));
        assertTrue(Files.exists(destination));
        assertFalse(Files.exists(legacyDestination));

        Files.writeString(legacyDestination, "personal-file-created-later");
        assertFalse(DocumentationManager.syncWithLegacyMigration(
                new ByteArrayInputStream("comment-only-yaml".getBytes(StandardCharsets.UTF_8)),
                destination,
                legacyDestination
        ));
        assertEquals("personal-file-created-later", Files.readString(legacyDestination));
    }

    @Test
    void bundledWikiIsCommentOnlyAndBilingual() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/wiki.yml");
        assertNotNull(stream);
        String content;
        try (stream) {
            content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(content.lines().allMatch(line -> line.startsWith("#")));
        assertTrue(content.contains("ENGLISH GUIDE"));
        assertTrue(content.contains("TÜRKÇE REHBER"));
        assertTrue(content.contains("Split 1.1.3"));
    }

    private static boolean sync(String content, Path destination) throws Exception {
        return DocumentationManager.sync(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                destination
        );
    }
}
