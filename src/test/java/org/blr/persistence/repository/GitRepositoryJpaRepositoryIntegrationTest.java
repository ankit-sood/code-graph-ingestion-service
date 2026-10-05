package org.blr.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.blr.domain.RepositoryGraphStatus;
import org.blr.persistence.entity.RepositoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GitRepositoryJpaRepositoryIntegrationTest {

    @Autowired
    private GitRepositoryJpaRepository gitRepositoryJpaRepository;

    @Test
    void shouldPersistRepositoryAndSupportPessimisticLookup() {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-1");
        repository.setRepositoryName("repo-one");
        repository.setGitUrl("https://example.org/repo-one.git");
        repository.setDefaultBranch("main");
        repository.setGraphStatus(RepositoryGraphStatus.READY);

        gitRepositoryJpaRepository.saveAndFlush(repository);

        RepositoryEntity locked = gitRepositoryJpaRepository.findByRepositoryIdForUpdate("repo-1")
            .orElseThrow();

        assertThat(locked.getRepositoryId()).isEqualTo("repo-1");
        assertThat(locked.getVersion()).isNotNull();
        assertThat(locked.getCreatedAt()).isNotNull();
        assertThat(locked.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldEnforceUniqueRepositoryName() {
        RepositoryEntity first = new RepositoryEntity();
        first.setRepositoryId("repo-2");
        first.setRepositoryName("duplicate-name");
        first.setGitUrl("https://example.org/repo-two.git");
        first.setGraphStatus(RepositoryGraphStatus.UNKNOWN);

        RepositoryEntity second = new RepositoryEntity();
        second.setRepositoryId("repo-3");
        second.setRepositoryName("duplicate-name");
        second.setGitUrl("https://example.org/repo-three.git");
        second.setGraphStatus(RepositoryGraphStatus.UNKNOWN);

        gitRepositoryJpaRepository.saveAndFlush(first);

        assertThatThrownBy(() -> gitRepositoryJpaRepository.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
