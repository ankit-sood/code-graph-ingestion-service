/*

*/
package org.blr.api.dto;

import java.time.Instant;

import org.blr.domain.GraphBuildStatus;

public record GraphBuildStatusResponse(
    String buildId,
    String repositoryId,
    String commitSha,
    GraphBuildStatus status,
    Instant startedAt,
    Instant completedAt
) {
}
