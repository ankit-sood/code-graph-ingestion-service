CREATE TABLE IF NOT EXISTS repositories (
    repository_id VARCHAR(128) PRIMARY KEY,
    repository_name VARCHAR(255) NOT NULL,
    git_url TEXT NOT NULL,
    default_branch VARCHAR(128) NOT NULL DEFAULT 'main',
    active_commit_sha VARCHAR(64),
    active_graph_uri TEXT,
    graph_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    target_commit_sha VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_repositories_name UNIQUE (repository_name),
    CONSTRAINT uq_repositories_git_url UNIQUE (git_url),
    CONSTRAINT chk_repositories_graph_status CHECK (
        graph_status IN ('READY', 'BUILDING', 'DEGRADED', 'UNKNOWN')
    )
);

CREATE TABLE IF NOT EXISTS graph_builds (
    build_id VARCHAR(64) PRIMARY KEY,
    repository_id VARCHAR(128) NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    project_slug VARCHAR(255),
    blob_uri TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_graph_builds_repository FOREIGN KEY (repository_id)
        REFERENCES repositories(repository_id),
    CONSTRAINT chk_graph_builds_status CHECK (
        status IN ('QUEUED', 'BUILDING', 'VALIDATING', 'PACKAGING', 'UPLOADING', 'PUBLISHED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_graph_builds_repository_id_created_at
    ON graph_builds (repository_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_graph_builds_repository_id_commit_sha
    ON graph_builds (repository_id, commit_sha);

CREATE INDEX IF NOT EXISTS idx_graph_builds_status
    ON graph_builds (status);
