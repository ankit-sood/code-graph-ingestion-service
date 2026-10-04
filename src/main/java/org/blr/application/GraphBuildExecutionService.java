package org.blr.application;

import java.time.Instant;
import java.util.List;

import org.blr.artifact.GraphArtifactRequest;
import org.blr.artifact.GraphArtifactResult;
import org.blr.artifact.GraphArtifactService;
import org.blr.artifact.GraphProjectLayout;
import org.blr.codegraph.CodeGraphRunRequest;
import org.blr.codegraph.CodeGraphRunResult;
import org.blr.codegraph.CodeGraphRunner;
import org.blr.domain.GraphBuildStatus;
import org.blr.domain.RepositoryGraphStatus;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.git.GitCheckoutRequest;
import org.blr.git.GitCheckoutResult;
import org.blr.git.GitRepositoryClient;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import org.blr.persistence.repository.RepositoryJpaRepository;
import org.blr.storage.GraphArtifactUploadRequest;
import org.blr.storage.GraphArtifactUploadResult;
import org.blr.storage.GraphArtifactUploadService;
import org.blr.workspace.WorkspaceContext;
import org.blr.workspace.WorkspaceManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class GraphBuildExecutionService {

    private static final List<GraphBuildStatus> IN_PROGRESS_STATUSES = List.of(
        GraphBuildStatus.QUEUED,
        GraphBuildStatus.BUILDING,
        GraphBuildStatus.VALIDATING,
        GraphBuildStatus.PACKAGING,
        GraphBuildStatus.UPLOADING
    );

    private final GraphBuildJpaRepository graphBuildJpaRepository;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final WorkspaceManager workspaceManager;
    private final GitRepositoryClient gitRepositoryClient;
    private final CodeGraphRunner codeGraphRunner;
    private final GraphArtifactService graphArtifactService;
    private final GraphArtifactUploadService graphArtifactUploadService;
    private final RetentionCleanupService retentionCleanupService;

    public GraphBuildExecutionService(
        GraphBuildJpaRepository graphBuildJpaRepository,
        RepositoryJpaRepository repositoryJpaRepository,
        WorkspaceManager workspaceManager,
        GitRepositoryClient gitRepositoryClient,
        CodeGraphRunner codeGraphRunner,
        GraphArtifactService graphArtifactService,
        GraphArtifactUploadService graphArtifactUploadService,
        RetentionCleanupService retentionCleanupService
    ) {
        this.graphBuildJpaRepository = graphBuildJpaRepository;
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.workspaceManager = workspaceManager;
        this.gitRepositoryClient = gitRepositoryClient;
        this.codeGraphRunner = codeGraphRunner;
        this.graphArtifactService = graphArtifactService;
        this.graphArtifactUploadService = graphArtifactUploadService;
        this.retentionCleanupService = retentionCleanupService;
    }

    @Async("graphBuildExecutor")
    public void executeBuildAsync(String buildId) {
        WorkspaceContext context = null;
        try {
            GraphBuildEntity initial = getRequiredBuild(buildId);
            context = workspaceManager.prepareWorkspace(buildId);

            transitionStatus(initial, GraphBuildStatus.BUILDING);

            GitCheckoutResult checkoutResult = gitRepositoryClient.checkout(new GitCheckoutRequest(
                initial.getRepository().getRepositoryId(),
                initial.getRepository().getGitUrl(),
                initial.getRepository().getDefaultBranch(),
                initial.getCommitSha(),
                context.source()
            ));

            GraphBuildEntity buildAfterCheckout = getRequiredBuild(buildId);
            buildAfterCheckout.setCommitSha(checkoutResult.resolvedCommitSha());
            graphBuildJpaRepository.saveAndFlush(buildAfterCheckout);

            updateRepositoryBuildState(
                buildAfterCheckout.getRepository(),
                checkoutResult.resolvedCommitSha(),
                RepositoryGraphStatus.BUILDING
            );

            CodeGraphRunResult runResult = codeGraphRunner.run(new CodeGraphRunRequest(
                buildAfterCheckout.getBuildId(),
                buildAfterCheckout.getRepository().getRepositoryId(),
                buildAfterCheckout.getCommitSha(),
                context.source(),
                context.codegraphHome(),
                context.artifact()
            ));

            log.info(
                "codegraph_execution_completed buildId={} repositoryId={} commitSha={} elapsedMs={} exitCode={} command={}",
                buildAfterCheckout.getBuildId(),
                buildAfterCheckout.getRepository().getRepositoryId(),
                buildAfterCheckout.getCommitSha(),
                runResult.elapsed().toMillis(),
                runResult.exitCode(),
                runResult.commandLine()
            );

            transitionStatus(getRequiredBuild(buildId), GraphBuildStatus.VALIDATING);
            GraphProjectLayout projectLayout = graphArtifactService.discoverAndValidate(runResult.generatedProjectPath());

            transitionStatus(getRequiredBuild(buildId), GraphBuildStatus.PACKAGING);
            GraphArtifactResult artifactResult = graphArtifactService.packageAndGenerateManifest(
                new GraphArtifactRequest(
                    buildAfterCheckout.getBuildId(),
                    buildAfterCheckout.getRepository().getRepositoryId(),
                    buildAfterCheckout.getCommitSha(),
                    context.artifact()
                ),
                projectLayout
            );

            GraphBuildEntity buildAfterPackaging = getRequiredBuild(buildId);
            buildAfterPackaging.setProjectSlug(artifactResult.projectSlug());
            buildAfterPackaging.setBlobUri(artifactResult.archivePath().toUri().toString());
            graphBuildJpaRepository.saveAndFlush(buildAfterPackaging);

            transitionStatus(getRequiredBuild(buildId), GraphBuildStatus.UPLOADING);
            GraphArtifactUploadResult uploadResult = graphArtifactUploadService.uploadAndVerify(
                new GraphArtifactUploadRequest(
                    buildAfterCheckout.getRepository().getRepositoryId(),
                    buildAfterCheckout.getCommitSha(),
                    artifactResult
                )
            );

            GraphBuildEntity buildAfterUpload = getRequiredBuild(buildId);
            buildAfterUpload.setBlobUri(uploadResult.archiveUri());
            graphBuildJpaRepository.saveAndFlush(buildAfterUpload);

            GraphBuildEntity published = publishBuildAtomically(buildId);

            log.info(
                "build_completed buildId={} repositoryId={} commitSha={} status={}",
                published.getBuildId(),
                published.getRepository().getRepositoryId(),
                published.getCommitSha(),
                published.getStatus()
            );

            retentionCleanupService.cleanupRepositoryAsync(published.getRepository().getRepositoryId());
        } catch (Exception ex) {
            handleFailure(buildId, ex);
        } finally {
            workspaceManager.cleanupWorkspace(context);
        }
    }

    @Transactional
    protected GraphBuildEntity transitionStatus(GraphBuildEntity build, GraphBuildStatus to) {
        GraphBuildStatus from = build.getStatus();
        if (from == to) {
            return build;
        }

        if (to == GraphBuildStatus.BUILDING && build.getStartedAt() == null) {
            build.setStartedAt(Instant.now());
        }

        build.setStatus(to);
        GraphBuildEntity saved = graphBuildJpaRepository.saveAndFlush(build);

        log.info(
            "build_transition buildId={} repositoryId={} commitSha={} from={} to={}",
            saved.getBuildId(),
            saved.getRepository().getRepositoryId(),
            saved.getCommitSha(),
            from,
            to
        );

        return saved;
    }

    @Transactional
    protected void updateRepositoryBuildState(RepositoryEntity repository, String targetCommitSha, RepositoryGraphStatus status) {
        repository.setTargetCommitSha(targetCommitSha);
        repository.setGraphStatus(status);
        repositoryJpaRepository.saveAndFlush(repository);
    }

    @Transactional
    protected GraphBuildEntity publishBuildAtomically(String buildId) {
        GraphBuildEntity build = getRequiredBuild(buildId);
        RepositoryEntity lockedRepository = repositoryJpaRepository.findByRepositoryIdForUpdate(
            build.getRepository().getRepositoryId()
        ).orElseThrow(() -> new IllegalStateException("Repository not found while publishing build: " + buildId));

        if (build.getBlobUri() == null || build.getBlobUri().isBlank()) {
            throw new AppException(ErrorCode.GRAPH_PUBLICATION_FAILED, "Blob URI missing while publishing build " + buildId);
        }

        GraphBuildEntity published = transitionStatus(build, GraphBuildStatus.PUBLISHED);
        published.setCompletedAt(Instant.now());
        graphBuildJpaRepository.saveAndFlush(published);

        GraphBuildEntity latestPublished = graphBuildJpaRepository
            .findFirstByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc(
                lockedRepository.getRepositoryId(),
                GraphBuildStatus.PUBLISHED
            )
            .orElse(published);

        boolean shouldBecomeActive = latestPublished.getBuildId().equals(published.getBuildId());
        if (shouldBecomeActive) {
            lockedRepository.setActiveCommitSha(published.getCommitSha());
            lockedRepository.setActiveGraphUri(published.getBlobUri());
        } else {
            log.info(
                "build_publication_skipped buildId={} repositoryId={} reason=stale_publish latestBuildId={}",
                published.getBuildId(),
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

        repositoryJpaRepository.saveAndFlush(lockedRepository);
        return published;
    }

    @Transactional(readOnly = true)
    protected GraphBuildEntity getRequiredBuild(String buildId) {
        return graphBuildJpaRepository.findById(buildId)
            .orElseThrow(() -> new IllegalStateException("Build not found: " + buildId));
    }

    @Transactional
    protected void handleFailure(String buildId, Exception ex) {
        GraphBuildEntity build = graphBuildJpaRepository.findById(buildId).orElse(null);
        if (build == null) {
            log.error("build_failed_without_record buildId={} reason={}", buildId, ex.getMessage(), ex);
            return;
        }

        ErrorCode mappedErrorCode = (ex instanceof AppException appException)
            ? appException.getErrorCode()
            : ErrorCode.INTERNAL_ERROR;

        build.setStatus(GraphBuildStatus.FAILED);
        build.setErrorCode(mappedErrorCode);
        build.setErrorMessage(ex.getMessage());
        build.setCompletedAt(Instant.now());
        graphBuildJpaRepository.saveAndFlush(build);

        RepositoryEntity repository = build.getRepository();
        repository.setTargetCommitSha(null);
        repository.setGraphStatus(RepositoryGraphStatus.DEGRADED);
        repositoryJpaRepository.saveAndFlush(repository);

        log.error(
            "build_failed buildId={} repositoryId={} commitSha={} error={}",
            build.getBuildId(),
            repository.getRepositoryId(),
            build.getCommitSha(),
            ex.getMessage(),
            ex
        );
    }
}
