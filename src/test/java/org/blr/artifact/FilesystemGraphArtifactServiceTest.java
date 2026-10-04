package org.blr.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemGraphArtifactServiceTest {

    @TempDir
    Path tempDir;

    private final FilesystemGraphArtifactService service = new FilesystemGraphArtifactService();

    @Test
    void shouldDiscoverValidatePackageAndCreateManifest() throws IOException {
        Path outputRoot = tempDir.resolve("codegraph-home");
        Path projectDir = outputRoot.resolve("graph-output");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("nodes.json"), "{}", StandardCharsets.UTF_8);

        GraphProjectLayout layout = service.discoverAndValidate(outputRoot);
        assertThat(layout.projectDirectory()).isEqualTo(projectDir);
        assertThat(layout.fileCount()).isEqualTo(1);

        GraphArtifactResult result = service.packageAndGenerateManifest(
            new GraphArtifactRequest("build-1", "repo-1", "0123456789abcdef", tempDir.resolve("artifact")),
            layout
        );

        assertThat(result.archivePath()).exists();
        assertThat(result.manifestPath()).exists();
        assertThat(result.archiveEntryCount()).isGreaterThan(0);
        assertThat(result.archiveSha256()).isNotBlank();
        assertThat(result.archiveSizeBytes()).isGreaterThan(0L);

        String manifestContent = Files.readString(result.manifestPath(), StandardCharsets.UTF_8);
        assertThat(manifestContent).contains("repo-1");
        assertThat(manifestContent).contains("archiveSha256");
    }

    @Test
    void shouldRejectEmptyProjectDirectory() throws IOException {
        Path outputRoot = tempDir.resolve("codegraph-home-empty");
        Path projectDir = outputRoot.resolve("graph-output");
        Files.createDirectories(projectDir);

        assertThatThrownBy(() -> service.discoverAndValidate(outputRoot))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.GRAPH_VALIDATION_FAILED);
    }
}
