# Graph Ingestion Service — GitHub Copilot / GPT-5.3 Codex Prompt

## Role

Act as a senior backend engineer experienced with Java, Spring Boot, PostgreSQL, Azure, Git, Kubernetes, Node.js process management, distributed systems, and production-grade build pipelines.

You are implementing a **Graph Ingestion Service** for a centralized Code Intelligence Graph Platform.

The service is a **Java/Spring Boot application running in Kubernetes**. It receives graph-build requests, checks out an exact Git commit, starts the required Node.js/CodeGraph runtime, generates the CodeGraph representation, packages the resulting CodeGraph project state, stores it in Azure Blob Storage, and publishes the graph version through a PostgreSQL control plane.

The service is **not** responsible for serving MCP queries. MCP serving will be implemented separately.

---

# 1. Objective

Build a production-ready Graph Ingestion Service that:

1. Receives graph-build requests through a REST API.
2. Resolves and validates the requested Git commit.
3. Ensures the commit belongs to the repository's `main` branch lineage.
4. Checks out the exact commit into an isolated ephemeral Kubernetes workspace.
5. Starts the required **Node.js process from Java** to run the CodeGraph server.
6. Runs CodeGraph against the checked-out workspace.
7. Validates that CodeGraph completed successfully.
8. Identifies the CodeGraph project directory generated for the repository.
9. Packages the **entire CodeGraph project directory** into a compressed archive.
10. Creates a manifest describing the graph artifact.
11. Uploads the immutable archive and manifest to Azure Blob Storage.
12. Persists build state and metadata in PostgreSQL.
13. Atomically publishes the new graph version only after successful validation and upload.
14. Preserves the currently active graph if anything fails.
15. Supports idempotent requests.
16. Handles concurrent builds safely.
17. Retains the latest configured number of successful graph versions, defaulting to 5.
18. Provides REST APIs for build status and active graph status.

The most important system invariant is:

> The active graph must always point to a completely built, validated, immutable CodeGraph artifact corresponding to a specific Git commit.

A failed, incomplete, corrupt, or partially uploaded graph must never become the active graph.

---

# 2. Runtime Environment

The Graph Ingestion Service runs inside Kubernetes.

It is **not intended to run from a developer's local environment**.

A build must use an isolated pod-local workspace.

Conceptually:

```text
/workspace/{buildId}/
├── source/
│   └── repository checked out at exact commit
│
├── codegraph-home/
│   └── CodeGraph runtime/persisted state
│
└── artifact/
    └── packaged graph artifact
```

The workspace is ephemeral.

Do not require a persistent volume for build workspaces.

Durable state is stored in:

```text
PostgreSQL       → control plane
Azure Blob       → graph artifacts
```

The pod-local workspace is disposable.

---

# 3. Java → Node.js → CodeGraph Runtime

This is an explicit architectural requirement.

The Java Graph Ingestion Service must **start the Node.js process from Java** in order to use CodeGraph.

The Java service should not directly depend on or invoke the native CodeGraph binary unless inspection of the actual CodeGraph distribution proves that this is the supported runtime path.

The expected process relationship is:

```text
┌──────────────────────────────────────┐
│ Java / Spring Boot                   │
│                                      │
│ Graph Ingestion Service              │
│                                      │
│  ProcessCodeGraphRunner              │
│          │                           │
│          │ ProcessBuilder             │
│          ▼                           │
│      Node.js process                 │
│          │                           │
│          │ starts CodeGraph           │
│          ▼                           │
│    CodeGraph server/runtime          │
└──────────────────────────────────────┘
```

The Kubernetes container therefore needs the required:

- Java runtime
- Node.js runtime
- CodeGraph Node.js package/runtime
- Git tooling

Do not assume these dependencies are available in the base image.

The Dockerfile/container image must explicitly provide the required runtime dependencies.

---

# 4. Node.js Process Lifecycle

Java owns the lifecycle of the Node.js/CodeGraph process.

The implementation must:

1. Start the Node.js process from Java.
2. Pass the required CodeGraph configuration.
3. Pass the build workspace.
4. Pass the isolated CodeGraph HOME/state directory.
5. Capture stdout and stderr.
6. Monitor process health/readiness as appropriate.
7. Detect process termination.
8. Enforce a configurable timeout.
9. Gracefully terminate the process when the build finishes.
10. Forcefully terminate it if graceful shutdown exceeds the configured grace period.
11. Ensure child processes do not remain running after the build completes.
12. Clean up the ephemeral workspace.

Do not leave orphaned Node.js or CodeGraph processes in the Kubernetes pod.

---

# 5. CodeGraph Integration Abstraction

Create an abstraction so the rest of the application does not depend directly on process-management details.

For example:

```java
public interface CodeGraphRunner {

    CodeGraphResult build(CodeGraphRequest request);
}
```

Possible implementation:

```text
ProcessCodeGraphRunner
```

Responsibilities include:

1. Prepare CodeGraph environment.
2. Start Node.js from Java.
3. Start/use the CodeGraph server through the Node.js runtime.
4. Configure the CodeGraph workspace.
5. Configure the CodeGraph HOME/state directory.
6. Wait until CodeGraph is ready for the required operation.
7. Execute the required graph-building/indexing operation.
8. Capture stdout/stderr.
9. Enforce timeouts.
10. Detect failures.
11. Shut down the Node.js/CodeGraph process.
12. Return structured information about generated CodeGraph state.

Do not scatter `ProcessBuilder` calls throughout the application.

All external process management should be centralized behind this abstraction.

---

# 6. Inspect the Actual CodeGraph Installation

Before implementing `ProcessCodeGraphRunner`, inspect the actual CodeGraph distribution/version being used.

Do not invent:

- npm package names
- executable names
- CLI arguments
- server modes
- MCP arguments
- Node.js entry points
- environment variables
- startup/readiness behavior

Determine from the actual installed/versioned CodeGraph implementation:

1. Which Node.js package must be installed.
2. Which Node.js command starts CodeGraph.
3. How the Node.js process starts the CodeGraph server.
4. How the workspace is supplied.
5. How the CodeGraph HOME/state directory is supplied.
6. Whether the server is long-lived or one-shot for the ingestion operation.
7. How Java should communicate with the process.
8. How to detect successful initialization.
9. How to request indexing/building.
10. How to shut down cleanly.

If CodeGraph exposes a supported Node.js wrapper/CLI, use that mechanism.

Do not bypass the supported Node.js runtime merely because a native binary happens to exist inside the package.

---

# 7. Node.js and CodeGraph Configuration

The CodeGraph runtime must be isolated per build where required.

Conceptually:

```text
/workspace/{buildId}/
    source/
    codegraph-home/
```

The CodeGraph process should operate using the build-specific state location.

This prevents:

```text
build-A
    ↓
CodeGraph state
    ↓
build-B
```

from accidentally sharing mutable state.

Concurrent graph builds must not share mutable CodeGraph project directories.

---

# 8. First: Inspect the Existing Repository

Before writing code:

1. Inspect the entire repository.
2. Identify:
   - Java version
   - Spring Boot version
   - Maven/Gradle
   - package structure
   - existing REST conventions
   - configuration conventions
   - database migration framework
   - testing conventions
   - logging conventions
   - exception handling
   - Docker/container setup
   - Kubernetes deployment conventions
3. Inspect existing PostgreSQL migrations and schemas.
4. Determine whether Testcontainers is already used.
5. Identify existing Azure SDK dependencies or conventions.
6. Determine how the project handles asynchronous/background work.
7. Inspect the actual CodeGraph version and integration available in this environment.

Before implementing the process runner, inspect the Node.js/CodeGraph runtime and document the exact invocation.

---

# 9. Build Lifecycle

The complete build flow is:

```text
REST / GitHub webhook
        │
        ▼
Resolve target commit
        │
        ▼
Create build record
        │
        ▼
Create isolated Kubernetes workspace
        │
        ▼
Checkout exact Git commit
        │
        ▼
Verify HEAD == requested SHA
        │
        ▼
Start Node.js from Java
        │
        ▼
Node.js starts CodeGraph
        │
        ▼
CodeGraph indexes checked-out source
        │
        ▼
Validate generated CodeGraph state
        │
        ▼
Package complete CodeGraph project directory
        │
        ▼
Create manifest
        │
        ▼
Upload artifact + manifest to Azure Blob
        │
        ▼
Atomically publish through PostgreSQL
        │
        ▼
Cleanup Node.js/CodeGraph process
        │
        ▼
Cleanup ephemeral workspace
```

The Git checkout occurs **before CodeGraph is started**.

---

# 10. Git Checkout

For every graph build:

1. Resolve the repository from the registered repository configuration.
2. Clone/fetch as appropriate.
3. Checkout the exact requested commit.
4. Verify:

```bash
git rev-parse HEAD
```

matches the requested SHA.

5. Verify the commit belongs to `main`.

The checkout must occur in:

```text
/workspace/{buildId}/source
```

Do not use a developer's local repository.

Do not use a shared mutable checkout.

Do not allow concurrent builds to modify the same Git workspace.

The generated graph must correspond exactly to the commit recorded in PostgreSQL and the artifact manifest.

---

# 11. REST API

Implement:

```http
POST /api/v1/repositories/{repositoryId}/graph-builds
```

Example:

```json
{
  "commitSha": "abc123..."
}
```

`commitSha` is optional.

If omitted:

- resolve the latest commit on `main`.

If supplied:

- verify the commit exists;
- verify the commit belongs to the registered repository;
- verify that it is part of the `main` branch lineage.

The service must not allow arbitrary repositories or arbitrary Git URLs.

Repositories must come from the registered repository configuration/control plane.

---

# 12. REST Response

The build operation should be asynchronous.

Return HTTP `202 Accepted`.

Example:

```json
{
  "buildId": "01J...",
  "repositoryId": "uds-shard-resolver",
  "commitSha": "abc123...",
  "status": "QUEUED"
}
```

The request should not remain open while CodeGraph is building a large repository.

---

# 13. Build Status API

Implement:

```http
GET /api/v1/repositories/{repositoryId}/graph-builds/{buildId}
```

Example:

```json
{
  "buildId": "01J...",
  "repositoryId": "uds-shard-resolver",
  "commitSha": "abc123...",
  "status": "BUILDING",
  "startedAt": "...",
  "completedAt": null
}
```

---

# 14. Active Graph Status API

Implement:

```http
GET /api/v1/repositories/{repositoryId}/graph-status
```

Example:

```json
{
  "repositoryId": "uds-shard-resolver",
  "activeCommitSha": "abc123...",
  "status": "READY",
  "graphUri": "codegraph/repositories/uds-shard-resolver/versions/abc123.../codegraph.tar.gz",
  "targetCommitSha": null
}
```

If a refresh is occurring:

```json
{
  "repositoryId": "uds-shard-resolver",
  "activeCommitSha": "abc123...",
  "status": "BUILDING",
  "targetCommitSha": "def456..."
}
```

The active graph remains `abc123...` until `def456...` is successfully published.

---

# 15. PostgreSQL Control Plane

PostgreSQL is the authoritative control plane.

Do not use Azure Blob Storage as the source of truth for active graph selection.

At minimum, model:

## Repository

```text
repository_id
repository_name
git_url
default_branch
active_commit_sha
active_graph_uri
graph_status
target_commit_sha
created_at
updated_at
```

## Graph Build

```text
build_id
repository_id
commit_sha
status
project_slug
blob_uri
started_at
completed_at
error_code
error_message
created_at
updated_at
```

Use appropriate primary keys, foreign keys, indexes, unique constraints, and concurrency controls.

---

# 16. Build Statuses

Use explicit lifecycle states:

```text
QUEUED
BUILDING
VALIDATING
PACKAGING
UPLOADING
PUBLISHED
FAILED
```

The exact state model may be refined during implementation if the existing repository has conventions that are better.

---

# 17. Graph Identity

A graph version is uniquely identified by:

```text
repositoryId + commitSha
```

The graph artifact is immutable once published.

Never overwrite an existing artifact for the same commit.

---

# 18. Idempotency

Requests for the same:

```text
repositoryId + commitSha
```

must be idempotent.

If already active:

- return existing active state.

If successfully built:

- reuse the existing artifact.

If currently building:

- return the existing build ID.

If previously failed:

- allow retry according to policy.

Do not create duplicate successful artifacts for the same immutable commit.

---

# 19. Concurrent Builds

Safely handle:

```text
commit A → build starts
commit B → build starts
commit C → build starts
```

without corrupting the active graph.

If A is active and B/C are building:

- A remains active until a newer eligible graph is successfully published.

Before publishing a build, re-check PostgreSQL state.

Preferred publication policy:

> The latest eligible successful commit wins.

An older build must never blindly overwrite a newer active graph.

---

# 20. Kubernetes Build Workspace

Every build gets an isolated workspace:

```text
/workspace/{buildId}/
├── source/
├── codegraph-home/
└── artifact/
```

The workspace should use pod-local ephemeral storage.

Make the root configurable:

```yaml
graph-ingestion:
  workspace:
    root: /workspace
```

Configure Kubernetes CPU and memory requests/limits appropriately for potentially expensive CodeGraph operations.

Do not require a persistent volume for graph build state.

If a pod is terminated during a build:

- the ephemeral workspace can be lost;
- PostgreSQL remains the durable record;
- the build must be recoverable/retriable;
- do not publish a partially completed build.

---

# 21. CodeGraph Persistence Model

Treat the entire CodeGraph project directory as the graph artifact.

For example:

```text
{codegraphHome}/projects/{projectSlug}/
├── index_state.json
└── memory/
    └── ...
```

Do not assume `graph.db` alone is sufficient.

Do not selectively copy files.

Package the complete project directory.

The exact contents may change between CodeGraph versions.

---

# 22. CodeGraph Artifact Packaging

Preferred format:

```text
tar.gz
```

ZIP is acceptable if there is a strong project-specific reason.

Example:

```text
uds-shard-resolver-c8f2.tar.gz
```

The archive should contain the complete CodeGraph project state.

The archive must not depend on the original Kubernetes pod filesystem.

---

# 23. CodeGraph Validation

Before packaging/upload:

1. CodeGraph process completed successfully.
2. Expected project directory exists.
3. Project directory is not empty.
4. Persisted CodeGraph state is readable.
5. The graph can be reopened/used as expected.
6. A basic graph/symbol query succeeds if supported.
7. Project identity matches the expected repository.
8. Manifest commit SHA matches the Git checkout.
9. Packaged archive can be opened.
10. Archive contains the complete project directory.

---

# 24. Artifact Manifest

Create `manifest.json`:

```json
{
  "repositoryId": "uds-shard-resolver",
  "repositoryName": "uds-shard-resolver",
  "branch": "main",
  "commitSha": "abc123...",
  "projectSlug": "uds-shard-resolver-c8f2",
  "codeGraphVersion": "x.y.z",
  "schemaVersion": "1",
  "generatedAt": "2026-10-04T08:00:00Z",
  "artifactFormat": "tar.gz"
}
```

The manifest must not contain secrets.

---

# 25. Azure Blob Storage

Use Azure Blob Storage for durable immutable graph artifacts.

Recommended structure:

```text
codegraph/
└── repositories/
    └── {repositoryId}/
        └── versions/
            └── {commitSha}/
                ├── codegraph.tar.gz
                └── manifest.json
```

Use Azure managed identity / `DefaultAzureCredential` where possible.

Do not hard-code credentials.

---

# 26. Upload Order

Use:

```text
1. Checkout commit
2. Start Node.js
3. Start/use CodeGraph
4. Build graph
5. Validate graph
6. Stop CodeGraph/Node.js
7. Create manifest
8. Package complete project directory
9. Validate archive
10. Upload archive
11. Upload manifest
12. Verify upload
13. Atomically publish in PostgreSQL
```

The CodeGraph process must be stopped before deleting the build workspace.

Do not publish the graph before the artifact is completely uploaded and validated.

---

# 27. Atomic Publication

Publication occurs transactionally in PostgreSQL.

Conceptually:

```text
BEGIN

lock/revalidate repository

verify build is still eligible

verify artifact upload succeeded

update active_commit_sha
update active_graph_uri
update graph_status
update target_commit_sha
mark graph_build PUBLISHED

COMMIT
```

If publication fails:

- previous active graph remains active;
- newly uploaded artifact remains unpublished;
- cleanup can occur asynchronously.

Never delete the old active artifact before successful publication.

---

# 28. Retention

Retain the latest **5 successful graph versions per repository** by default.

Make this configurable.

Publish the new version first.

Then asynchronously remove versions older than the retention threshold.

Never delete:

- active version;
- version currently required by an MCP server;
- version involved in an in-progress build.

Temporary retention beyond five versions is acceptable if cleanup is delayed.

---

# 29. MCP Server Interaction

The Graph Ingestion Service does not implement MCP.

The future MCP server will:

```text
repositoryId
      ↓
PostgreSQL
      ↓
active graph URI
      ↓
Azure Blob
      ↓
local cache
      ↓
restore CodeGraph project state
      ↓
CodeGraph queries
```

The MCP server should not search Blob Storage for the newest archive.

PostgreSQL determines the active graph.

---

# 30. GitHub Webhook

Implement if appropriate:

```http
POST /api/v1/github/webhooks
```

Requirements:

1. Validate GitHub webhook signature.
2. Identify registered repository.
3. Process only `main`.
4. Determine resulting commit SHA.
5. Create/enqueue graph build.
6. Return quickly.
7. Do not run the entire build synchronously inside the webhook request.

---

# 31. Observability

Include:

```text
buildId
repositoryId
commitSha
```

in relevant logs.

Log lifecycle transitions:

```text
QUEUED
BUILDING
VALIDATING
PACKAGING
UPLOADING
PUBLISHED
FAILED
```

Add metrics for:

```text
graph_build_total
graph_build_success_total
graph_build_failure_total
graph_build_duration
graph_build_validation_duration
graph_build_upload_duration
graph_build_active_age
graph_build_retention_cleanup_total
```

Include Node.js/CodeGraph process failures and exit codes in diagnostics.

Do not leak secrets.

---

# 32. Process Failure Handling

Explicitly handle:

### Node.js fails to start

```text
build = FAILED
active graph = unchanged
```

### CodeGraph fails

```text
build = FAILED
active graph = unchanged
```

### Node.js/CodeGraph times out

```text
terminate gracefully
wait grace period
force terminate if necessary
build = FAILED
active graph = unchanged
```

### Node.js process crashes

Detect the process exit and fail the build.

### Pod termination

The build must not become published unless PostgreSQL publication completed successfully.

---

# 33. Error Handling

Meaningful exceptions may include:

```text
RepositoryNotFoundException
InvalidCommitException
CommitNotOnMainException
GraphBuildAlreadyRunningException
NodeProcessStartException
CodeGraphExecutionException
CodeGraphTimeoutException
GraphValidationException
GraphArtifactPackagingException
GraphArtifactUploadException
GraphPublicationException
```

Use existing project conventions where applicable.

---

# 34. Security

The service must:

1. Authenticate/authorize REST APIs.
2. Validate GitHub webhook signatures.
3. Only build registered repositories.
4. Never allow arbitrary Git URLs.
5. Never accept arbitrary filesystem paths.
6. Validate commit SHA.
7. Use managed identity for Azure.
8. Prevent command injection.
9. Pass external process arguments safely.
10. Apply execution timeouts.
11. Restrict resource consumption.
12. Ensure temporary workspaces are isolated.

---

# 35. Suggested Package Structure

Adapt to existing repository conventions:

```text
api/
application/
domain/
repository/
git/
codegraph/
storage/
validation/
config/
```

The `codegraph` package should contain the process/runtime integration, for example:

```text
codegraph/
├── CodeGraphRunner
├── ProcessCodeGraphRunner
├── NodeProcessManager
├── CodeGraphRequest
├── CodeGraphResult
├── CodeGraphArtifactLocator
└── CodeGraphRuntimeConfiguration
```

Keep Node.js process lifecycle management separate from business/application logic.

---

# 36. Configuration

Configuration should cover:

```text
PostgreSQL
Azure Blob
CodeGraph Node.js package/runtime
Node.js executable
CodeGraph startup command
CodeGraph timeout
Node.js shutdown grace period
workspace root
artifact format
retention count
maximum concurrent builds
GitHub configuration
registered repositories
```

Example:

```yaml
graph-ingestion:
  workspace:
    root: /workspace

  codegraph:
    node-command: node
    startup-timeout: 60s
    build-timeout: 30m
    shutdown-grace-period: 10s

  retention:
    versions: 5
```

Do not hard-code environment-specific paths.

---

# 37. Testing

## Unit tests

Test:

- commit validation
- idempotency
- lifecycle transitions
- publication eligibility
- concurrency
- manifest generation
- artifact path generation
- retention
- Node.js process lifecycle
- CodeGraph runner failure handling

Mock external processes in ordinary unit tests.

## Integration tests

Test:

- PostgreSQL
- REST APIs
- publication transaction
- concurrent builds
- Azure storage abstraction

## CodeGraph integration test

Provide an optional integration test that uses the real Node.js + CodeGraph runtime:

```text
Test repository
      ↓
Java starts Node.js
      ↓
Node.js starts CodeGraph
      ↓
CodeGraph indexes repository
      ↓
Graph project generated
      ↓
Artifact packaged
      ↓
Artifact reopened/validated
```

The ordinary test suite should not require CodeGraph unless the existing project already follows that pattern.

---

# 38. Docker/Kubernetes Image

The production container image must explicitly include:

```text
Java runtime
Node.js runtime
CodeGraph Node.js package
Git
required OS utilities
```

Do not assume the Kubernetes base image contains Node.js.

Do not rely on a developer's globally installed npm package.

Pin versions where appropriate for reproducibility.

The image should be capable of executing the same CodeGraph runtime used by the integration tests.

---

# 39. Failure Scenarios

Test:

### CodeGraph startup failure

Active graph unchanged.

### CodeGraph build failure

Active graph unchanged.

### Packaging failure

Active graph unchanged.

### Blob upload failure

Active graph unchanged.

### PostgreSQL publication failure

Previous active graph remains active.

### Newer commit arrives

Older build cannot incorrectly replace the newer active graph.

### Duplicate request

Existing build/artifact is reused.

### Pod dies

No incomplete graph becomes active.

### Node.js child process remains alive

Process manager must detect and terminate it.

---

# 40. Definition of Done

The implementation is complete when:

- Java/Spring Boot service runs in Kubernetes.
- The container contains Java, Node.js, Git, and the required CodeGraph Node.js runtime.
- Java starts the Node.js process for CodeGraph.
- Java owns the Node.js/CodeGraph process lifecycle.
- Every build gets an isolated ephemeral workspace.
- The exact Git commit is checked out before CodeGraph runs.
- CodeGraph runs against that checkout.
- The complete CodeGraph project directory is packaged.
- Manifest is generated.
- Artifact is uploaded to Azure Blob.
- PostgreSQL records the build.
- Active graph is updated transactionally.
- Failed builds never replace active graph.
- Duplicate requests are idempotent.
- Concurrent builds are safe.
- Latest eligible successful commit wins.
- Last five successful versions are retained.
- REST status APIs work.
- GitHub webhook processing works.
- Process timeouts and failures are handled.
- Node.js/CodeGraph child processes cannot remain orphaned.
- Observability exists.
- Security controls exist.
- Tests cover critical scenarios.
- MCP can later consume the artifact through the PostgreSQL active pointer.
- MCP query implementation is not included in this service.

---

## Node.js Runtime Boundary — Important Architectural Constraint

Node.js is **not a general-purpose runtime for this application**.

The Graph Ingestion Service is a Java/Spring Boot application.

Node.js must be used **only for the CodeGraph generation step**, because the selected CodeGraph distribution requires the Node.js runtime/package.

The responsibility boundary is:

```text
Java / Spring Boot
│
├── REST API
├── request validation
├── Git repository operations
├── commit resolution
├── Git checkout
├── PostgreSQL control plane
├── build orchestration
├── Azure Blob Storage
├── artifact packaging
├── artifact validation
├── retention
├── logging/metrics
├── concurrency control
└── lifecycle management
        │
        │ ONLY during CodeGraph generation
        ▼
   Node.js process
        │
        ▼
     CodeGraph
```

### Strict requirement

Do not introduce Node.js into any part of the application other than the CodeGraph integration.

In particular:

- Do not implement REST APIs in Node.js.
- Do not implement Git operations in Node.js.
- Do not implement Azure Blob operations in Node.js.
- Do not implement PostgreSQL access in Node.js.
- Do not implement artifact packaging in Node.js.
- Do not implement scheduling in Node.js.
- Do not implement business logic in Node.js.
- Do not use Node.js as a sidecar for general application functionality.
- Do not move Java application responsibilities into JavaScript merely because Node.js is already present in the container.

### Process lifecycle

Java owns the lifecycle of the Node.js process.

The lifecycle is:

```text
Java starts build
      │
      ▼
Git checkout
      │
      ▼
Java starts Node.js
      │
      ▼
Node.js starts/executes CodeGraph
      │
      ▼
CodeGraph generates graph state
      │
      ▼
Java validates generated state
      │
      ▼
Java terminates Node.js/CodeGraph
      │
      ▼
Java packages artifact
      │
      ▼
Java uploads to Azure Blob
      │
      ▼
Java publishes through PostgreSQL
```

Once CodeGraph generation has completed, the Node.js process should no longer be required for the remainder of the ingestion workflow.

### Container requirement

The production Kubernetes container may contain:

```text
Java
Node.js
CodeGraph
Git
```

but the presence of Node.js must not imply that Node.js is part of the application's general runtime architecture.

The Java application remains the sole application runtime and orchestration layer.

### Implementation requirement

Keep the Node.js/CodeGraph integration isolated behind a small Java abstraction such as:

```java
public interface CodeGraphRunner {

    CodeGraphResult build(CodeGraphRequest request);
}
```

For example:

```text
codegraph/
├── CodeGraphRunner
├── ProcessCodeGraphRunner
├── NodeProcessManager
├── CodeGraphRequest
├── CodeGraphResult
└── CodeGraphArtifactLocator
```

`NodeProcessManager` should contain the Node.js-specific process-management logic.

No other application package should directly interact with Node.js.

### Failure isolation

If Node.js or CodeGraph fails:

```text
Node.js/CodeGraph failure
        │
        ▼
CodeGraphRunner reports failure
        │
        ▼
Java marks build FAILED
        │
        ▼
Java cleans up the process/workspace
        │
        ▼
Active graph remains unchanged
```

The rest of the application must remain Java-based and must not depend on Node.js being continuously available.

### Key architectural principle

> **Node.js is an implementation detail of the CodeGraph adapter, not a runtime dependency of the Graph Ingestion Service itself.**

---

# 41. Final Architecture Invariant

The complete production flow is:

```text
                 GitHub
                    │
                    │ merge / commit
                    ▼
          ┌─────────────────────┐
          │ Java Graph          │
          │ Ingestion Service   │
          │                     │
          │ Kubernetes Pod      │
          └──────────┬──────────┘
                     │
                     ▼
              Git checkout
              exact commit
                     │
                     ▼
              ┌─────────────┐
              │   Node.js   │
              └──────┬──────┘
                     │
                     ▼
              ┌─────────────┐
              │  CodeGraph  │
              └──────┬──────┘
                     │
                     ▼
          CodeGraph project directory
                     │
                     ▼
                tar.gz
                     │
              ┌──────┴──────┐
              ▼             ▼
        Azure Blob      PostgreSQL
        artifact        control plane
              │             │
              └──────┬──────┘
                     │
                     ▼
                MCP Server
                     │
                     ▼
                  Agents
```

The key responsibility boundaries are:

> **Java owns orchestration and process lifecycle.**

> **Node.js provides the runtime through which CodeGraph is started/used.**

> **CodeGraph builds the code intelligence graph from the exact Git checkout.**

> **Azure Blob stores immutable graph artifacts.**

> **PostgreSQL determines which graph version is active.**

> **The future MCP server loads and queries the active graph.**

Do not collapse these responsibilities into a single process or component unless the existing CodeGraph implementation requires it.