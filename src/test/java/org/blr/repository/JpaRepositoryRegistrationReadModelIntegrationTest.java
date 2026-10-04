package org.blr.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.blr.domain.RepositoryGraphStatus;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.RepositoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JpaRepositoryRegistrationReadModelIntegrationTest {

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @Autowired
    private RepositoryRegistrationReadModel readModel;

    @Test
    void shouldLoadRegisteredRepositoryById() {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-read-1");
        repository.setRepositoryName("repo-read-one");
        repository.setGitUrl("https://example.org/repo-read-one.git");
        repository.setGraphStatus(RepositoryGraphStatus.READY);
        repositoryJpaRepository.saveAndFlush(repository);

        RegisteredRepository loaded = readModel.getById("repo-read-1");

        assertThat(loaded.repositoryId()).isEqualTo("repo-read-1");
        assertThat(loaded.repositoryName()).isEqualTo("repo-read-one");
        assertThat(loaded.defaultBranch()).isEqualTo("main");
        assertThat(readModel.exists("repo-read-1")).isTrue();
    }

    @Test
    void shouldThrowWhenRepositoryNotRegistered() {
        assertThatThrownBy(() -> readModel.getById("missing-repo"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPOSITORY_NOT_FOUND);
    }
}
