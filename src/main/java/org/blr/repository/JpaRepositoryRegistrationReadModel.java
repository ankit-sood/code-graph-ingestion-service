package org.blr.repository;

import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class JpaRepositoryRegistrationReadModel implements RepositoryRegistrationReadModel {

    private final RepositoryJpaRepository repositoryJpaRepository;

    public JpaRepositoryRegistrationReadModel(RepositoryJpaRepository repositoryJpaRepository) {
        this.repositoryJpaRepository = repositoryJpaRepository;
    }

    @Override
    public RegisteredRepository getById(String repositoryId) {
        RepositoryEntity entity = repositoryJpaRepository.findById(repositoryId)
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
        return repositoryJpaRepository.existsById(repositoryId);
    }
}
