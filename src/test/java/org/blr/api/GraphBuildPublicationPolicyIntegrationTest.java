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
import org.blr.persistence.entity.GraphBuildEntity;
import org.blr.persistence.entity.RepositoryEntity;
import org.blr.persistence.repository.GraphBuildJpaRepository;
import org.blr.persistence.repository.GitRepositoryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "graph-ingestion.codegraph.node-command=/bin/sh",
    "graph-ingestion.codegraph.startup-command=${user.dir}/src/test/resources/codegraph-delay.sh",
    "graph-ingestion.codegraph.build-timeout=30s",
    "graph-ingestion.codegraph.shutdown-grace-period=1s",
    "graph-ingestion.build.max-concurrent-builds=2",
    "graph-ingestion.retention.versions=5",
    "graph-ingestion.storage.provider=local",
    "graph-ingestion.storage.local.root=target/test-blob-storage-phase7"
})
class GraphBuildPublicationPolicyIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private GraphBuildController graphBuildController;

    @Autowired
    private GitRepositoryJpaRepository gitRepositoryJpaRepository;

    @Autowired
    private GraphBuildJpaRepository graphBuildJpaRepository;

    private String olderCommitSha;
    private String newerCommitSha;

    @BeforeEach
    void setUp() throws Exception {

        graphBuildJpaRepository.deleteAllInBatch();
        gitRepositoryJpaRepository.deleteAllInBatch();

        Path localRepo = createRepositoryWithSlowAndFastCommits(tempDir.resolve("repo-phase7"));

        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-phase7");
        repository.setRepositoryName("repo-phase7-name");
        repository.setGitUrl(localRepo.toAbsolutePath().toString());
        repository.setDefaultBranch("main");
        repository.setGraphStatus(RepositoryGraphStatus.UNKNOWN);
        gitRepositoryJpaRepository.saveAndFlush(repository);
    }

    @Test
    void shouldKeepLatestSuccessfulCommitAsActiveWhenOlderFinishesLater() throws Exception {
        GraphBuildAcceptedResponse olderBuildResponse = graphBuildController.createBuild(
            "repo-phase7",
            new GraphBuildRequest(olderCommitSha)
        ).getBody();

        GraphBuildAcceptedResponse newerBuildResponse = graphBuildController.createBuild(
            "repo-phase7",
            new GraphBuildRequest(newerCommitSha)
        ).getBody();

        assertThat(olderBuildResponse).isNotNull();
        assertThat(newerBuildResponse).isNotNull();

        GraphBuildEntity olderBuild = waitForTerminalBuild(olderBuildResponse.buildId());
        GraphBuildEntity newerBuild = waitForTerminalBuild(newerBuildResponse.buildId());

        assertThat(olderBuild.getStatus()).isEqualTo(GraphBuildStatus.PUBLISHED);
        assertThat(newerBuild.getStatus()).isEqualTo(GraphBuildStatus.PUBLISHED);

        RepositoryEntity repository = gitRepositoryJpaRepository.findById("repo-phase7").orElseThrow();
        assertThat(repository.getGraphStatus()).isEqualTo(RepositoryGraphStatus.READY);
        assertThat(repository.getActiveCommitSha()).isEqualTo(newerCommitSha);
        assertThat(repository.getActiveGraphUri()).contains(newerCommitSha);
    }

    private GraphBuildEntity waitForTerminalBuild(String buildId) throws InterruptedException {
        for (int i = 0; i < 120; i++) {
            GraphBuildEntity entity = graphBuildJpaRepository.findById(buildId).orElse(null);
            if (entity != null && (entity.getStatus() == GraphBuildStatus.PUBLISHED || entity.getStatus() == GraphBuildStatus.FAILED)) {
                return entity;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Build did not reach terminal status in time: " + buildId);
    }

    private Path createRepositoryWithSlowAndFastCommits(Path path) throws IOException, InterruptedException {
        Files.createDirectories(path);
        runGit(path, List.of("init", "-b", "main"));
        runGit(path, List.of("config", "user.name", "phase7-test"));
        runGit(path, List.of("config", "user.email", "phase7-test@example.org"));

        Files.writeString(path.resolve("README.md"), "phase7\n", StandardCharsets.UTF_8);
        Files.writeString(path.resolve("slow.marker"), "slow\n", StandardCharsets.UTF_8);
        runGit(path, List.of("add", "README.md", "slow.marker"));
        runGit(path, List.of("commit", "-m", "slow-commit"));
        olderCommitSha = runGit(path, List.of("rev-parse", "HEAD")).trim();

        Files.delete(path.resolve("slow.marker"));
        Files.writeString(path.resolve("README.md"), "phase7-fast\n", StandardCharsets.UTF_8);
        runGit(path, List.of("add", "-A"));
        runGit(path, List.of("commit", "-m", "fast-commit"));
        newerCommitSha = runGit(path, List.of("rev-parse", "HEAD")).trim();

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
