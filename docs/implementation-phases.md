# Graph Ingestion Service - Phased Implementation Plan

## Purpose
This document defines the implementation phases for the Graph Ingestion Service so delivery can proceed incrementally with clear acceptance criteria and risk control.

## How To Use This Document During Implementation
- This file is the single source of truth for phase status and handoff context.
- At the start of each implementation session:
  - Update the Phase Tracker section.
  - Add a Session Log entry.
  - Confirm the current active phase and next checkpoint.
- At the end of each implementation session:
  - Mark completed checkpoints.
  - Record blockers and decisions.
  - Add a handoff note using the template in this file.

## Phase Tracker
Status values: NOT_STARTED, IN_PROGRESS, BLOCKED, DONE

| Phase | Name | Status | Owner | Start Date | Last Updated | Completion % | Next Checkpoint |
|---|---|---|---|---|---|---:|---|
| 0 | Architecture Baseline and Contracts | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 1 migration framework |
| 1 | PostgreSQL Control Plane Foundation | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 2 async orchestration skeleton |
| 2 | Async Build Orchestration Skeleton | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 3 git integration and commit validation |
| 3 | Git Integration and Commit Validation | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 4 runtime discovery and Node adapter |
| 4 | CodeGraph Runtime Discovery and Node Adapter | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 5 artifact validation and packaging |
| 5 | Artifact Validation, Packaging, and Manifest | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 6 Azure Blob upload integration |
| 6 | Azure Blob Upload and Verification | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 7 atomic publication and idempotency |
| 7 | Atomic Publication, Idempotency, and Concurrency Safety | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 8 retention and cleanup |
| 8 | Retention and Cleanup | DONE | Copilot | 2026-10-04 | 2026-10-04 | 100 | Start Phase 9 security and webhook integration |
| 9 | Security and Webhook Integration | NOT_STARTED | TBD | TBD | TBD | 0 | Implement webhook signature validation |
| 10 | Observability, Runtime Hardening, and Production Readiness | NOT_STARTED | TBD | TBD | TBD | 0 | Add build metrics and operational health checks |

## Session Log
| Date | Active Phase | Summary | Decisions | Blockers | Next Step |
|---|---|---|---|---|---|
| 2026-10-04 | Planning | Created phased implementation plan and tracker. | Use this file as resumable implementation source. | None | Start Phase 0 implementation checklist |
| 2026-10-04 | Phase 0 | Added API DTOs, contract stub controllers, lifecycle enums, configuration properties, and phase-0 contracts document. | Keep Phase 0 endpoints as contract stubs until persistence/orchestration phases. | None | Add global exception mapping and finish Phase 0 |
| 2026-10-04 | Phase 0 | Added global exception handling and explicit ErrorCode-to-HTTP status mappings. | Use centralized error mapping via RestControllerAdvice as baseline for future domain exceptions. | None | Begin Phase 1 with migration framework and schema |
| 2026-10-04 | Phase 1 | Added JPA/PostgreSQL stack, schema.sql control-plane DDL, entities, repositories, and integration tests for constraints and read model. | Use schema.sql as source of table creation and keep control-plane schema strict with enum check constraints. | None | Run tests and mark Phase 1 done |
| 2026-10-04 | Phase 1 | Validated repository CRUD, lock query, status transitions, unique and foreign-key constraints with integration tests. | Standardized on Spring SQL initialization via schema.sql for both app and tests. | None | Start Phase 2 orchestration implementation |
| 2026-10-04 | Phase 2 | Added async execution config, workspace manager abstraction, controller-to-service orchestration, and status progression skeleton with structured lifecycle logs. | Use simulated progression in background worker until Git/CodeGraph/Azure adapters are implemented. | None | Run full test suite and close Phase 2 |
| 2026-10-04 | Phase 2 | Validated end-to-end 202 build flow, async status transitions, persisted status lookup, and repository graph-status updates with integration tests. | Use test-only writable workspace root (`target/test-workspace`) because `/workspace` is not writable in local runtime. | None | Start Phase 3 git integration and commit validation |
| 2026-10-04 | Phase 3 | Implemented process-based Git adapter and integrated clone/resolve/lineage-check/checkout into build execution flow before subsequent lifecycle stages. | Keep commit resolution inside async execution stage and persist resolved SHA back into build/repository records. | None | Run full suite and finalize phase |
| 2026-10-04 | Phase 3 | Added local-repository integration tests for latest-main resolution, explicit commit checkout, invalid SHA rejection, and main-lineage enforcement; validated end-to-end build flow with real git commits. | Use local temporary git repositories in tests to avoid remote dependencies and guarantee deterministic commit ancestry. | None | Start Phase 4 runtime discovery and Node adapter |
| 2026-10-04 | Phase 4 | Implemented CodeGraphRunner/ProcessCodeGraphRunner and NodeProcessManager with timeout and shutdown control, and integrated CodeGraph execution into async build flow during BUILDING stage. | Keep runtime invocation contract externalized in configuration and pass workspace context via environment variables. | None | Add focused tests for timeout/failure mapping and finalize phase |
| 2026-10-04 | Phase 4 | Added runtime contract doc and tests for node process capture/timeout, runner error mapping, and end-to-end build failure when CodeGraph exits non-zero; validated full suite via `mvn -q test`. | Use deterministic `/usr/bin/env true/false` commands in tests to avoid depending on installed CodeGraph binaries. | None | Start Phase 5 artifact validation and packaging |
| 2026-10-04 | Phase 5 | Added artifact discovery/validation, tar.gz packaging, manifest generation, and archive integrity verification via filesystem artifact service; integrated into VALIDATING/PACKAGING transitions. | Keep artifact path local (`file://...`) until Phase 6 upload adapter is added; persist project slug and archive URI in build record. | None | Validate full suite and update tracker |
| 2026-10-04 | Phase 5 | Added unit tests for artifact service and extended build integration tests to verify packaged artifact metadata persistence; validated full suite via `mvn -q test`. | Use test runtime script to emit deterministic CodeGraph output under `CODEGRAPH_HOME_DIR` for packaging assertions. | None | Start Phase 6 Azure Blob upload integration |
| 2026-10-04 | Phase 6 | Added storage abstraction and upload pipeline integration with strict archive-first then manifest-second ordering, immutable destination paths, and upload verification. | Keep provider switchable via configuration; default provider is local for development and tests. | None | Add Azure implementation and retry/backoff behavior |
| 2026-10-04 | Phase 6 | Added Azure Blob adapter using DefaultAzureCredential with retry/backoff, immutable blob pathing (`pathPrefix/repositoryId/commitSha`), and existence/size verification; validated full suite via `mvn -q test`. | Use local storage adapter in tests to avoid cloud dependency while preserving same upload contract. | None | Start Phase 7 atomic publication and idempotency |
| 2026-10-04 | Phase 7 | Implemented idempotent create-build behavior under repository lock to deduplicate duplicate `repositoryId+commitSha` requests and reuse existing non-failed builds. | Failed builds remain eligible for a new retry request; in-flight or published builds are reused. | None | Add publication revalidation and race-condition tests |
| 2026-10-04 | Phase 7 | Added transactional publication revalidation with repository lock and latest-published-commit protection so stale completions cannot overwrite newer active graphs; validated full suite via `mvn -q test`. | Use `created_at` ordering among published builds to decide active pointer winner and preserve `BUILDING` graph status when other builds remain in progress. | None | Start Phase 8 retention and cleanup |
| 2026-10-04 | Phase 8 | Added retention cleanup service with asynchronous trigger after successful publication and lock-safe execution under repository update lock. | Retention policy keeps latest N published builds and always protects active/target commits from cleanup. | None | Add cleanup safety integration tests |
| 2026-10-04 | Phase 8 | Added integration tests for retention policy enforcement and protected commit safety; validated full suite via `mvn -q test`. | Cleanup deletes local file artifacts (`.tar.gz` + paired `.manifest.json`) and logs non-file URI skips for cloud artifacts. | None | Start Phase 9 security and webhook integration |
| 2026-10-05 | Phase 9 (Paused) | Rolled back all webhook/auth implementation files and config/error mappings after request to defer Phase 9; retained Phase 7/8 stability fixes (transactional publication service, post-commit async dispatch, concurrency-safe repository state updates). Re-ran full suite successfully with no failure markers. | Do not proceed with Phase 9 until explicitly requested. | None | Continue from stable post-Phase-8 baseline |

## Handoff Template
Copy this section into the latest Session Log row at end of each implementation session.

Active Phase: <phase-number-and-name>
Completed This Session: <what was implemented>
Artifacts Updated: <files, migrations, tests>
Open Decisions: <pending decisions>
Blockers: <external dependencies>
Next Concrete Step: <single next action>

## Guiding Invariants
- Active graph must always reference a fully built, validated, immutable artifact.
- Failed or partial builds must never be published as active.
- Java/Spring Boot owns orchestration end-to-end.
- Node.js is used only inside the CodeGraph adapter boundary.

## Phase Checkpoints
Use this as the implementation checklist and update checkboxes as each item is completed.

### Phase 0 checkpoints
- [x] API request/response DTOs are defined.
- [x] Build lifecycle state machine is finalized.
- [x] Error taxonomy and HTTP mappings are documented.
- [x] Configuration keys and defaults are defined.

### Phase 1 checkpoints
- [x] Migration framework is added.
- [x] Repository and graph-build schema is created.
- [x] Indexes and constraints are validated.
- [x] Persistence integration tests pass.

### Phase 2 checkpoints
- [x] POST build API returns 202 with buildId.
- [x] Background execution pipeline updates status transitions.
- [x] Workspace manager abstraction is implemented.
- [x] Structured logs include buildId/repositoryId/commitSha.

### Phase 3 checkpoints
- [x] Git adapter supports clone/fetch/checkout in isolated workspace.
- [x] Commit resolution supports explicit SHA and latest main.
- [x] Main-lineage validation is enforced.
- [x] HEAD verification matches requested commit SHA.

### Phase 4 checkpoints
- [x] Actual CodeGraph runtime invocation is documented.
- [x] CodeGraphRunner and ProcessCodeGraphRunner are implemented.
- [x] NodeProcessManager handles startup, timeout, and shutdown.
- [x] Process stdout/stderr and exit diagnostics are captured.

### Phase 5 checkpoints
- [x] CodeGraph project directory discovery is implemented.
- [x] Validation checks are implemented.
- [x] Full project directory tar.gz packaging is implemented.
- [x] Manifest generation and archive integrity checks pass.

### Phase 6 checkpoints
- [x] Azure Blob adapter is implemented with DefaultAzureCredential.
- [x] Archive upload then manifest upload order is enforced.
- [x] Upload verification and retry/backoff are implemented.
- [x] Immutable artifact pathing per commit is enforced.

### Phase 7 checkpoints
- [x] Transactional publication with revalidation is implemented.
- [x] Idempotency for repositoryId + commitSha is implemented.
- [x] Latest eligible successful commit policy is enforced.
- [x] Concurrency race-condition tests pass.

### Phase 8 checkpoints
- [x] Retention policy is configurable and enforced.
- [x] Cleanup excludes active and in-use versions.
- [x] Cleanup is asynchronous and observable.
- [x] Cleanup safety tests pass.

### Phase 9 checkpoints
- [ ] API authn/authz is integrated.
- [ ] GitHub webhook signature validation is implemented.
- [ ] Main branch filtering is enforced.
- [ ] Input/process hardening checks are implemented.

### Phase 10 checkpoints
- [ ] Metrics and timers are implemented.
- [ ] Health and readiness checks are implemented.
- [ ] Runtime dependencies and image requirements are documented.
- [ ] Production readiness checklist is complete.

## Phase 0 - Architecture Baseline and Contracts
### Scope
- Freeze API contract skeleton for:
  - POST /api/v1/repositories/{repositoryId}/graph-builds
  - GET /api/v1/repositories/{repositoryId}/graph-builds/{buildId}
  - GET /api/v1/repositories/{repositoryId}/graph-status
  - Optional POST /api/v1/github/webhooks
- Define domain lifecycle and status transitions:
  - QUEUED, BUILDING, VALIDATING, PACKAGING, UPLOADING, PUBLISHED, FAILED
- Define configuration model and defaults:
  - workspace root, timeouts, retention, concurrency, runtime commands
- Define error taxonomy and response mapping.

### Deliverables
- API DTOs and error response contract document.
- State machine definition for build transitions.
- Configuration key list with defaults and environment override policy.

### Exit Criteria
- Team agreement on external API and internal state model.
- No blocking ambiguities for persistence and orchestration implementation.

## Phase 1 - PostgreSQL Control Plane Foundation
### Scope
- Add persistence stack and migration framework.
- Create schema for repository and graph build control-plane tables.
- Add indexes, foreign keys, uniqueness, and optimistic/pessimistic locking strategy.
- Implement repository registration read model (trusted repositories only).

### Deliverables
- Migration scripts for v1 schema.
- Persistence adapters and domain mapping.
- Integration tests for CRUD, transitions, and lock behavior.

### Exit Criteria
- Build records and active pointer can be persisted safely.
- Transaction patterns for publication are proven in tests.

## Phase 2 - Async Build Orchestration Skeleton
### Scope
- Implement asynchronous job orchestration triggered by POST build API.
- Implement lifecycle progression with stubbed dependencies.
- Implement workspace manager abstraction for isolated build directories.
- Add structured logs with buildId, repositoryId, commitSha.

### Deliverables
- Working 202 Accepted flow with persisted build status updates.
- Background execution framework and job state tracking.
- Initial failure handling and retry policy skeleton.

### Exit Criteria
- End-to-end simulated flow works without Git/CodeGraph/Azure integrations.
- Status API reflects transitions accurately.

## Phase 3 - Git Integration and Commit Validation
### Scope
- Integrate Git adapter for clone/fetch/checkout in workspace/source.
- Resolve target commit from request or latest main.
- Validate commit exists and belongs to main lineage.
- Verify checked-out HEAD matches requested commit.

### Deliverables
- Git service abstraction with deterministic command execution and safe argument handling.
- Domain exceptions for invalid commit and branch lineage violations.
- Tests for commit resolution, lineage validation, and idempotent duplicate requests.

### Exit Criteria
- Exact commit checkout and validation are reliable under concurrent builds.

## Phase 4 - CodeGraph Runtime Discovery and Node Adapter
### Scope
- Inspect actual CodeGraph distribution in target runtime image.
- Document exact Node.js command, entry point, arguments, env variables, and readiness semantics.
- Implement CodeGraphRunner abstraction and ProcessCodeGraphRunner.
- Implement NodeProcessManager for startup, stdout/stderr capture, timeout, graceful/force shutdown.

### Deliverables
- codegraph integration notes with exact invocation contract.
- Process lifecycle management with orphan prevention.
- Structured result model (project location, runtime metadata, diagnostics).

### Exit Criteria
- Java can reliably start and stop Node.js/CodeGraph for one build.
- Failures are detected and mapped to FAILED state without affecting active graph.

## Phase 5 - Artifact Validation, Packaging, and Manifest
### Scope
- Locate generated CodeGraph project directory.
- Validate generated state (exists, non-empty, readable, reopen/query smoke check where supported).
- Package full project directory as tar.gz.
- Generate manifest.json and validate archive integrity.

### Deliverables
- Artifact locator and validator components.
- Packaging component with deterministic output paths.
- Manifest model and serializer.

### Exit Criteria
- Archive and manifest are reproducible and verifiably tied to repositoryId + commitSha.

## Phase 6 - Azure Blob Upload and Verification
### Scope
- Integrate Azure Blob client with DefaultAzureCredential.
- Upload order enforcement: archive first, manifest second.
- Verify upload completion and content availability.
- Enforce immutability policy for published commit artifacts.

### Deliverables
- Storage adapter abstraction and Azure implementation.
- Retry/backoff behavior for transient storage failures.
- URI generation aligned with required path structure.

### Exit Criteria
- Artifact upload is durable and verifiable before publication begins.

## Phase 7 - Atomic Publication, Idempotency, and Concurrency Safety
### Scope
- Implement publication transaction with revalidation under lock.
- Enforce policy: latest eligible successful commit wins.
- Idempotency behavior for duplicate repositoryId + commitSha requests.
- Ensure older builds cannot overwrite newer active state.

### Deliverables
- Publication service with transactional guarantees.
- Concurrency tests for out-of-order completion scenarios.
- Build deduplication logic and status reuse rules.

### Exit Criteria
- Active pointer remains correct across race conditions and retries.

## Phase 8 - Retention and Cleanup
### Scope
- Implement retention policy (default 5 successful versions per repository).
- Async cleanup of stale versions while protecting active/in-use versions.
- Add safe cleanup guards for in-progress builds and referenced artifacts.

### Deliverables
- Cleanup scheduler/service and retention config.
- Metrics and logs for cleanup operations.

### Exit Criteria
- Storage growth is controlled without risking active graph integrity.

## Phase 9 - Security and Webhook Integration
### Scope
- Add API authentication/authorization hooks according to platform standards.
- Add GitHub webhook endpoint with signature validation and branch filtering.
- Harden process execution against injection and path abuse.

### Deliverables
- Security configuration and request validation hardening.
- Webhook ingestion flow that enqueues builds asynchronously.

### Exit Criteria
- Only trusted repos and authenticated callers can trigger builds.

## Phase 10 - Observability, Runtime Hardening, and Production Readiness
### Scope
- Add metrics for totals, failures, duration, validation, upload, active age, retention cleanup.
- Add health/readiness checks.
- Define container and Kubernetes runtime requirements:
  - Java runtime
  - Node.js runtime
  - CodeGraph runtime/package
  - Git
- Add resource and timeout tuning guidance.

### Deliverables
- Monitoring dashboard metric map and log field standards.
- Deployment notes for container and Kubernetes settings.

### Exit Criteria
- Service is operable, diagnosable, and production-safe.

## Test Strategy by Phase
- Unit tests start in Phase 0 and expand each phase.
- Integration tests begin in Phase 1 for PostgreSQL and extend through publication.
- Concurrency and idempotency tests emphasized in Phase 7.
- Optional real CodeGraph runtime integration test added after Phase 4.

## Execution Order and Dependencies
- Phase 0 is required before all others.
- Phase 1 and Phase 2 can partially overlap after contracts stabilize.
- Phase 4 depends on completion of runtime discovery in target image.
- Phase 7 depends on Phases 1, 2, 3, 5, and 6.
- Phase 10 is finalized after all functional phases.

## Risks and Early Mitigations
- Unknown CodeGraph invocation details:
  - Mitigation: perform runtime discovery before implementing process runner logic.
- Concurrency race conditions on publication:
  - Mitigation: transactional locking plus deterministic publish eligibility checks.
- Artifact integrity drift across versions:
  - Mitigation: strict manifest + archive validation + immutable version paths.

## Definition of Ready for Phase Execution
- Runtime discovery environment is available with actual Node.js/CodeGraph distribution.
- PostgreSQL and Azure target environments are reachable for integration testing.
- Repository registration source of truth is defined.

## Definition of Done for Overall Program
- All required APIs implemented and tested.
- Active graph invariant is preserved across failure scenarios.
- Node.js lifecycle is fully Java-controlled and safely terminated.
- Artifact publication is transactional, immutable, and auditable.
- Retention, observability, security, and operational readiness are in place.
