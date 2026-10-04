package org.blr.codegraph;

import java.time.Duration;

public record NodeProcessResult(
    int exitCode,
    boolean timedOut,
    Duration elapsed,
    String stdout,
    String stderr,
    String commandLine
) {
}
