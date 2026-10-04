package org.blr.storage;

import java.time.Duration;

import org.blr.config.GraphIngestionProperties;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;

import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "graph-ingestion.storage", name = "provider", havingValue = "azure")
public class AzureBlobGraphArtifactUploadService implements GraphArtifactUploadService {

    private final GraphIngestionProperties properties;
    private final BlobContainerClient containerClient;

    public AzureBlobGraphArtifactUploadService(GraphIngestionProperties properties) {
        this.properties = properties;

        String accountUrl = properties.storage().azure().accountUrl();
        if (accountUrl == null || accountUrl.isBlank()) {
            throw new IllegalStateException("graph-ingestion.storage.azure.account-url must be configured when provider=azure");
        }

        this.containerClient = new BlobContainerClientBuilder()
            .endpoint(accountUrl)
            .credential(new DefaultAzureCredentialBuilder().build())
            .containerName(properties.storage().azure().container())
            .buildClient();
    }

    @Override
    public GraphArtifactUploadResult uploadAndVerify(GraphArtifactUploadRequest request) {
        String basePath = blobBasePath(request.repositoryId(), request.commitSha());

        String archiveBlobName = basePath + "/" + request.artifact().archivePath().getFileName();
        String manifestBlobName = basePath + "/" + request.artifact().manifestPath().getFileName();

        BlockBlobItem archiveItem = retryUpload(
            () -> uploadSingleImmutable(archiveBlobName, request.artifact().archivePath().toAbsolutePath().toString()),
            "archive"
        );
        verifyBlob(archiveBlobName, request.artifact().archiveSizeBytes());

        BlockBlobItem manifestItem = retryUpload(
            () -> uploadSingleImmutable(manifestBlobName, request.artifact().manifestPath().toAbsolutePath().toString()),
            "manifest"
        );
        verifyBlob(manifestBlobName, null);

        String archiveUri = containerClient.getBlobClient(archiveBlobName).getBlobUrl();
        String manifestUri = containerClient.getBlobClient(manifestBlobName).getBlobUrl();

        log.info(
            "artifact_upload_completed provider=azure repositoryId={} commitSha={} archiveBlob={} manifestBlob={}",
            request.repositoryId(),
            request.commitSha(),
            archiveBlobName,
            manifestBlobName
        );

        return new GraphArtifactUploadResult(
            archiveUri,
            manifestUri,
            archiveItem.getETag(),
            manifestItem.getETag()
        );
    }

    private BlockBlobItem uploadSingleImmutable(String blobName, String path) {
        try {
            var blobClient = containerClient.getBlobClient(blobName);
            if (blobClient.exists()) {
                throw new AppException(
                    ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                    "Blob already exists for immutable artifact path: " + blobName
                );
            }
            BinaryData data = BinaryData.fromFile(java.nio.file.Path.of(path));
            return blobClient.getBlockBlobClient().upload(data, true);
        } catch (BlobStorageException ex) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                "Azure upload failed for blob " + blobName + ": " + ex.getMessage()
            );
        }
    }

    private void verifyBlob(String blobName, Long expectedContentLength) {
        try {
            var blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                throw new AppException(
                    ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                    "Uploaded blob not found: " + blobName
                );
            }
            if (expectedContentLength != null) {
                long actual = blobClient.getProperties().getBlobSize();
                if (actual != expectedContentLength) {
                    throw new AppException(
                        ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                        "Blob verification failed (size mismatch) for " + blobName
                    );
                }
            }
        } catch (BlobStorageException ex) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                "Azure verification failed for blob " + blobName + ": " + ex.getMessage()
            );
        }
    }

    private String blobBasePath(String repositoryId, String commitSha) {
        String prefix = properties.storage().azure().pathPrefix();
        String safePrefix = (prefix == null || prefix.isBlank()) ? "graph-ingestion" : prefix;
        String safeRepo = repositoryId.replaceAll("[^a-zA-Z0-9._-]", "-");
        return safePrefix + "/" + safeRepo + "/" + commitSha;
    }

    private BlockBlobItem retryUpload(java.util.concurrent.Callable<BlockBlobItem> upload, String label) {
        int maxAttempts = Math.max(1, properties.storage().maxAttempts());
        Duration backoff = properties.storage().initialBackoff();
        Duration maxBackoff = properties.storage().maxBackoff();

        AppException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return upload.call();
            } catch (AppException ex) {
                lastException = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                sleep(backoff, label, attempt);
                backoff = nextBackoff(backoff, maxBackoff);
            } catch (Exception ex) {
                throw new AppException(
                    ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
                    "Unexpected upload failure for " + label + ": " + ex.getMessage()
                );
            }
        }

        throw new AppException(
            ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED,
            "Azure upload failed after retries for " + label + ": " + (lastException == null ? "unknown" : lastException.getMessage())
        );
    }

    private void sleep(Duration duration, String label, int attempt) {
        try {
            Thread.sleep(duration.toMillis());
            log.warn("artifact_upload_retry label={} attempt={} nextBackoffMs={}", label, attempt + 1, duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.GRAPH_ARTIFACT_UPLOAD_FAILED, "Interrupted during upload retry backoff");
        }
    }

    private Duration nextBackoff(Duration current, Duration maxBackoff) {
        Duration doubled = current.multipliedBy(2);
        return doubled.compareTo(maxBackoff) > 0 ? maxBackoff : doubled;
    }
}
