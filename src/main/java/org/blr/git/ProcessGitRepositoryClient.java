package org.blr.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.blr.config.GraphIngestionProperties;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProcessGitRepositoryClient implements GitRepositoryClient {

    private final GraphIngestionProperties properties;

    public ProcessGitRepositoryClient(GraphIngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public GitCheckoutResult checkout(GitCheckoutRequest request) {
        cloneRepository(request);

        String remoteMainRef = "refs/remotes/origin/" + request.branch();
        String resolvedCommitSha = resolveCommitSha(request, remoteMainRef);

        validateCommitOnMain(request, resolvedCommitSha, remoteMainRef);

        runGitChecked(request, List.of("-C", request.checkoutPath().toString(), "checkout", "--detach", resolvedCommitSha));
        String head = runGitChecked(request, List.of("-C", request.checkoutPath().toString(), "rev-parse", "HEAD")).trim();

        if (!head.equals(resolvedCommitSha)) {
            throw new AppException(
                ErrorCode.INVALID_COMMIT,
                "HEAD mismatch after checkout for repository " + request.repositoryId()
            );
        }

        return new GitCheckoutResult(resolvedCommitSha, head);
    }

    private void cloneRepository(GitCheckoutRequest request) {
        runGitChecked(
            request,
            List.of(
                "clone",
                "--no-checkout",
                "--branch",
                request.branch(),
                request.gitUrl(),
                request.checkoutPath().toString()
            )
        );
    }

    private String resolveCommitSha(GitCheckoutRequest request, String remoteMainRef) {
        if (request.requestedCommitSha() == null || request.requestedCommitSha().isBlank() || "LATEST_MAIN".equals(request.requestedCommitSha())) {
            return runGitChecked(
                request,
                List.of("-C", request.checkoutPath().toString(), "rev-parse", remoteMainRef)
            ).trim();
        }

        try {
            return runGitChecked(
                request,
                List.of("-C", request.checkoutPath().toString(), "rev-parse", "--verify", request.requestedCommitSha() + "^{commit}")
            ).trim();
        } catch (AppException ex) {
            if (ex.getErrorCode() == ErrorCode.INTERNAL_ERROR) {
                throw new AppException(
                    ErrorCode.INVALID_COMMIT,
                    "Invalid commit SHA for repository " + request.repositoryId() + ": " + request.requestedCommitSha()
                );
            }
            throw ex;
        }
    }

    private void validateCommitOnMain(GitCheckoutRequest request, String commitSha, String remoteMainRef) {
        GitCommandResult result = runGit(
            request,
            List.of("-C", request.checkoutPath().toString(), "merge-base", "--is-ancestor", commitSha, remoteMainRef)
        );
        if (result.exitCode == 0) {
            return;
        }
        if (result.exitCode == 1) {
            throw new AppException(
                ErrorCode.COMMIT_NOT_ON_MAIN,
                "Commit " + commitSha + " is not part of branch " + request.branch()
            );
        }
        throw new AppException(
            ErrorCode.INTERNAL_ERROR,
            "Failed to validate commit lineage. git exit code: " + result.exitCode + ". output: " + result.output
        );
    }

    private String runGitChecked(GitCheckoutRequest request, List<String> args) {
        GitCommandResult result = runGit(request, args);
        if (result.exitCode != 0) {
            throw new AppException(
                ErrorCode.INTERNAL_ERROR,
                "Git command failed with exit code " + result.exitCode + ": " + String.join(" ", args)
            );
        }
        return result.output;
    }

    private GitCommandResult runGit(GitCheckoutRequest request, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(properties.git().command());
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(properties.git().operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new AppException(
                    ErrorCode.INTERNAL_ERROR,
                    "Timed out running git command for repository " + request.repositoryId()
                );
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn(
                    "git_command_failed repositoryId={} command={} exitCode={} output={}",
                    request.repositoryId(),
                    String.join(" ", command),
                    exitCode,
                    output
                );
            }
            return new GitCommandResult(exitCode, output);
        } catch (IOException e) {
            throw new AppException(
                ErrorCode.INTERNAL_ERROR,
                "Failed to execute git command for repository " + request.repositoryId() + ": " + e.getMessage()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(
                ErrorCode.INTERNAL_ERROR,
                "Git command interrupted for repository " + request.repositoryId()
            );
        }
    }

    private record GitCommandResult(int exitCode, String output) {
    }
}
