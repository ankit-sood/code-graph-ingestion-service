package org.blr.codegraph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class NodeProcessManager {

    public NodeProcessResult run(NodeProcessRequest request) {
        List<String> command = new ArrayList<>(request.command());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(request.workingDirectory().toFile());

        Map<String, String> env = processBuilder.environment();
        env.putAll(request.environment());

        Instant started = Instant.now();
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new AppException(
                ErrorCode.NODE_PROCESS_START_FAILED,
                "Failed to start process " + request.processLabel() + ": " + ex.getMessage()
            );
        }

        String stdout;
        String stderr;
        boolean timedOut = false;
        int exitCode;
        try {
            boolean finished = process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                timedOut = true;
                process.destroy();
                boolean stopped = process.waitFor(request.shutdownGracePeriod().toMillis(), TimeUnit.MILLISECONDS);
                if (!stopped) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            }

            stdout = readStreamSafely(process.getInputStream(), timedOut);
            stderr = readStreamSafely(process.getErrorStream(), timedOut);
            exitCode = process.exitValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new AppException(
                ErrorCode.INTERNAL_ERROR,
                "Interrupted while waiting for process " + request.processLabel()
            );
        }

        Duration elapsed = Duration.between(started, Instant.now());
        return new NodeProcessResult(
            exitCode,
            timedOut,
            elapsed,
            stdout,
            stderr,
            String.join(" ", command)
        );
    }

    private String readStreamSafely(java.io.InputStream stream, boolean timedOut) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            if (timedOut) {
                return "";
            }
            throw new AppException(
                ErrorCode.INTERNAL_ERROR,
                "Failed to read process output: " + ex.getMessage()
            );
        }
    }
}
