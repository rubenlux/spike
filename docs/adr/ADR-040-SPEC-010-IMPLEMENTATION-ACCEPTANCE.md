# ADR-040: SPEC-010 Implementation Acceptance — Local Peer Directory, Human Identity Binding & Trust Establishment Protocol

**Status**: Accepted  
**Date**: 2026-07-21  
**Spec**: `specs/010-local-peer-directory/spec.md`  
**Spec Version**: Draft 1.0 (Normative)  
**Commit Hash**: `5931b7250e1bdff3160378976b88f40938475bbf`  

---

## 1. Context

SPEC-010 defines the offline-first Local Peer Directory, Human Identity Binding, Readable Fingerprint derivation, Verification Ceremonies (QR, SAS, Manual Fingerprint), Verification Evidence generation, Trust Establishment & Overrides, Identity Change Detection, Multi-Device Human Binding, Device Replacement, Trust Migration, and Crash Recovery.

---

## 2. Scope

### In-Scope (Responsibility of SPEC-010 / LocalPeerDirectory)
* Persistent local peer directory entries and metadata classification.
* User-assigned local aliases (`LocalAlias`).
* Human identity records (`HumanIdentity`) separated from cryptographic identities (`NodeId`).
* One-to-many human-to-node bindings (`HumanNodeBinding`).
* Deterministic readable fingerprint derivation (SHA-256 base32 raw and 10 groups of 4 characters formatted).
* Short Authentication String derivation (6 decimal digits + 3 dictionary words).
* Verification session lifecycle (`VerificationSession`) and replay protection via single-use nonces.
* Verification evidence generation (`VerificationEvidence`) and transcript hashing.
* Evidence-based trust establishment and recorded unverified trust overrides (`UNVERIFIED_TRUST_OVERRIDE`).
* Device replacement operations and explicit trust migration plans.
* Identity history audit logging (`IdentityHistoryEntry`).
* Delegation of trust state transitions strictly via SPEC-009's public `LocalPeerSecurityCore` interface.
* Boot crash recovery with state reconciliation and zero automatic trust upgrades.

### Out-of-Scope (Explicitly Excluded from SPEC-010)
* Peer discovery mechanisms (handled by SPEC-004).
* Cryptographic key pair generation and node ID derivation (handled by SPEC-003).
* AEAD channel encryption and secure session handshakes (handled by SPEC-005).
* Physical/hybrid transport protocols (handled by SPEC-006).
* Global user directories, cloud accounts, phone number verification, or central identity providers.

---

## 3. Architectural Dependencies

### Upstream Dependencies (Required by SPEC-010)
* **SPEC-003 Identity**: Provides persistent `NodeId` derivation and canonical public identity data.
* **SPEC-004 Peer Discovery**: Provides authenticated peer discovery notifications (`observeAuthenticatedPeer`).
* **SPEC-005 Secure Session**: Provides authenticated peer session context and transcript binding.
* **SPEC-006 Hybrid Transport**: Preserves human identity bindings across transport switches.
* **SPEC-009 Local Security**: Public `LocalPeerSecurityCore` interface receives all trust state upgrades/downgrades (`trustPeer`, `removeTrust`, `blockPeer`).

### Downstream Dependents (Requires SPEC-010)
* **SPEC-011+ High-Level Applications**: Messaging, UI presentation layers, and user contact management rely on `LocalPeerDirectoryCore`.

---

## 4. Implementation Maturity

* **API Stability**: `Stable` (Normative `LocalPeerDirectoryCore` & `VerificationSessionService` contracts).
* **Implementation State**: `Production Ready` (Decoupled Kotlin modules with zero framework leaks).
* **Performance Profile**: `Baseline` (Zero-allocation `@JvmInline value class` domain IDs, thread-safe in-memory/persistence repository).
* **Security Review**: `Passed` (Strict evidence-based trust, SAS/QR session binding hashes, replay protection, zero bypass).
* **Spec Compatibility**: `SPEC Draft 1.0 (Normative)`.

---

## 5. Decision

Accept the formal implementation of SPEC-010 across five decoupled Kotlin modules in the repository:

### Modules Created & Configured
1. **`:core:peer-directory-domain`**: Pure leaf domain model containing entities (`PeerDirectoryEntry`, `HumanIdentity`, `HumanNodeBinding`, `VerificationSession`, `VerificationEvidence`, `IdentityHistoryEntry`, `DeviceReplacementOperation`, `TrustMigrationPlan`, `QrVerificationPayload`), inline value class VOs (`HumanIdentityId`, `BindingId`, `VerificationSessionId`, `VerificationEvidenceId`, `ReplacementOperationId`, `TrustMigrationPlanId`, `HistoryEntryId`, `LocalAlias`, `VerificationTranscriptHash`), `ReadableFingerprintGenerator` (SHA-256 base32 raw / 10 groups of 4 uppercase characters formatted), `SasGenerator` (6 decimal digits + 3 dictionary words), and sealed `LocalPeerIdentityError` taxonomy.
2. **`:storage:peer-directory-storage-contracts`**: Pure persistence contract interface `PeerIdentityRepository`.
3. **`:core:peer-directory-contracts`**: Normative public API interfaces `LocalPeerDirectoryCore`, `VerificationSessionService`, and `PeerDirectoryEventListener`.
4. **`:core:peer-directory-application`**: Implementation services (`VerificationSessionServiceImpl`, `TrustEstablishmentServiceImpl`, `DeviceReplacementServiceImpl`, `PeerIdentityRecoveryServiceImpl`, `LocalPeerDirectoryCoreImpl`).
5. **`:core:peer-directory-testing`**: Reusable testing environment (`TestPeerDirectoryEnvironment`, `PeerDirectoryTestFixtures`, `InMemoryPeerIdentityRepository`, `FakeLocalPeerSecurityCore`, and acceptance test suite).

---

## 6. Test Suite & Verification Results

All automated unit and integration tests passed cleanly (`BUILD SUCCESSFUL`):

* **Domain Unit Tests**:
  - `ReadableFingerprintTest`: Validated deterministic SHA-256 Base32 raw encoding and 10 groups of 4 uppercase characters formatting.
  - `SasGeneratorTest`: Validated 6-digit decimal derivation and 3-word dictionary selection.
  - `PeerDirectoryValueObjectsTest`: Validated ID format validations and generation.

* **Application Unit Tests**:
  - `VerificationSessionServiceTest`: Verified QR payload generation, nonce replay blocking, SAS derivation & rejection, and manual fingerprint confirmation.
  - `TrustEstablishmentServiceTest`: Verified evidence-based trust, unverified trust override, binding revocation, and SPEC-009 security core delegation.
  - `LocalPeerDirectoryCoreTest`: Verified directory observation, encounter count increments, and local alias assignment.

* **Acceptance Scenario Test Suite (`PeerDirectoryAcceptanceScenariosTest`)**:
  - **Scenario A**: First Encounter & Observation (Peer observed $\to$ `OBSERVED` entry without trust) — **Passed**.
  - **Scenario B**: QR Verification Ceremony (QR payload, evidence generation, `VERIFIED` binding, SPEC-009 `TRUSTED` state) — **Passed**.
  - **Scenario C**: SAS Verification Ceremony (Derives 6 digits + 3 words, mutual confirmation, evidence, SPEC-009 `TRUSTED` state) — **Passed**.
  - **Scenario D**: Manual Fingerprint Verification (10 groups of 4 chars comparison, evidence, SPEC-009 `TRUSTED` state) — **Passed**.
  - **Scenario E**: Unverified Trust Override (Explicit confirmation message $\to$ `TRUSTED_UNVERIFIED` binding with `UNVERIFIED_TRUST_OVERRIDE` mode) — **Passed**.
  - **Scenario F**: Identity Change Detection (Same advertised alias with new `NodeId` triggers history logging without auto trust inheritance) — **Passed**.
  - **Scenario G**: Multi-Device Binding (Single `HumanIdentity` bound to multiple independent `NodeId`s; each verified separately) — **Passed**.
  - **Scenario H**: Binding Revocation (`revokeBinding` transitions to `REVOKED`, downgrades SPEC-009 trust to `OBSERVED`, preserves history) — **Passed**.
  - **Scenario I**: Device Replacement & Trust Migration (Old device replaced by new device; trust migration plan applied) — **Passed**.
  - **Scenario J**: Verification Replay Protection (Replayed QR payload nonce throws `QrReplayDetected`) — **Passed**.
  - **Scenario K**: Crash Recovery & Continuity (Recovers directory, bindings, history; expires non-terminal sessions; recovery never silently upgrades trust) — **Passed**.

---

## 7. Architectural Consequences

* **Strict Identity Separation**: Cryptographic identity (`NodeId`) and human identity (`HumanIdentityId`) are decoupled; public keys are never assumed to represent legal identities automatically.
* **Zero Silent TOFU Policy**: First encounters create `OBSERVED` entries; trust upgrades require valid `VerificationEvidence` or an explicitly recorded `UNVERIFIED_TRUST_OVERRIDE`.
* **Unidirectional Dependency Guarantee**: SPEC-010 consumes SPEC-009's public `LocalPeerSecurityCore` interface, while SPEC-009 remains completely independent of SPEC-010.
* **Audit Trail Preservation**: Binding revocations and device replacements update state and record append-only history without destroying identity history.

---

## 8. Final Status

**COMPLETE & ACCEPTED**. SPEC-010 is established as a formal architectural milestone.
