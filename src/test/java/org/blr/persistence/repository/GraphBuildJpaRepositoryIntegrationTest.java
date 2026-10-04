package org.blr.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.blr.domain.GraphBuildStatus;
import org.blr.domain.RepositoryGraphStatus;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GraphBuildJpaRepositoryIntegrationTest {

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @Autowired
    private GraphBuildJpaRepository graphBuildJpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistBuildAndSupportStatusTransitionQuery() {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-build-1");
        repository.setRepositoryName("repo-build-one");
        repository.setGitUrl("https://example.org/repo-build-one.git");
        repository.setGraphStatus(RepositoryGraphStatus.BUILDING);
        repositoryJpaRepository.saveAndFlush(repository);

        GraphBuildEntity build = new GraphBuildEntity();
        build.setBuildId("build-1");
        build.setRepository(repository);
        build.setCommitSha("abc123");
        build.setStatus(GraphBuildStatus.QUEUED);
        build.setStartedAt(Instant.now());
        graphBuildJpaRepository.saveAndFlush(build);

        build.setStatus(GraphBuildStatus.BUILDING);
        build.setErrorCode(ErrorCode.INTERNAL_ERROR);
        graphBuildJpaRepository.saveAndFlush(build);

        GraphBuildEntity latest = graphBuildJpaRepository
            .findFirstByRepository_RepositoryIdAndCommitShaOrderByCreatedAtDesc("repo-build-1", "abc123")
            .orElseThrow();

        assertThat(latest.getStatus()).isEqualTo(GraphBuildStatus.BUILDING);
        assertThat(latest.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(latest.getVersion()).isNotNull();
    }

    @Test
    void shouldEnforceRepositoryForeignKey() {
        RepositoryEntity missingRepositoryRef = entityManager.getReference(RepositoryEntity.class, "missing-repo");

        GraphBuildEntity build = new GraphBuildEntity();
        build.setBuildId("build-missing-repo");
        build.setRepository(missingRepositoryRef);
        build.setCommitSha("def456");
        build.setStatus(GraphBuildStatus.QUEUED);

        assertThatThrownBy(() -> graphBuildJpaRepository.saveAndFlush(build))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
