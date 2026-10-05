package org.blr.repository;

import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GitRepositoryJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaRepositoryRegistrationReadModel implements RepositoryRegistrationReadModel {

    private final GitRepositoryJpaRepository gitRepositoryJpaRepository;

    public JpaRepositoryRegistrationReadModel(GitRepositoryJpaRepository gitRepositoryJpaRepository) {
        this.gitRepositoryJpaRepository = gitRepositoryJpaRepository;
    }

    @Override
    public RegisteredRepository getById(String repositoryId) {
        RepositoryEntity entity = gitRepositoryJpaRepository.findById(repositoryId)
            .orElseThrow(() -> new AppException(
                ErrorCode.REPOSITORY_NOT_FOUND,
                "Repository not found: " + repositoryId
            ));

        return new RegisteredRepository(
            entity.getRepositoryId(),
            entity.getRepositoryName(),
            entity.getGitUrl(),
            entity.getDefaultBranch(),
            entity.getGraphStatus()
        );
    }

    @Override
    public boolean exists(String repositoryId) {
        return gitRepositoryJpaRepository.existsById(repositoryId);
    }
}
