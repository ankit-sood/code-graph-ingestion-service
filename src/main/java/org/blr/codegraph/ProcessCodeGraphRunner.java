package org.blr.codegraph;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.blr.config.GraphIngestionProperties;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ProcessCodeGraphRunner implements CodeGraphRunner {

    private final GraphIngestionProperties properties;
    private final NodeProcessManager nodeProcessManager;

    public ProcessCodeGraphRunner(
        GraphIngestionProperties properties,
        NodeProcessManager nodeProcessManager
    ) {
        this.properties = properties;
        this.nodeProcessManager = nodeProcessManager;
    }

    @Override
    public CodeGraphRunResult run(CodeGraphRunRequest request) {
        Map<String, String> env = new HashMap<>();
        env.put("CODEGRAPH_SOURCE_DIR", request.sourcePath().toAbsolutePath().toString());
        env.put("CODEGRAPH_HOME_DIR", request.codegraphHomePath().toAbsolutePath().toString());
        env.put("CODEGRAPH_ARTIFACT_DIR", request.artifactPath().toAbsolutePath().toString());
        env.put("CODEGRAPH_BUILD_ID", request.buildId());
        env.put("CODEGRAPH_REPOSITORY_ID", request.repositoryId());
        env.put("CODEGRAPH_COMMIT_SHA", request.commitSha());

        NodeProcessResult processResult = nodeProcessManager.run(new NodeProcessRequest(
            "codegraph-build",
            List.of(properties.codegraph().nodeCommand(), properties.codegraph().startupCommand()),
            request.sourcePath(),
            env,
            properties.codegraph().buildTimeout(),
            properties.codegraph().shutdownGracePeriod()
        ));

        if (processResult.timedOut()) {
            throw new AppException(
                ErrorCode.CODEGRAPH_TIMEOUT,
                "CodeGraph process timed out after " + properties.codegraph().buildTimeout().toSeconds() + "s"
            );
        }

        if (processResult.exitCode() != 0) {
            throw new AppException(
                ErrorCode.CODEGRAPH_EXECUTION_FAILED,
                "CodeGraph process failed with exit code " + processResult.exitCode()
            );
        }

        Path generatedProjectPath = request.codegraphHomePath();
        return new CodeGraphRunResult(
            processResult.exitCode(),
            processResult.timedOut(),
            processResult.elapsed(),
            generatedProjectPath,
            processResult.stdout(),
            processResult.stderr(),
            processResult.commandLine()
        );
    }
}
