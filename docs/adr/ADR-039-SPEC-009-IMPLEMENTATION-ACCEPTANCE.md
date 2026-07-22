# ADR-039: SPEC-009 Implementation Acceptance — Peer Trust, Authorization & Local Capability Security Model

**Status**: Accepted  
**Date**: 2026-07-21  
**Spec**: `specs/009-peer-trust-security/spec.md`  
**Spec Version**: Draft 1.0 (Normative)  
**Commit Hash**: `5931b7250e1bdff3160378976b88f40938475bbf`  

---

## 1. Context

SPEC-009 defines the offline-first Local Capability Security Model for node identity authorization, peer trust state classification, capability definition, explicit constrained capability grants, resource reservations, rate limiting, storage quota enforcement, active operation revocation, explainable authorization decisions, append-only security audit logging, and boot crash recovery.

---

## 2. Scope

### In-Scope (Responsibility of SPEC-009 / LocalPeerSecurity)
* Local peer trust state classification and transition machine.
* Protected core operation authorization.
* Capability definition registry.
* Explicit constrained capability grants (ALLOW / DENY).
* Normative 13-step authorization evaluation pipeline.
* Resource reservations and atomic consumption.
* Usage accounting and quota enforcement ($\text{AVAILABLE} = \text{LIMIT} - \text{CONSUMED} - \text{RESERVED}$).
* Fixed Window rate limiting per peer/capability.
* Storage quotas across inbox, object transfer, and temporary storage.
* Authorization lease lifecycle and checkpoint validation.
* Peer blocking and immediate active lease revocation.
* Decision explainability API.
* Append-only security audit event logging.
* Boot crash recovery with Fail-Closed state guarantees.

### Out-of-Scope (Explicitly Excluded from SPEC-009)
* Peer discovery (handled by SPEC-004).
* Identity derivation and cryptographic key establishment (handled by SPEC-003).
* Secure session establishment and AEAD channel encryption (handled by SPEC-005).
* Physical or hybrid transport implementations (handled by SPEC-006).
* Multi-hop routing and mesh forwarding.
* Cross-node data synchronization or CRDT mesh state reconciliation.
* Centralized identity, cloud ACLs, OAuth, or PKI certificate authorities.

---

## 3. Architectural Dependencies

### Upstream Dependencies (Required by SPEC-009)
* **SPEC-003 Identity**: Provides persistent `NodeId` derivation and cryptographic identity model.
* **SPEC-005 Secure Session**: Provides authenticated peer session context and secure record channels.
* **SPEC-006 Hybrid Transport**: Provides physical/logical transport link context for authorization decisions.
* **SPEC-007 Durable Object Transfer**: Integrates with `object.transfer.receive` authorization and storage reservations.
* **SPEC-008 Offline Messaging**: Integrates with `messaging.message.receive` and `messaging.message.process` authorization.

### Downstream Dependents (Requires SPEC-009)
* **SPEC-010+ Core Services**: All future protected application services, mesh protocols, and data channels must authenticate through `AuthorizationGateway`.

---

## 4. Implementation Maturity

* **API Stability**: `Stable` (Normative `AuthorizationGateway` & `LocalPeerSecurityCore` contracts).
* **Implementation State**: `Production Ready` (Decoupled Kotlin modules with zero framework leaks).
* **Performance Profile**: `Baseline` (Zero-allocation `@JvmInline value class` domain IDs, thread-safe in-memory/persistence repository).
* **Security Review**: `Passed` (Strict 13-step evaluation pipeline, explicit DENY precedence, fail-closed guarantees).
* **Spec Compatibility**: `SPEC Draft 1.0 (Normative)`.

---

## 5. Decision

Accept the formal implementation of SPEC-009 across five decoupled Kotlin modules in the repository:

### Modules Created & Configured
1. **`:core:security-domain`**: Pure leaf domain model containing entities (`PeerSecurityProfile`, `CapabilityDefinition`, `CapabilityGrant`, `AuthorizationDecision`, `AuthorizationLease`, `ResourceReservation`, `UsageCounter`, `RateLimitBucket`, `SecurityAuditEvent`, `AuthorizationExplanation`), inline value class VOs (`CapabilityId`, `GrantId`, `DecisionId`, `AuthorizationLeaseId`, `ReservationId`, `PolicyId`, `PolicyVersion`, `TrustVersion`, `RevocationVersion`), sealed `Constraint` hierarchy, sealed `LocalPeerSecurityError` taxonomy, and pure domain `TrustStateMachine`.
2. **`:storage:security-storage-contracts`**: Pure persistence contract interface `SecurityRepository` free of storage framework or database leaks.
3. **`:core:security-contracts`**: Normative public API interfaces `AuthorizationGateway`, `LocalPeerSecurityCore`, and `SecurityEventListener`.
4. **`:core:security-application`**: Implementation services (`PolicyEvaluationEngine` enforcing the exact 13-step normative evaluation pipeline, `ResourceReservationManager`, `RateLimiter`, `QuotaManager`, `AuthorizationGatewayImpl`, `LocalPeerSecurityCoreImpl`, `SecurityRecoveryService`).
5. **`:core:security-testing`**: Reusable testing environment (`TestSecurityEnvironment`, `SecurityTestFixtures`, thread-safe `InMemorySecurityRepository`, and acceptance test suite).

---

## 6. Test Suite & Verification Results

All automated unit and integration tests passed cleanly (`BUILD SUCCESSFUL`):

* **Domain Unit Tests**:
  - `TrustStateMachineTest`: Validated state machine transitions ($\text{UNKNOWN} \to \text{OBSERVED} \to \{\text{PENDING\_APPROVAL}, \text{TRUSTED}, \text{RESTRICTED}, \text{BLOCKED}\}$ and unblocking) and illegal transition exception handling.
  - `SecurityConstraintsTest`: Validated constraint boundary conditions and `ConstraintSet` lookups.
  - `SecurityValueObjectsTest`: Validated canonical `CapabilityId` (`namespace.resource.action`) parsing and grant expiration.

* **Application Unit Tests**:
  - `PolicyEvaluationEngineTest`: Verified 13-step evaluation pipeline, default deny, explicit DENY grant precedence, and message type constraint checks.
  - `AuthorizationGatewayTest`: Verified `authorizeAndReserve`, atomic one-shot grant consumption, and exhaustion state transitions.
  - `LocalPeerSecurityCoreTest`: Verified peer blocking, active lease revocation, and `explainDecision` output.

* **Acceptance Scenario Test Suite (`SecurityAcceptanceScenariosTest`)**:
  - **Scenario A**: Authenticated Unknown Peer without grants $\to$ `DENIED_NO_MATCHING_GRANT` (Passed).
  - **Scenario B**: Trusted Peer without specific grant $\to$ `DENIED_NO_MATCHING_GRANT` (Passed).
  - **Scenario C**: Explicit DENY Grant Precedence $\to$ `DENIED_EXPLICIT_GRANT` overriding ALLOW (Passed).
  - **Scenario D**: Expired & Exhausted Grants $\to$ `DENIED_GRANT_EXPIRED` & `DENIED_GRANT_EXHAUSTED` (Passed).
  - **Scenario E**: Message Type & Payload Size Constraints evaluation (Passed).
  - **Scenario F**: Object Transfer Authorization & Storage Capacity Reservation (Passed).
  - **Scenario G**: Rate Limits Enforcement (Passed).
  - **Scenario H**: Peer Blocking & Active Operation/Lease Revocation (Passed).
  - **Scenario I**: Lease Validation & Expiration (Passed).
  - **Scenario J**: Explainability API Output (Passed).
  - **Scenario K**: Crash Recovery & Fail-Closed State Verification (Passed).

---

## 7. Architectural Consequences

* **Strict Local Security Authority**: All protected core operations (messaging, object transfers, and future services) now route through `AuthorizationGateway`.
* **Zero Bypass Policy**: Direct protected state mutations or side effects are forbidden.
* **Fail-Closed Security Guarantee**: Unhandled state or persistence errors default to DENY.
* **Strong Type Safety**: Identifiers use `@JvmInline value class` and `NodeId` from `:core:identity-domain`.

---

## 8. Final Status

**COMPLETE & ACCEPTED**. SPEC-009 is established as a formal architectural milestone.
