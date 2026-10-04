package org.blr.codegraph;

import java.nio.file.Path;

public record CodeGraphRunRequest(
    String buildId,
    String repositoryId,
    String commitSha,
    Path sourcePath,
    Path codegraphHomePath,
    Path artifactPath
) {
}
