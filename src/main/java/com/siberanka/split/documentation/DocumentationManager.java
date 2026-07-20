package com.siberanka.split.documentation;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the bundled, read-only documentation mirror current in the plugin data
 * directory. WIKI.md is never parsed as configuration and cannot affect runtime
 * behavior.
 */
public final class DocumentationManager {

    public static final String DOCUMENTATION_FILE = "WIKI.md";

    private final JavaPlugin plugin;

    public DocumentationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Restores a missing guide and replaces an outdated guide atomically.
     *
     * @return true when the on-disk copy was created or updated
     */
    public boolean sync() throws IOException {
        InputStream bundledDocumentation = plugin.getResource(DOCUMENTATION_FILE);
        if (bundledDocumentation == null) {
            throw new IOException("Bundled documentation resource is missing: " + DOCUMENTATION_FILE);
        }
        Path destination = plugin.getDataFolder().toPath().resolve(DOCUMENTATION_FILE);
        return sync(bundledDocumentation, destination);
    }

    static boolean sync(InputStream bundledDocumentation, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IOException("Documentation destination has no parent directory: " + destination);
        }
        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(parent, ".split-wiki-", ".tmp");
        try {
            try (InputStream input = bundledDocumentation;
                 OutputStream output = Files.newOutputStream(temporaryFile)) {
                input.transferTo(output);
            }

            if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(destination) == Files.size(temporaryFile)
                    && Files.mismatch(destination, temporaryFile) == -1L) {
                return false;
            }

            try {
                Files.move(
                        temporaryFile,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
