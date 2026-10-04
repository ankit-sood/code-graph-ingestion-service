# CodeGraph Runtime Contract (Phase 4)

## Effective Invocation
The service invokes CodeGraph using two configured fields:

- `graph-ingestion.codegraph.node-command`
- `graph-ingestion.codegraph.startup-command`

Effective process command:

- `<node-command> <startup-command>`

Default production values:

- `node-command=node`
- `startup-command=codegraph-daemon`

## Working Directory
The process working directory is the build's checked-out source directory:

- `<workspaceRoot>/<buildId>/source`

## Runtime Environment Variables
The service passes these environment variables to the CodeGraph process:

- `CODEGRAPH_SOURCE_DIR` = absolute source path
- `CODEGRAPH_HOME_DIR` = absolute codegraph-home path
- `CODEGRAPH_ARTIFACT_DIR` = absolute artifact path
- `CODEGRAPH_BUILD_ID` = build id
- `CODEGRAPH_REPOSITORY_ID` = repository id
- `CODEGRAPH_COMMIT_SHA` = resolved commit SHA

## Timeouts and Shutdown
- Build timeout: `graph-ingestion.codegraph.build-timeout`
- Shutdown grace period: `graph-ingestion.codegraph.shutdown-grace-period`

If timeout is reached:
1. service sends graceful terminate
2. waits for grace period
3. force-kills process if still running

## Failure Mapping
- Startup failure (cannot spawn process): `NODE_PROCESS_START_FAILED`
- Process timeout: `CODEGRAPH_TIMEOUT`
- Non-zero exit: `CODEGRAPH_EXECUTION_FAILED`

## Observability
On success, service logs include:
- build id
- repository id
- commit SHA
- elapsed time
- exit code
- executed command

On failure, build is moved to `FAILED` and repository status is moved to `DEGRADED` by existing failure handling.
