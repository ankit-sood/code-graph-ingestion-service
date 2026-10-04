package org.blr.persistence.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.blr.persistence.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, String> {

    Optional<RepositoryEntity> findByRepositoryName(String repositoryName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepositoryEntity r where r.repositoryId = :repositoryId")
    Optional<RepositoryEntity> findByRepositoryIdForUpdate(@Param("repositoryId") String repositoryId);
}
