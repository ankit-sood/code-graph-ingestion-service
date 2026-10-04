package org.blr.codegraph;

import java.nio.file.Path;
import java.time.Duration;

public record CodeGraphRunResult(
    int exitCode,
    boolean timedOut,
    Duration elapsed,
    Path generatedProjectPath,
    String stdout,
    String stderr,
    String commandLine
) {
}
