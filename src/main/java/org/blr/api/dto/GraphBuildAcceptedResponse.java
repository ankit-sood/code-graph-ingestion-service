/*

*/
package org.blr.api.dto;

import org.blr.domain.GraphBuildStatus;

public record GraphBuildAcceptedResponse(
    String buildId,
    String repositoryId,
    String commitSha,
    GraphBuildStatus status
) {
}
