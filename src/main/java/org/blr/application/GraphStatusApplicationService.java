package org.blr.application;

import org.blr.api.dto.GraphStatusResponse;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.RepositoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphStatusApplicationService {

    private final RepositoryJpaRepository repositoryJpaRepository;

    public GraphStatusApplicationService(RepositoryJpaRepository repositoryJpaRepository) {
        this.repositoryJpaRepository = repositoryJpaRepository;
    }

    @Transactional(readOnly = true)
    public GraphStatusResponse getGraphStatus(String repositoryId) {
        RepositoryEntity repository = repositoryJpaRepository.findById(repositoryId)
            .orElseThrow(() -> new AppException(
                ErrorCode.REPOSITORY_NOT_FOUND,
                "Repository not found: " + repositoryId
            ));

        return new GraphStatusResponse(
            repository.getRepositoryId(),
            repository.getActiveCommitSha(),
            repository.getGraphStatus(),
            repository.getActiveGraphUri(),
            repository.getTargetCommitSha()
        );
    }
}
