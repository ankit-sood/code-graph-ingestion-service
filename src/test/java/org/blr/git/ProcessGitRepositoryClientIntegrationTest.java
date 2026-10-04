package org.blr.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProcessGitRepositoryClientIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private GitRepositoryClient gitRepositoryClient;

    private Path repositoryPath;
    private String mainHeadSha;
    private String featureHeadSha;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        repositoryPath = tempDir.resolve("remote-repo");
        Files.createDirectories(repositoryPath);

        runGit(repositoryPath, List.of("init", "-b", "main"));
        runGit(repositoryPath, List.of("config", "user.name", "phase3-test"));
        runGit(repositoryPath, List.of("config", "user.email", "phase3-test@example.org"));

        Files.writeString(repositoryPath.resolve("file.txt"), "main-1\n", StandardCharsets.UTF_8);
        runGit(repositoryPath, List.of("add", "file.txt"));
        runGit(repositoryPath, List.of("commit", "-m", "main-1"));

        Files.writeString(repositoryPath.resolve("file.txt"), "main-2\n", StandardCharsets.UTF_8);
        runGit(repositoryPath, List.of("add", "file.txt"));
        runGit(repositoryPath, List.of("commit", "-m", "main-2"));
        mainHeadSha = runGit(repositoryPath, List.of("rev-parse", "HEAD")).trim();

        runGit(repositoryPath, List.of("checkout", "-b", "feature"));
        Files.writeString(repositoryPath.resolve("feature.txt"), "feature\n", StandardCharsets.UTF_8);
        runGit(repositoryPath, List.of("add", "feature.txt"));
        runGit(repositoryPath, List.of("commit", "-m", "feature-1"));
        featureHeadSha = runGit(repositoryPath, List.of("rev-parse", "HEAD")).trim();

        runGit(repositoryPath, List.of("checkout", "main"));
    }

    @Test
    void shouldResolveLatestMainWhenCommitNotProvided() {
        GitCheckoutResult result = gitRepositoryClient.checkout(new GitCheckoutRequest(
            "repo-phase3",
            repositoryPath.toAbsolutePath().toString(),
            "main",
            null,
            tempDir.resolve("checkout-latest")
        ));

        assertThat(result.resolvedCommitSha()).isEqualTo(mainHeadSha);
        assertThat(result.headCommitSha()).isEqualTo(mainHeadSha);
    }

    @Test
    void shouldCheckoutExplicitMainCommit() {
        GitCheckoutResult result = gitRepositoryClient.checkout(new GitCheckoutRequest(
            "repo-phase3",
            repositoryPath.toAbsolutePath().toString(),
            "main",
            mainHeadSha,
            tempDir.resolve("checkout-explicit")
        ));

        assertThat(result.resolvedCommitSha()).isEqualTo(mainHeadSha);
        assertThat(result.headCommitSha()).isEqualTo(mainHeadSha);
    }

    @Test
    void shouldRejectInvalidCommitSha() {
        assertThatThrownBy(() -> gitRepositoryClient.checkout(new GitCheckoutRequest(
            "repo-phase3",
            repositoryPath.toAbsolutePath().toString(),
            "main",
            "deadbeef",
            tempDir.resolve("checkout-invalid")
        )))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_COMMIT);
    }

    @Test
    void shouldRejectCommitNotOnMainLineage() {
        assertThatThrownBy(() -> gitRepositoryClient.checkout(new GitCheckoutRequest(
            "repo-phase3",
            repositoryPath.toAbsolutePath().toString(),
            "main",
            featureHeadSha,
            tempDir.resolve("checkout-feature")
        )))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.COMMIT_NOT_ON_MAIN);
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
            throw new IllegalStateException("git setup command failed: " + String.join(" ", command) + " output: " + output);
        }
        return output;
    }
}
