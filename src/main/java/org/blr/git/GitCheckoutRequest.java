package org.blr.git;

import java.nio.file.Path;

public record GitCheckoutRequest(
    String repositoryId,
    String gitUrl,
    String branch,
    String requestedCommitSha,
    Path checkoutPath
) {
}
