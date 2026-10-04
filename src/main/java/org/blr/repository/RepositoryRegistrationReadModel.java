package org.blr.repository;

public interface RepositoryRegistrationReadModel {

    RegisteredRepository getById(String repositoryId);

    boolean exists(String repositoryId);
}
