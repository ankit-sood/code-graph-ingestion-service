package org.blr.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.blr.artifact.GraphArtifactResult;
import org.blr.config.GraphIngestionProperties;
import org.blr.config.sections.AzureStorageProperties;
import org.blr.config.sections.BuildProperties;
import org.blr.config.sections.CodeGraphProperties;
import org.blr.config.sections.GitProperties;
import org.blr.config.sections.LocalStorageProperties;
import org.blr.config.sections.RetentionProperties;
import org.blr.config.sections.StorageProperties;
import org.blr.config.sections.WorkspaceProperties;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGraphArtifactUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadArchiveThenManifestAndVerify() throws IOException {
        Path artifactDir = tempDir.resolve("artifact");
        Files.createDirectories(artifactDir);

        Path archive = artifactDir.resolve("repo.tar.gz");
        Path manifest = artifactDir.resolve("repo.manifest.json");
        Files.writeString(archive, "archive-bytes", StandardCharsets.UTF_8);
        Files.writeString(manifest, "manifest-bytes", StandardCharsets.UTF_8);

        LocalGraphArtifactUploadService service = new LocalGraphArtifactUploadService(properties(tempDir.resolve("storage")));
        GraphArtifactUploadResult result = service.uploadAndVerify(new GraphArtifactUploadRequest(
            "repo-6",
            "abcdef123456",
            new GraphArtifactResult(archive, manifest, "repo", "sha", Files.size(archive), 1)
        ));

        assertThat(result.archiveUri()).startsWith("file:");
        assertThat(result.manifestUri()).startsWith("file:");

        Path uploadedArchive = Path.of(java.net.URI.create(result.archiveUri()));
        Path uploadedManifest = Path.of(java.net.URI.create(result.manifestUri()));
        assertThat(uploadedArchive).exists();
        assertThat(uploadedManifest).exists();
        assertThat(Files.readString(uploadedArchive, StandardCharsets.UTF_8)).isEqualTo("archive-bytes");
        assertThat(Files.readString(uploadedManifest, StandardCharsets.UTF_8)).isEqualTo("manifest-bytes");
    }

    @Test
    void shouldFailWhenImmutableArchivePathAlreadyExists() throws IOException {
        Path artifactDir = tempDir.resolve("artifact-immutable");
        Files.createDirectories(artifactDir);

        Path archive = artifactDir.resolve("repo.tar.gz");
        Path manifest = artifactDir.resolve("repo.manifest.json");
        Files.writeString(archive, "archive-bytes", StandardCharsets.UTF_8);
        Files.writeString(manifest, "manifest-bytes", StandardCharsets.UTF_8);

        Path storageRoot = tempDir.resolve("storage-immutable");
        Path preexistingTarget = storageRoot.resolve("repo-6").resolve("abcdef123456").resolve("repo.tar.gz");
        Files.createDirectories(preexistingTarget.getParent());
        Files.writeString(preexistingTarget, "existing", StandardCharsets.UTF_8);

        LocalGraphArtifactUploadService service = new LocalGraphArtifactUploadService(properties(storageRoot));

        assertThatThrownBy(() -> service.uploadAndVerify(new GraphArtifactUploadRequest(
            "repo-6",
            "abcdef123456",
            new GraphArtifactResult(archive, manifest, "repo", "sha", Files.size(archive), 1)
        )))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED);
    }

    private GraphIngestionProperties properties(Path storageRoot) {
        return new GraphIngestionProperties(
            new WorkspaceProperties(tempDir.toAbsolutePath().toString()),
            new GitProperties("git", Duration.ofSeconds(60)),
            new CodeGraphProperties("/usr/bin/env", "true", Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofMillis(100)),
            new StorageProperties(
                "local",
                2,
                Duration.ofMillis(100),
                Duration.ofMillis(500),
                new LocalStorageProperties(storageRoot.toAbsolutePath().toString()),
                new AzureStorageProperties("", "container", "prefix")
            ),
            new RetentionProperties(5),
            new BuildProperties(2)
        );
    }
}
