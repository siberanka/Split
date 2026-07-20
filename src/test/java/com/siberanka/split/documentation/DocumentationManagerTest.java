package com.siberanka.split.documentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsMissingDocumentationAndLeavesCurrentCopyUntouched() throws Exception {
        Path destination = temporaryDirectory.resolve("plugins/Split/WIKI.md");

        assertTrue(sync("version-one", destination));
        assertEquals("version-one", Files.readString(destination));
        assertFalse(sync("version-one", destination));
    }

    @Test
    void replacesOutdatedOrEditedDocumentation() throws Exception {
        Path destination = temporaryDirectory.resolve("WIKI.md");
        Files.writeString(destination, "old-or-edited");

        assertTrue(sync("bundled-current", destination));
        assertEquals("bundled-current", Files.readString(destination));
    }

    @Test
    void restoresDocumentationAfterDeletion() throws Exception {
        Path destination = temporaryDirectory.resolve("WIKI.md");
        assertTrue(sync("bundled", destination));
        Files.delete(destination);

        assertTrue(sync("bundled", destination));
        assertEquals("bundled", Files.readString(destination));
    }

    private static boolean sync(String content, Path destination) throws Exception {
        return DocumentationManager.sync(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                destination
        );
    }
}
