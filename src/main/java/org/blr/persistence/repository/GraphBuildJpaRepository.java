package org.blr.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.blr.domain.GraphBuildStatus;
import org.blr.persistence.entity.GraphBuildEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraphBuildJpaRepository extends JpaRepository<GraphBuildEntity, String> {

    Optional<GraphBuildEntity> findByBuildIdAndRepository_RepositoryId(String buildId, String repositoryId);

    List<GraphBuildEntity> findByRepository_RepositoryIdOrderByCreatedAtDesc(String repositoryId);

    Optional<GraphBuildEntity> findFirstByRepository_RepositoryIdAndCommitShaOrderByCreatedAtDesc(
        String repositoryId,
        String commitSha
    );

    Optional<GraphBuildEntity> findFirstByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc(
        String repositoryId,
        GraphBuildStatus status
    );

    List<GraphBuildEntity> findByRepository_RepositoryIdAndStatusOrderByCreatedAtDesc(
        String repositoryId,
        GraphBuildStatus status
    );

    boolean existsByRepository_RepositoryIdAndStatusIn(String repositoryId, List<GraphBuildStatus> statuses);
}
