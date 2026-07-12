# Tasks: Decentralized Node Identity

**Input**: `specs/003-node-identity/spec.md`, `specs/003-node-identity/plan.md`  
**Goal**: Offline, transport-independent, self-certifying Node Identity Core.

## Phase 1: Domain Foundation

- [ ] T001 Define value objects in `core/identity-domain/`.
- [ ] T002 Define identity error taxonomy.
- [ ] T003 Define local, remote, document, proof, challenge and event entities.
- [ ] T004 Define identity lifecycle state machine.
- [ ] T005 Define domain tags and algorithm identifiers.

## Phase 2: Crypto and Encoding Contracts

- [ ] T006 Define crypto provider contract for Ed25519 key generation/sign/verify.
- [ ] T007 Define SHA-256 and CSPRNG provider contracts.
- [ ] T008 Define opaque private key handle contract.
- [ ] T009 Define canonical encoder contract.
- [ ] T010 Document deterministic encoding requirements in ADR-005.

## Phase 3: Derivation and Validation

- [ ] T011 Implement canonical public key representation.
- [ ] T012 Implement deterministic `KeyId` derivation.
- [ ] T013 Implement deterministic `NodeId` derivation.
- [ ] T014 Validate `NodeId + PublicKey` binding.
- [ ] T015 Validate `KeyId + PublicKey` binding.

## Phase 4: Identity Documents

- [ ] T016 Create unsigned identity document model.
- [ ] T017 Self-sign identity document with identity key.
- [ ] T018 Validate document size, version, algorithms and fields.
- [ ] T019 Verify self-signature.
- [ ] T020 Reject modified document fields.

## Phase 5: Persistence and Lifecycle

- [ ] T021 Define local identity repository.
- [ ] T022 Define remote identity repository.
- [ ] T023 Define identity event repository.
- [ ] T024 Implement identity creation transaction flow.
- [ ] T025 Implement startup validation.
- [ ] T026 Implement key-unavailable detection without silent replacement.
- [ ] T027 Implement compromised and retired transitions.

## Phase 6: Proof of Possession

- [ ] T028 Define challenge repository and challenge states.
- [ ] T029 Generate 256-bit+ challenges via CSPRNG.
- [ ] T030 Implement proof creation.
- [ ] T031 Verify proof signature, verifier, claimant and context.
- [ ] T032 Consume challenges atomically.
- [ ] T033 Reject replay, expired and wrong-context proofs.

## Phase 7: Public API and Observability

- [ ] T034 Define `IdentityCore` public API.
- [ ] T035 Implement domain-separated `sign()`.
- [ ] T036 Implement verification result API without generic exceptions for expected failures.
- [ ] T037 Emit durable identity events.
- [ ] T038 Add metrics counters.

## Phase 8: Testing

- [ ] T039 Add deterministic crypto test vector file.
- [ ] T040 Scenario A: offline identity creation and restart.
- [ ] T041 Scenario B: two independent nodes and proof.
- [ ] T042 Scenario C: transport changes do not affect `NodeId`.
- [ ] T043 Scenario D: modified document rejected.
- [ ] T044 Scenario E: substituted `NodeId` rejected.
- [ ] T045 Scenario F: proof replay rejected.
- [ ] T046 Scenario G: wrong context rejected.
- [ ] T047 Scenario H: missing private key detected.
- [ ] T048 Scenario I: process death during creation detected.

## Phase 9: Verification

- [ ] T049 Scan Identity Core for forbidden Android/network/transport imports.
- [ ] T050 Confirm normal API exposes no private key bytes.
- [ ] T051 Finalize ADR-003, ADR-004 and ADR-005.
