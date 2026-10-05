package org.blr.application;

import java.util.UUID;

import org.blr.api.dto.GraphBuildAcceptedResponse;
import org.blr.api.dto.GraphBuildStatusResponse;
import org.blr.domain.GraphBuildStatus;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import org.blr.persistence.repository.GitRepositoryJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class GraphBuildApplicationService {

    private final GitRepositoryJpaRepository gitRepositoryJpaRepository;
    private final GraphBuildJpaRepository graphBuildJpaRepository;
    private final GraphBuildExecutionService graphBuildExecutionService;

    public GraphBuildApplicationService(
        GitRepositoryJpaRepository gitRepositoryJpaRepository,
        GraphBuildJpaRepository graphBuildJpaRepository,
        GraphBuildExecutionService graphBuildExecutionService
    ) {
        this.gitRepositoryJpaRepository = gitRepositoryJpaRepository;
        this.graphBuildJpaRepository = graphBuildJpaRepository;
        this.graphBuildExecutionService = graphBuildExecutionService;
    }

    @Transactional
    public GraphBuildAcceptedResponse createBuild(String repositoryId, String commitSha) {

        RepositoryEntity repository = gitRepositoryJpaRepository.findByRepositoryIdForUpdate(repositoryId)
            .orElseThrow(() -> new AppException(
                ErrorCode.REPOSITORY_NOT_FOUND,
                "Repository not found: " + repositoryId
            ));

        String resolvedCommitSha = (commitSha == null || commitSha.isBlank())
            ? "LATEST_MAIN"
            : commitSha;

        var existingBuild = graphBuildJpaRepository
            .findFirstByRepository_RepositoryIdAndCommitShaOrderByCreatedAtDesc(repositoryId, resolvedCommitSha)
            .orElse(null);

        if (existingBuild != null && existingBuild.getStatus() != GraphBuildStatus.FAILED) {
            log.info(
                "build_deduplicated repositoryId={} commitSha={} existingBuildId={} status={}",
                repositoryId,
                resolvedCommitSha,
                existingBuild.getBuildId(),
                existingBuild.getStatus()
            );
            return new GraphBuildAcceptedResponse(
                existingBuild.getBuildId(),
                repositoryId,
                existingBuild.getCommitSha(),
                existingBuild.getStatus()
            );
        }

        String buildId = UUID.randomUUID().toString();

        GraphBuildEntity build = new GraphBuildEntity();
        build.setBuildId(buildId);
        build.setRepository(repository);
        build.setCommitSha(resolvedCommitSha);
        build.setStatus(GraphBuildStatus.QUEUED);
        graphBuildJpaRepository.save(build);

        log.info(
            "build_transition buildId={} repositoryId={} commitSha={} from={} to={}",
            buildId,
            repositoryId,
            resolvedCommitSha,
            "NONE",
            GraphBuildStatus.QUEUED
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Schedule asynchronous execution after commit so the worker sees persisted rows.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    graphBuildExecutionService.executeBuildAsync(buildId);
                }
            });
        } else {
            graphBuildExecutionService.executeBuildAsync(buildId);
        }

        return new GraphBuildAcceptedResponse(
            buildId,
            repositoryId,
            resolvedCommitSha,
            GraphBuildStatus.QUEUED
        );
    }

    @Transactional(readOnly = true)
    public GraphBuildStatusResponse getBuildStatus(String repositoryId, String buildId) {
        GraphBuildEntity build = graphBuildJpaRepository.findByBuildIdAndRepository_RepositoryId(buildId, repositoryId)
            .orElseThrow(() -> new AppException(
                ErrorCode.INVALID_REQUEST,
                "Build not found for repository: " + buildId
            ));

        return new GraphBuildStatusResponse(
            build.getBuildId(),
            build.getRepository().getRepositoryId(),
            build.getCommitSha(),
            build.getStatus(),
            build.getStartedAt(),
            build.getCompletedAt()
        );
    }
}
