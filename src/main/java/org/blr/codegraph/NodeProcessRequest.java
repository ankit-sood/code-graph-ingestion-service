package org.blr.codegraph;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record NodeProcessRequest(
    String processLabel,
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    Duration timeout,
    Duration shutdownGracePeriod
) {
}
