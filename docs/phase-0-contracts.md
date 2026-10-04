# Phase 0 Contracts

## API Contracts

### Create Graph Build
- Method: POST
- Path: /api/v1/repositories/{repositoryId}/graph-builds
- Request body:
  - commitSha (optional)
- Response: 202 Accepted
- Response body:
  - buildId
  - repositoryId
  - commitSha
  - status

### Graph Build Status
- Method: GET
- Path: /api/v1/repositories/{repositoryId}/graph-builds/{buildId}
- Response: 200 OK
- Response body:
  - buildId
  - repositoryId
  - commitSha
  - status
  - startedAt
  - completedAt

### Active Graph Status
- Method: GET
- Path: /api/v1/repositories/{repositoryId}/graph-status
- Response: 200 OK
- Response body:
  - repositoryId
  - activeCommitSha
  - status
  - graphUri
  - targetCommitSha

## Build Lifecycle State Machine
- QUEUED -> BUILDING
- BUILDING -> VALIDATING
- VALIDATING -> PACKAGING
- PACKAGING -> UPLOADING
- UPLOADING -> PUBLISHED
- Any non-terminal stage -> FAILED

## Error Taxonomy
- REPOSITORY_NOT_FOUND
- INVALID_COMMIT
- COMMIT_NOT_ON_MAIN
- GRAPH_BUILD_ALREADY_RUNNING
- NODE_PROCESS_START_FAILED
- CODEGRAPH_EXECUTION_FAILED
- CODEGRAPH_TIMEOUT
- GRAPH_VALIDATION_FAILED
- GRAPH_ARTIFACT_PACKAGING_FAILED
- GRAPH_ARTIFACT_UPLOAD_FAILED
- GRAPH_PUBLICATION_FAILED
- INVALID_REQUEST
- INTERNAL_ERROR

## Error To HTTP Mapping
- REPOSITORY_NOT_FOUND -> 404 Not Found
- INVALID_COMMIT -> 400 Bad Request
- COMMIT_NOT_ON_MAIN -> 422 Unprocessable Entity
- GRAPH_BUILD_ALREADY_RUNNING -> 409 Conflict
- NODE_PROCESS_START_FAILED -> 502 Bad Gateway
- CODEGRAPH_EXECUTION_FAILED -> 500 Internal Server Error
- CODEGRAPH_TIMEOUT -> 504 Gateway Timeout
- GRAPH_VALIDATION_FAILED -> 422 Unprocessable Entity
- GRAPH_ARTIFACT_PACKAGING_FAILED -> 500 Internal Server Error
- GRAPH_ARTIFACT_UPLOAD_FAILED -> 502 Bad Gateway
- GRAPH_PUBLICATION_FAILED -> 500 Internal Server Error
- INVALID_REQUEST -> 400 Bad Request
- INTERNAL_ERROR -> 500 Internal Server Error

## Configuration Keys and Defaults
- graph-ingestion.workspace.root: /workspace
- graph-ingestion.codegraph.node-command: node
- graph-ingestion.codegraph.startup-command: codegraph-daemon
- graph-ingestion.codegraph.startup-timeout: 60s
- graph-ingestion.codegraph.build-timeout: 30m
- graph-ingestion.codegraph.shutdown-grace-period: 10s
- graph-ingestion.retention.versions: 5
- graph-ingestion.build.max-concurrent-builds: 2

## Notes
- These endpoints are contract stubs in Phase 0.
- Persistence-backed behavior starts in Phase 1 and Phase 2.
