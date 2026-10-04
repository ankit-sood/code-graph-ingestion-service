package org.blr.git;

public record GitCheckoutResult(
    String resolvedCommitSha,
    String headCommitSha
) {
}
