package org.blr.codegraph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodeProcessManagerTest {

    @TempDir
    Path tempDir;

    private final NodeProcessManager nodeProcessManager = new NodeProcessManager();

    @Test
    void shouldCaptureStdoutAndStderrAndExitCode() throws Exception {
        Path script = createExecutableScript("#!/bin/sh\necho ok\necho warn 1>&2\nexit 0\n");

        NodeProcessResult result = nodeProcessManager.run(new NodeProcessRequest(
            "node-process-test",
            java.util.List.of("/usr/bin/env", script.toAbsolutePath().toString()),
            tempDir,
            Map.of("TEST_VALUE", "x"),
            Duration.ofSeconds(2),
            Duration.ofMillis(200)
        ));

        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("ok");
        assertThat(result.stderr()).contains("warn");
    }

    @Test
    void shouldMarkTimeoutAndStopProcess() throws Exception {
        Path script = createExecutableScript("#!/bin/sh\nsleep 5\n");

        NodeProcessResult result = nodeProcessManager.run(new NodeProcessRequest(
            "node-process-timeout",
            java.util.List.of("/usr/bin/env", script.toAbsolutePath().toString()),
            tempDir,
            Map.of(),
            Duration.ofMillis(100),
            Duration.ofMillis(100)
        ));

        assertThat(result.timedOut()).isTrue();
    }

    private Path createExecutableScript(String contents) throws IOException {
        Path script = tempDir.resolve("script-" + System.nanoTime() + ".sh");
        Files.writeString(script, contents, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }
}
