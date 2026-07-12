# Tasks: Transport-Independent Transfer Core

**Input**: `specs/002-transfer-core/spec.md`, `specs/002-transfer-core/plan.md`  
**Goal**: A tested, durable, transport-independent Transfer Core.

## Phase 1: Domain Foundation

- [ ] T001 Define value objects in `core/transfer-domain/`.
- [ ] T002 Define typed error hierarchy.
- [ ] T003 Define transfer entities and event types.
- [ ] T004 Implement transfer state machine and invalid-transition rejection.
- [ ] T005 Implement manifest validation invariants.
- [ ] T006 Implement missing chunk range abstraction and calculator.

## Phase 2: Contracts

- [ ] T007 Define bounded stream interfaces.
- [ ] T008 Define public `TransferCore` API.
- [ ] T009 Define transfer snapshots, command results and event listener contracts.
- [ ] T010 Define `ChunkInput` and `ChunkReadHandle`.
- [ ] T011 Verify contract module has no Android, BLE, Wi-Fi Direct or network imports.

## Phase 3: Storage Boundary

- [ ] T012 Define `MetadataStore`.
- [ ] T013 Define `ChunkStore`.
- [ ] T014 Define `ObjectStore`.
- [ ] T015 Define `TransactionManager`.
- [ ] T016 Document atomicity guarantees expected from concrete storage adapters.

## Phase 4: Application Services

- [ ] T017 Implement object preparation by streaming.
- [ ] T018 Implement outbound transfer creation.
- [ ] T019 Implement inbound manifest acceptance.
- [ ] T020 Implement activation/interruption/waiting/resume/cancel transitions.
- [ ] T021 Implement per-transfer mutation serialization.
- [ ] T022 Implement durable event append in same transaction as state mutation.

## Phase 5: Chunk Pipeline

- [ ] T023 Implement `acceptChunk()` validation.
- [ ] T024 Implement temporary write and incremental SHA-256.
- [ ] T025 Commit verified chunks only after hash match.
- [ ] T026 Make duplicate valid chunks idempotent.
- [ ] T027 Reject corrupt chunks without increasing progress.
- [ ] T028 Emit receive/verified/rejected events.

## Phase 6: Reconstruction and Recovery

- [ ] T029 Implement `openChunk()` with bounded reads.
- [ ] T030 Implement object reconstruction from verified chunks.
- [ ] T031 Verify final object hash before completion.
- [ ] T032 Implement atomic finalization abstraction.
- [ ] T033 Implement recovery service.
- [ ] T034 Implement recovery policies for temp, missing and stray chunks.

## Phase 7: Testing Adapters

- [ ] T035 Implement in-memory storage adapter for unit/integration tests.
- [ ] T036 Implement loopback transfer adapter.
- [ ] T037 Implement file-like transfer adapter.
- [ ] T038 Add duplicate, out-of-order and corruption fixtures.
- [ ] T039 Add process-death/recovery fixture.

## Phase 8: Acceptance Scenarios

- [ ] T040 Scenario A: 1 GiB loopback transfer without network.
- [ ] T041 Scenario B: interrupt at 35%, recover and resume missing chunks only.
- [ ] T042 Scenario C: duplicate every chunk.
- [ ] T043 Scenario D: out-of-order chunk sequence.
- [ ] T044 Scenario E: corrupt chunk rejection and later acceptance.
- [ ] T045 Scenario F: process death and recovery to `WAITING`.
- [ ] T046 Scenario G: final hash mismatch forbids `COMPLETED`.

## Phase 9: Verification

- [ ] T047 Scan Core modules for forbidden imports.
- [ ] T048 Run unit tests.
- [ ] T049 Run integration tests.
- [ ] T050 Run fault injection tests.
- [ ] T051 Update `docs/testing/TRANSFER-CORE-FAULT-MODEL.md` with results.
- [ ] T052 Finalize `docs/adr/ADR-002-TRANSFER-INDEPENDENCE.md`.
