package org.blr.application;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.blr.config.GraphIngestionProperties;
import org.blr.domain.GraphBuildStatus;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GitRepositoryJpaRepository;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RetentionCleanupService {

    private static final List<GraphBuildStatus> IN_PROGRESS_STATUSES = List.of(
        GraphBuildStatus.QUEUED,
        GraphBuildStatus.BUILDING,
        GraphBuildStatus.VALIDATING,
        GraphBuildStatus.PACKAGING,
        GraphBuildStatus.UPLOADING
    );

    private final GraphIngestionProperties properties;
    private final GraphBuildJpaRepository graphBuildJpaRepository;
    private final GitRepositoryJpaRepository gitRepositoryJpaRepository;

    public RetentionCleanupService(
        GraphIngestionProperties properties,
        GraphBuildJpaRepository graphBuildJpaRepository,
        GitRepositoryJpaRepository gitRepositoryJpaRepository
    ) {
        this.properties = properties;
        this.graphBuildJpaRepository = graphBuildJpaRepository;
        this.gitRepositoryJpaRepository = gitRepositoryJpaRepository;
    }

    @Transactional
    @Async("graphBuildExecutor")
    public void cleanupRepositoryAsync(String repositoryId) {
        try {
            cleanupRepository(repositoryId);
        } catch (Exception ex) {
            log.warn("retention_cleanup_failed repositoryId={} reason={}", repositoryId, ex.getMessage(), ex);
        }
    }

    @Transactional
    public void cleanupRepository(String repositoryId) {
        int versionsToKeep = Math.max(1, properties.retention().versions());

        RepositoryEntity repository = gitRepositoryJpaRepository.findByRepositoryIdForUpdate(repositoryId).orElse(null);
        if (repository == null) {
            log.info("retention_cleanup_skipped repositoryId={} reason=repository_missing", repositoryId);
            return;
        }

        List<GraphBuildEntity> publishedBuilds = graphBuildJpaRepository
            .findByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc(repositoryId, GraphBuildStatus.PUBLISHED);

        if (publishedBuilds.size() <= versionsToKeep) {
            log.info(
                "retention_cleanup_skipped repositoryId={} reason=within_limit publishedCount={} keep={}",
                repositoryId,
                publishedBuilds.size(),
                versionsToKeep
            );
            return;
        }

        Set<String> protectedBuildIds = new HashSet<>();
        for (int i = 0; i < Math.min(versionsToKeep, publishedBuilds.size()); i++) {
            protectedBuildIds.add(publishedBuilds.get(i).getBuildId());
        }

        for (GraphBuildEntity build : publishedBuilds) {
            if (build.getCommitSha() != null && build.getCommitSha().equals(repository.getActiveCommitSha())) {
                protectedBuildIds.add(build.getBuildId());
            }
            if (build.getCommitSha() != null && build.getCommitSha().equals(repository.getTargetCommitSha())) {
                protectedBuildIds.add(build.getBuildId());
            }
        }

        List<GraphBuildEntity> toDelete = new ArrayList<>();
        for (GraphBuildEntity build : publishedBuilds) {
            if (!protectedBuildIds.contains(build.getBuildId())) {
                toDelete.add(build);
            }
        }

        for (GraphBuildEntity build : toDelete) {
            deleteArtifactIfPossible(build.getBlobUri());
            build.setBlobUri(null);
            build.setErrorMessage(null);
            build.setCompletedAt(Instant.now());
        }

        graphBuildJpaRepository.deleteAll(toDelete);

        boolean hasInProgress = graphBuildJpaRepository.existsByRepository_RepositoryIdAndStatusIn(
            repositoryId,
            IN_PROGRESS_STATUSES
        );

        log.info(
            "retention_cleanup_completed repositoryId={} deleted={} protected={} inProgress={}",
            repositoryId,
            toDelete.size(),
            protectedBuildIds.size(),
            hasInProgress
        );
    }

    private void deleteArtifactIfPossible(String blobUri) {
        if (blobUri == null || blobUri.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(blobUri);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                log.info("retention_cleanup_artifact_skip uri={} reason=non_file_scheme", blobUri);
                return;
            }

            Path archivePath = Path.of(uri);
            Files.deleteIfExists(archivePath);

            String name = archivePath.getFileName() == null ? "" : archivePath.getFileName().toString();
            if (name.endsWith(".tar.gz")) {
                Path manifestPath = archivePath.resolveSibling(name.substring(0, name.length() - ".tar.gz".length()) + ".manifest.json");
                Files.deleteIfExists(manifestPath);
            }
        } catch (Exception ex) {
            log.warn("retention_cleanup_artifact_delete_failed uri={} reason={}", blobUri, ex.getMessage());
        }
    }
}
