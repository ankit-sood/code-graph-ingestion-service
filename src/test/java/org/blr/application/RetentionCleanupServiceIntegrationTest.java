package org.blr.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.blr.domain.GraphBuildStatus;
import org.blr.domain.RepositoryGraphStatus;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import org.blr.persistence.repository.RepositoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "graph-ingestion.retention.versions=1"
})
class RetentionCleanupServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private RetentionCleanupService retentionCleanupService;

    @Autowired
    private GraphBuildJpaRepository graphBuildJpaRepository;

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @BeforeEach
    void setUp() {
        graphBuildJpaRepository.deleteAll();
        repositoryJpaRepository.deleteAll();
    }

    @Test
    void shouldDeleteOldPublishedBuildArtifactsButKeepActiveAndLatest() throws Exception {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-retention");
        repository.setRepositoryName("repo-retention-name");
        repository.setGitUrl("https://example.org/repo-retention.git");
        repository.setDefaultBranch("main");
        repository.setGraphStatus(RepositoryGraphStatus.READY);
        repositoryJpaRepository.saveAndFlush(repository);

        Path oldArchive = tempDir.resolve("old.tar.gz");
        Path oldManifest = tempDir.resolve("old.manifest.json");
        Files.writeString(oldArchive, "old", StandardCharsets.UTF_8);
        Files.writeString(oldManifest, "old-manifest", StandardCharsets.UTF_8);

        Path latestArchive = tempDir.resolve("latest.tar.gz");
        Path latestManifest = tempDir.resolve("latest.manifest.json");
        Files.writeString(latestArchive, "latest", StandardCharsets.UTF_8);
        Files.writeString(latestManifest, "latest-manifest", StandardCharsets.UTF_8);

        GraphBuildEntity oldBuild = new GraphBuildEntity();
        oldBuild.setBuildId("build-old");
        oldBuild.setRepository(repository);
        oldBuild.setCommitSha("commit-old");
        oldBuild.setStatus(GraphBuildStatus.PUBLISHED);
        oldBuild.setBlobUri(oldArchive.toUri().toString());
        oldBuild.setStartedAt(Instant.now().minusSeconds(120));
        oldBuild.setCompletedAt(Instant.now().minusSeconds(110));
        graphBuildJpaRepository.saveAndFlush(oldBuild);

        Thread.sleep(5);

        GraphBuildEntity latestBuild = new GraphBuildEntity();
        latestBuild.setBuildId("build-latest");
        latestBuild.setRepository(repository);
        latestBuild.setCommitSha("commit-latest");
        latestBuild.setStatus(GraphBuildStatus.PUBLISHED);
        latestBuild.setBlobUri(latestArchive.toUri().toString());
        latestBuild.setStartedAt(Instant.now().minusSeconds(60));
        latestBuild.setCompletedAt(Instant.now().minusSeconds(50));
        graphBuildJpaRepository.saveAndFlush(latestBuild);

        repository.setActiveCommitSha("commit-latest");
        repository.setActiveGraphUri(latestArchive.toUri().toString());
        repositoryJpaRepository.saveAndFlush(repository);

        retentionCleanupService.cleanupRepository("repo-retention");

        List<GraphBuildEntity> published = graphBuildJpaRepository
            .findByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc("repo-retention", GraphBuildStatus.PUBLISHED);

        assertThat(published).hasSize(1);
        assertThat(published.get(0).getBuildId()).isEqualTo("build-latest");

        assertThat(Files.exists(oldArchive)).isFalse();
        assertThat(Files.exists(oldManifest)).isFalse();
        assertThat(Files.exists(latestArchive)).isTrue();
        assertThat(Files.exists(latestManifest)).isTrue();
    }

    @Test
    void shouldKeepTargetCommitEvenWhenBeyondRetentionLimit() throws Exception {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-target");
        repository.setRepositoryName("repo-target-name");
        repository.setGitUrl("https://example.org/repo-target.git");
        repository.setDefaultBranch("main");
        repository.setGraphStatus(RepositoryGraphStatus.BUILDING);
        repository.setTargetCommitSha("commit-protected");
        repositoryJpaRepository.saveAndFlush(repository);

        GraphBuildEntity protectedBuild = new GraphBuildEntity();
        protectedBuild.setBuildId("build-protected");
        protectedBuild.setRepository(repository);
        protectedBuild.setCommitSha("commit-protected");
        protectedBuild.setStatus(GraphBuildStatus.PUBLISHED);
        protectedBuild.setBlobUri(tempDir.resolve("protected.tar.gz").toUri().toString());
        graphBuildJpaRepository.saveAndFlush(protectedBuild);

        Thread.sleep(5);

        GraphBuildEntity latestBuild = new GraphBuildEntity();
        latestBuild.setBuildId("build-latest-target");
        latestBuild.setRepository(repository);
        latestBuild.setCommitSha("commit-latest");
        latestBuild.setStatus(GraphBuildStatus.PUBLISHED);
        latestBuild.setBlobUri(tempDir.resolve("latest-target.tar.gz").toUri().toString());
        graphBuildJpaRepository.saveAndFlush(latestBuild);

        retentionCleanupService.cleanupRepository("repo-target");

        List<GraphBuildEntity> published = graphBuildJpaRepository
            .findByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc("repo-target", GraphBuildStatus.PUBLISHED);

        assertThat(published).hasSize(2);
        assertThat(published.stream().map(GraphBuildEntity::getCommitSha)).contains("commit-protected", "commit-latest");
    }
}
