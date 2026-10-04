package org.blr.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

import org.blr.domain.RepositoryGraphStatus;

@Entity
@Table(name = "repositories")

@Getter
@Setter
public class RepositoryEntity {

    @Id
    @Column(name = "repository_id", nullable = false, length = 128)
    private String repositoryId;

    @Column(name = "repository_name", nullable = false, length = 255, unique = true)
    private String repositoryName;

    @Column(name = "git_url", nullable = false, unique = true)
    private String gitUrl;

    @Column(name = "default_branch", nullable = false, length = 128)
    private String defaultBranch = "main";

    @Column(name = "active_commit_sha", length = 64)
    private String activeCommitSha;

    @Column(name = "active_graph_uri")
    private String activeGraphUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "graph_status", nullable = false, length = 32)
    private RepositoryGraphStatus graphStatus = RepositoryGraphStatus.UNKNOWN;

    @Column(name = "target_commit_sha", length = 64)
    private String targetCommitSha;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public RepositoryEntity() {
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
