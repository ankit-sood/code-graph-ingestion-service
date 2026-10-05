package org.blr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.blr.api.dto.GraphBuildAcceptedResponse;
import org.blr.api.dto.GraphBuildRequest;
import org.blr.api.dto.GraphBuildStatusResponse;
import org.blr.api.dto.GraphStatusResponse;
import org.blr.domain.GraphBuildStatus;
import org.blr.domain.RepositoryGraphStatus;
import org.blr.error.AppException;
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

@SpringBootTest
class GraphBuildApiIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private GraphBuildController graphBuildController;

    @Autowired
    private GraphStatusController graphStatusController;

    @Autowired
    private RepositoryJpaRepository repositoryJpaRepository;

    @Autowired
    private GraphBuildJpaRepository graphBuildJpaRepository;

    private String mainCommitSha;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {

        graphBuildJpaRepository.deleteAllInBatch();
        repositoryJpaRepository.deleteAllInBatch();

        Path localRepo = createLocalRepository(tempDir.resolve("repo-phase2"));

        RepositoryEntity repository = new RepositoryEntity();
        repository.setRepositoryId("repo-phase2");
        repository.setRepositoryName("repo-phase2-name");
        repository.setGitUrl(localRepo.toAbsolutePath().toString());
        repository.setDefaultBranch("main");
        repository.setGraphStatus(RepositoryGraphStatus.UNKNOWN);
        repositoryJpaRepository.saveAndFlush(repository);
    }

    @Test
    void shouldReturnAcceptedAndPublishBuildAsynchronously() throws Exception {
        ResponseEntity<GraphBuildAcceptedResponse> createResponse = graphBuildController.createBuild(
            "repo-phase2",
            new GraphBuildRequest(mainCommitSha)
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(202));
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().repositoryId()).isEqualTo("repo-phase2");
        assertThat(createResponse.getBody().commitSha()).isEqualTo(mainCommitSha);
        assertThat(createResponse.getBody().status()).isEqualTo(GraphBuildStatus.QUEUED);

        String buildId = createResponse.getBody().buildId();

        GraphBuildStatus terminalStatus = waitForTerminalStatus(buildId);
        assertThat(terminalStatus).isEqualTo(GraphBuildStatus.PUBLISHED);

        GraphBuildEntity persistedBuild = graphBuildJpaRepository.findById(buildId).orElseThrow();
        assertThat(persistedBuild.getProjectSlug()).isNotBlank();
        assertThat(persistedBuild.getBlobUri()).contains(".tar.gz");

        ResponseEntity<GraphBuildStatusResponse> buildStatusResponse = graphBuildController.getBuildStatus(
            "repo-phase2",
            buildId
        );
        assertThat(buildStatusResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(buildStatusResponse.getBody()).isNotNull();
        assertThat(buildStatusResponse.getBody().status()).isEqualTo(GraphBuildStatus.PUBLISHED);

        ResponseEntity<GraphStatusResponse> graphStatusResponse = graphStatusController.getGraphStatus("repo-phase2");
        assertThat(graphStatusResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(graphStatusResponse.getBody()).isNotNull();
        assertThat(graphStatusResponse.getBody().status()).isEqualTo(RepositoryGraphStatus.READY);
        assertThat(graphStatusResponse.getBody().activeCommitSha()).isEqualTo(mainCommitSha);
        assertThat(graphStatusResponse.getBody().graphUri()).contains(".tar.gz");
    }

    @Test
    void shouldReturnNotFoundForUnknownRepository() throws Exception {
        assertThatThrownBy(() -> graphBuildController.createBuild("unknown-repo", new GraphBuildRequest("abc123")))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.REPOSITORY_NOT_FOUND);
    }

    @Test
    void shouldDeduplicateDuplicateCommitRequestsForSameRepository() throws Exception {
        ResponseEntity<GraphBuildAcceptedResponse> firstResponse = graphBuildController.createBuild(
            "repo-phase2",
            new GraphBuildRequest(mainCommitSha)
        );

        ResponseEntity<GraphBuildAcceptedResponse> secondResponse = graphBuildController.createBuild(
            "repo-phase2",
            new GraphBuildRequest(mainCommitSha)
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(202));
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(202));
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().buildId()).isEqualTo(firstResponse.getBody().buildId());

        long sameCommitBuildCount = graphBuildJpaRepository
            .findByRepository_RepositoryIdOrderByCreatedAtDesc("repo-phase2")
            .stream()
            .filter(b -> mainCommitSha.equals(b.getCommitSha()))
            .count();
        assertThat(sameCommitBuildCount).isEqualTo(1L);
    }

    private GraphBuildStatus waitForTerminalStatus(String buildId) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            GraphBuildEntity entity = graphBuildJpaRepository.findById(buildId).orElse(null);
            if (entity != null && (entity.getStatus() == GraphBuildStatus.PUBLISHED || entity.getStatus() == GraphBuildStatus.FAILED)) {
                return entity.getStatus();
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Build did not reach terminal status in time: " + buildId);
    }

    private Path createLocalRepository(Path path) throws IOException, InterruptedException {
        Files.createDirectories(path);
        runGit(path, List.of("init", "-b", "main"));
        runGit(path, List.of("config", "user.name", "phase2-test"));
        runGit(path, List.of("config", "user.email", "phase2-test@example.org"));

        Files.writeString(path.resolve("README.md"), "phase2\n", StandardCharsets.UTF_8);
        runGit(path, List.of("add", "README.md"));
        runGit(path, List.of("commit", "-m", "initial"));

        Files.writeString(path.resolve("README.md"), "phase2-main\n", StandardCharsets.UTF_8);
        runGit(path, List.of("add", "README.md"));
        runGit(path, List.of("commit", "-m", "main-update"));

        this.mainCommitSha = runGit(path, List.of("rev-parse", "HEAD")).trim();
        return path;
    }

    private String runGit(Path cwd, List<String> args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
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
