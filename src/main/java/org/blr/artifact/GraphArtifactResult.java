package org.blr.artifact;

import java.nio.file.Path;

public record GraphArtifactResult(
    Path archivePath,
    Path manifestPath,
    String projectSlug,
    String archiveSha256,
    long archiveSizeBytes,
    int archiveEntryCount
) {
}
