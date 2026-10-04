/*

*/
package org.blr.api.dto;

import org.blr.domain.RepositoryGraphStatus;

public record GraphStatusResponse(
    String repositoryId,
    String activeCommitSha,
    RepositoryGraphStatus status,
    String graphUri,
    String targetCommitSha
) {
}
