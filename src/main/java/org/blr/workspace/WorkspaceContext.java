package org.blr.workspace;

import java.nio.file.Path;

public record WorkspaceContext(
    Path root,
    Path source,
    Path codegraphHome,
    Path artifact
) {
}
