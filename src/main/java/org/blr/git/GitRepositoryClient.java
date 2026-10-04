package org.blr.git;

public interface GitRepositoryClient {

    GitCheckoutResult checkout(GitCheckoutRequest request);
}
