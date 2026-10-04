package org.blr.storage;

import org.blr.artifact.GraphArtifactResult;

public record GraphArtifactUploadRequest(
    String repositoryId,
    String commitSha,
    GraphArtifactResult artifact
) {
}
