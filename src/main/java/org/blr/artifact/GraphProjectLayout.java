package org.blr.artifact;

import java.nio.file.Path;

public record GraphProjectLayout(
    Path projectDirectory,
    String projectSlug,
    long fileCount
) {
}
