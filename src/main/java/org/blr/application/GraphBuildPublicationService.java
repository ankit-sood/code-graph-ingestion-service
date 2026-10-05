package org.blr.application;

import java.time.Instant;
import java.util.List;

import org.blr.domain.GraphBuildStatus;
import org.blr.domain.RepositoryGraphStatus;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GitRepositoryJpaRepository;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class GraphBuildPublicationService {

    private static final List<GraphBuildStatus> IN_PROGRESS_STATUSES = List.of(
        GraphBuildStatus.QUEUED,
        GraphBuildStatus.BUILDING,
        GraphBuildStatus.VALIDATING,
        GraphBuildStatus.PACKAGING,
        GraphBuildStatus.UPLOADING
    );

    private final GraphBuildJpaRepository graphBuildJpaRepository;
    private final GitRepositoryJpaRepository gitRepositoryJpaRepository;

    public GraphBuildPublicationService(
        GraphBuildJpaRepository graphBuildJpaRepository,
        GitRepositoryJpaRepository gitRepositoryJpaRepository
    ) {
        this.graphBuildJpaRepository = graphBuildJpaRepository;
        this.gitRepositoryJpaRepository = gitRepositoryJpaRepository;
    }

    @Transactional
    public GraphBuildEntity publishBuildAtomically(String buildId) {
        GraphBuildEntity build = graphBuildJpaRepository.findById(buildId)
            .orElseThrow(() -> new IllegalStateException("Build not found: " + buildId));

        RepositoryEntity lockedRepository = gitRepositoryJpaRepository.findByRepositoryIdForUpdate(
            build.getRepository().getRepositoryId()
        ).orElseThrow(() -> new IllegalStateException("Repository not found while publishing build: " + buildId));

        if (build.getBlobUri() == null || build.getBlobUri().isBlank()) {
            throw new AppException(ErrorCode.GRAPH_PUBLICATION_FAILED, "Blob URI missing while publishing build " + buildId);
        }

        GraphBuildStatus from = build.getStatus();
        if (from != GraphBuildStatus.PUBLISHED) {
            build.setStatus(GraphBuildStatus.PUBLISHED);
            build.setCompletedAt(Instant.now());
            build = graphBuildJpaRepository.saveAndFlush(build);
            log.info(
                "build_transition buildId={} repositoryId={} commitSha={} from={} to={}",
                build.getBuildId(),
                build.getRepository().getRepositoryId(),
                build.getCommitSha(),
                from,
                GraphBuildStatus.PUBLISHED
            );
        }

        GraphBuildEntity latestPublished = graphBuildJpaRepository
            .findFirstByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc(
                lockedRepository.getRepositoryId(),
                GraphBuildStatus.PUBLISHED
            )
            .orElse(build);

        boolean shouldBecomeActive = latestPublished.getBuildId().equals(build.getBuildId());
        if (shouldBecomeActive) {
            lockedRepository.setActiveCommitSha(build.getCommitSha());
            lockedRepository.setActiveGraphUri(build.getBlobUri());
        } else {
            log.info(
                "build_publication_skipped buildId={} repositoryId={} reason=stale_publish latestBuildId={}",
                build.getBuildId(),
                lockedRepository.getRepositoryId(),
                latestPublished.getBuildId()
            );
        }

        boolean hasInProgressBuild = graphBuildJpaRepository.existsByRepository_RepositoryIdAndStatusIn(
            lockedRepository.getRepositoryId(),
            IN_PROGRESS_STATUSES
        );
        if (hasInProgressBuild) {
            lockedRepository.setGraphStatus(RepositoryGraphStatus.BUILDING);
        } else {
            lockedRepository.setGraphStatus(RepositoryGraphStatus.READY);
            lockedRepository.setTargetCommitSha(null);
        }

        gitRepositoryJpaRepository.saveAndFlush(lockedRepository);
        return build;
    }
}
