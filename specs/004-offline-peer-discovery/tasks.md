# Tasks: Offline Peer Discovery & Encounter Protocol

**Input**: `specs/004-offline-peer-discovery/spec.md`, `specs/004-offline-peer-discovery/plan.md`  
**Goal**: BLE-independent discovery/encounter core plus adapter contracts.

## Phase 1: Domain Foundation

- [x] T001 Define discovery value objects.
- [x] T002 Define discovery advertisement v1 model.
- [x] T003 Define candidate states and state machine.
- [x] T004 Define encounter states and state machine.
- [x] T005 Define discovery and encounter event types.
- [x] T006 Define error taxonomy.

## Phase 2: Ephemeral Advertising

- [x] T007 Implement rotation secret contract.
- [x] T008 Implement HMAC-SHA-256 ephemeral ID derivation.
- [x] T009 Implement configurable rotation interval.
- [x] T010 Implement advertisement encoding budget check.
- [x] T011 Verify advertisement encoding contains no `NodeId` or public key.

## Phase 3: Candidate Lifecycle

- [x] T012 Implement strict advertisement validation.
- [x] T013 Create/update candidates from observations.
- [x] T014 Deduplicate observations inside valid windows.
- [x] T015 Expire non-terminal stale candidates.
- [x] T016 Persist candidate state and failure reason.

## Phase 4: Control Channel

- [x] T017 Define `DiscoveryControlFrame`.
- [x] T018 Implement frame size limits and strict parsing.
- [x] T019 Enforce monotonic sequence numbers.
- [x] T020 Bind frames to session nonce.
- [x] T021 Define BLE-independent control channel contracts.

## Phase 5: Authentication

- [ ] T022 Negotiate discovery, identity and proof versions.
- [ ] T023 Exchange identity documents.
- [x] T024 Validate remote identity through Identity Core.
- [ ] T025 Create mutual challenges.
- [ ] T026 Verify mutual proofs bound to session nonce and both NodeIds.
- [x] T027 Reject unilateral authentication.

## Phase 6: Encounter Lifecycle

- [x] T028 Create authenticated peer only after mutual auth.
- [x] T029 Create persisted encounter from authenticated peer.
- [x] T030 Enforce one active logical encounter per remote node.
- [x] T031 Record proximity observations.
- [x] T032 Implement contact loss grace period.
- [x] T033 Implement rediscovery as new encounter after closure.

## Phase 7: Recovery and Observability

- [x] T034 Recover after process death and assume physical channels destroyed.
- [x] T035 Mark active encounters contact-lost on recovery.
- [x] T036 Emit discovery events.
- [x] T037 Emit encounter events.
- [x] T038 Add metrics counters and timing metrics.

## Phase 8: Adapters and Tests

- [x] T039 Implement fake discovery transport.
- [x] T040 Implement fake control channel.
- [ ] T041 Implement Android BLE discovery adapter.
- [ ] T042 Implement Android BLE control channel adapter.
- [x] T043 Add protocol test vectors.
- [ ] T044 Run mandatory scenarios A-L.
