package org.blr.artifact;

import java.nio.file.Path;

public record GraphArtifactRequest(
    String buildId,
    String repositoryId,
    String commitSha,
    Path artifactDirectory
) {
}
