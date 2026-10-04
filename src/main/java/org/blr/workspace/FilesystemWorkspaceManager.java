package org.blr.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.blr.config.GraphIngestionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FilesystemWorkspaceManager implements WorkspaceManager {

    private final GraphIngestionProperties properties;

    public FilesystemWorkspaceManager(GraphIngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public WorkspaceContext prepareWorkspace(String buildId) {
        try {
            Path root = Path.of(properties.workspace().root(), buildId);
            Path source = root.resolve("source");
            Path codegraphHome = root.resolve("codegraph-home");
            Path artifact = root.resolve("artifact");

            Files.createDirectories(source);
            Files.createDirectories(codegraphHome);
            Files.createDirectories(artifact);

            return new WorkspaceContext(root, source, codegraphHome, artifact);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to prepare workspace", ex);
        }
    }

    @Override
    public void cleanupWorkspace(WorkspaceContext context) {
        if (context == null || context.root() == null || !Files.exists(context.root())) {
            return;
        }

        try (var walk = Files.walk(context.root())) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        log.warn("Failed to delete workspace path={}", path, ex);
                    }
                });
        } catch (IOException ex) {
            log.warn("Failed to cleanup workspace root={}", context.root(), ex);
        }
    }
}
