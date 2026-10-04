package org.blr.repository;

import org.blr.domain.RepositoryGraphStatus;

public record RegisteredRepository(
    String repositoryId,
    String repositoryName,
    String gitUrl,
    String defaultBranch,
    RepositoryGraphStatus graphStatus
) {
}
