package org.blr.codegraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.blr.config.GraphIngestionProperties;
import org.blr.config.sections.BuildProperties;
import org.blr.config.sections.CodeGraphProperties;
import org.blr.config.sections.GitProperties;
import org.blr.config.sections.LocalStorageProperties;
import org.blr.config.sections.RetentionProperties;
import org.blr.config.sections.StorageProperties;
import org.blr.config.sections.AzureStorageProperties;
import org.blr.config.sections.WorkspaceProperties;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessCodeGraphRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRunCodeGraphProcessSuccessfully() throws Exception {
        Path script = createExecutableScript("#!/bin/sh\necho started\nexit 0\n");
        ProcessCodeGraphRunner runner = new ProcessCodeGraphRunner(
            propertiesFor(script.toAbsolutePath().toString(), Duration.ofSeconds(2)),
            new NodeProcessManager()
        );

        CodeGraphRunResult result = runner.run(new CodeGraphRunRequest(
            "build-1",
            "repo-1",
            "sha-1",
            tempDir,
            tempDir.resolve("cg-home"),
            tempDir.resolve("artifact")
        ));

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.timedOut()).isFalse();
        assertThat(result.stdout()).contains("started");
        assertThat(result.generatedProjectPath()).isEqualTo(tempDir.resolve("cg-home"));
    }

    @Test
    void shouldMapNonZeroExitToCodeGraphExecutionFailed() throws Exception {
        Path script = createExecutableScript("#!/bin/sh\nexit 7\n");
        ProcessCodeGraphRunner runner = new ProcessCodeGraphRunner(
            propertiesFor(script.toAbsolutePath().toString(), Duration.ofSeconds(2)),
            new NodeProcessManager()
        );

        assertThatThrownBy(() -> runner.run(new CodeGraphRunRequest(
            "build-2",
            "repo-2",
            "sha-2",
            tempDir,
            tempDir.resolve("cg-home-2"),
            tempDir.resolve("artifact-2")
        )))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CODEGRAPH_EXECUTION_FAILED);
    }

    @Test
    void shouldMapTimeoutToCodeGraphTimeout() throws Exception {
        Path script = createExecutableScript("#!/bin/sh\nsleep 5\n");
        ProcessCodeGraphRunner runner = new ProcessCodeGraphRunner(
            propertiesFor(script.toAbsolutePath().toString(), Duration.ofMillis(100)),
            new NodeProcessManager()
        );

        assertThatThrownBy(() -> runner.run(new CodeGraphRunRequest(
            "build-3",
            "repo-3",
            "sha-3",
            tempDir,
            tempDir.resolve("cg-home-3"),
            tempDir.resolve("artifact-3")
        )))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CODEGRAPH_TIMEOUT);
    }

    private GraphIngestionProperties propertiesFor(String startupCommand, Duration buildTimeout) {
        return new GraphIngestionProperties(
            new WorkspaceProperties(tempDir.toAbsolutePath().toString()),
            new GitProperties("git", Duration.ofSeconds(60)),
            new CodeGraphProperties("/usr/bin/env", startupCommand, Duration.ofSeconds(10), buildTimeout, Duration.ofMillis(100)),
            new StorageProperties(
                "local",
                2,
                Duration.ofMillis(100),
                Duration.ofMillis(300),
                new LocalStorageProperties(tempDir.resolve("storage").toAbsolutePath().toString()),
                new AzureStorageProperties("", "container", "prefix")
            ),
            new RetentionProperties(5),
            new BuildProperties(2)
        );
    }

    private Path createExecutableScript(String contents) throws IOException {
        Path script = tempDir.resolve("runner-script-" + System.nanoTime() + ".sh");
        Files.writeString(script, contents, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }
}
