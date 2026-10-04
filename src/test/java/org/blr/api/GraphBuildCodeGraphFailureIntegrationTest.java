package org.blr.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.blr.api.dto.GraphBuildAcceptedResponse;
import org.blr.api.dto.GraphBuildRequest;
import org.blr.domain.GraphBuildStatus;
import org.blr.domain.RepositoryGraphStatus;
import org.blr.error.ErrorCode;
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import org.blr.persistence.repository.RepositoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

@SpringBootTest(properties = {
    "graph-ingestion.codegraph.node-command=/usr/bin/env",
    "graph-ingestion.codegraph.startup-command=false",
    "graph-ingestion.codegraph.build-timeout=30s",
    "graph-ingestion.codegraph.shutdown-grace-period=1s"
})
class GraphBuildCodeGraphFailureIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private GraphBuildController graphBuildController;

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @Autowired
    private GraphBuildJpaRepository graphBuildJpaRepository;

    private String mainCommitSha;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        graphBuildJpaRepository.deleteAll();
        repositoryJpaRepository.deleteAll();

        Path localRepo = createLocalRepository(tempDir.resolve("repo-phase4"));

        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-phase4");
        repository.setRepositoryName("repo-phase4-name");
        repository.setGitUrl(localRepo.toAbsolutePath().toString());
        repository.setDefaultBranch("main");
        repository.setGraphStatus(RepositoryGraphStatus.UNKNOWN);
        repositoryJpaRepository.saveAndFlush(repository);
    }

    @Test
    void shouldMarkBuildFailedWhenCodeGraphProcessFails() throws Exception {
        ResponseEntity<GraphBuildAcceptedResponse> createResponse = graphBuildController.createBuild(
            "repo-phase4",
            new GraphBuildRequest(mainCommitSha)
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(202));
        GraphBuildEntity build = waitForTerminalBuild(createResponse.getBody().buildId());

        assertThat(build.getStatus()).isEqualTo(GraphBuildStatus.FAILED);
        assertThat(build.getErrorCode()).isEqualTo(ErrorCode.CODEGRAPH_EXECUTION_FAILED);

        RepositoryEntity repository = repositoryJpaRepository.findById("repo-phase4").orElseThrow();
        assertThat(repository.getGraphStatus()).isEqualTo(RepositoryGraphStatus.DEGRADED);
    }

    private GraphBuildEntity waitForTerminalBuild(String buildId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            GraphBuildEntity entity = graphBuildJpaRepository.findById(buildId).orElse(null);
            if (entity != null && (entity.getStatus() == GraphBuildStatus.PUBLISHED || entity.getStatus() == GraphBuildStatus.FAILED)) {
                return entity;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Build did not reach terminal status in time: " + buildId);
    }

    private Path createLocalRepository(Path path) throws IOException, InterruptedException {
        Files.createDirectories(path);
        runGit(path, List.of("init", "-b", "main"));
        runGit(path, List.of("config", "user.name", "phase4-test"));
        runGit(path, List.of("config", "user.email", "phase4-test@example.org"));

        Files.writeString(path.resolve("README.md"), "phase4\n", StandardCharsets.UTF_8);
        runGit(path, List.of("add", "README.md"));
        runGit(path, List.of("commit", "-m", "initial"));

        this.mainCommitSha = runGit(path, List.of("rev-parse", "HEAD")).trim();
        return path;
    }

    private String runGit(Path cwd, List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(cwd.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException("git command failed: " + String.join(" ", command) + " output: " + output);
        }
        return output;
    }
}
