package org.blr.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.blr.config.GraphIngestionProperties;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "graph-ingestion.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalGraphArtifactUploadService implements GraphArtifactUploadService {

    private final GraphIngestionProperties properties;

    public LocalGraphArtifactUploadService(GraphIngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public GraphArtifactUploadResult uploadAndVerify(GraphArtifactUploadRequest request) {
        try {
            Path root = Path.of(properties.storage().local().root()).toAbsolutePath();
            Path targetDir = root.resolve(sanitize(request.repositoryId())).resolve(request.commitSha());
            Files.createDirectories(targetDir);

            Path archiveTarget = targetDir.resolve(request.artifact().archivePath().getFileName().toString());
            Path manifestTarget = targetDir.resolve(request.artifact().manifestPath().getFileName().toString());

            // Upload archive first to enforce required ordering.
            copyImmutable(request.artifact().archivePath(), archiveTarget);
            verifyCopy(request.artifact().archivePath(), archiveTarget);

            // Upload manifest second.
            copyImmutable(request.artifact().manifestPath(), manifestTarget);
            verifyCopy(request.artifact().manifestPath(), manifestTarget);

            log.info(
                "artifact_upload_completed provider=local repositoryId={} commitSha={} archive={} manifest={}",
                request.repositoryId(),
                request.commitSha(),
                archiveTarget,
                manifestTarget
            );

            return new GraphArtifactUploadResult(
                archiveTarget.toUri().toString(),
                manifestTarget.toUri().toString(),
                "local",
                "local"
            );
        } catch (IOException ex) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                "Local upload failed: " + ex.getMessage()
            );
        }
    }

    private void copyImmutable(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                "Refusing to overwrite existing artifact at " + target
            );
        }
        Files.copy(source, target);
    }

    private void verifyCopy(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            throw new AppException(ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED, "Uploaded artifact does not exist at " + target);
        }
        long sourceSize = Files.size(source);
        long targetSize = Files.size(target);
        if (sourceSize != targetSize) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                "Upload verification failed for " + target + " (size mismatch)"
            );
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
