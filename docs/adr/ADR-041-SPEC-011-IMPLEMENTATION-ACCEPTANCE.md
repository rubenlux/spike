# ADR-041 — SPEC-011 Implementation Acceptance

**Status:** Accepted
**Date:** 2026-07-22
**Commit Hash:** `caf4d70`
**Specification:** SPEC-011 — Offline Service Discovery, Capability Advertisement & Durable Local RPC Protocol
**Type:** Implementation Acceptance Record

---

## 1. Context

SPEC-011 defines the subsystem through which a node can:

- Register local services with versioned contracts;
- Advertise available services in a purely descriptive manner;
- Discover services offered by remote peers;
- Negotiate compatible contract versions before any invocation;
- Invoke remote services durably with immutable InvocationId across retries and reconnections;
- Enforce SPEC-009 authorization before any handler execution or side effect;
- Deduplicate executions via persisted state, not in-memory cache;
- Recover interrupted executions safely, preserving the `NON_IDEMPOTENT_COMMAND → UNKNOWN_OUTCOME` invariant.

This ADR records the acceptance of the full four-stage implementation.

---

## 2. Implementation Scope

### Stage 1 — `:core:service-discovery-domain`
| Artifact | Description |
|---|---|
| `ServiceDiscoveryValueObjects.kt` | `@JvmInline` value classes: `ServiceId`, `ContractId`, `ContractVersion`, `MethodId`, `InvocationId`, `ExecutionId`, `IdempotencyKey`, `AdvertisementId`, `ServiceInstanceId`, `ContractHash`, `Deadline`, and all enums |
| `ServiceDiscoveryEntities.kt` | Domain entities: `ServiceDefinition`, `ServiceContract`, `ServiceAdvertisement`, `RemoteServiceRecord`, `NegotiatedContract`, `RpcInvocation`, `RpcExecution`, `RpcResult`, `RpcCancellation`, `IdempotencyRecord` |
| `ContractHashGenerator.kt` | SHA-256 with domain tag `OFFLINE_SERVICE_CONTRACT_V1` |
| `ServiceRuntimeError.kt` | Sealed error taxonomy |

### Stage 2 — `:storage:service-discovery-storage-contracts` + `:core:service-discovery-contracts`
| Artifact | Description |
|---|---|
| `RpcRuntimeRepository.kt` | Pure persistence contract interface |
| `OfflineServiceRuntimeContracts.kt` | Public interfaces: `LocalServiceRegistry`, `ServiceAdvertisementService`, `ContractNegotiationService`, `RpcInvocationService`, `RpcExecutionService`, `OfflineServiceRuntime`, `ServiceHandler`, `ServiceRuntimeEventListener` |

### Stage 3 — `:core:service-discovery-application`
| Artifact | Responsibility |
|---|---|
| `LocalServiceRegistryImpl` | Service & contract registration, in-memory handler dispatch map |
| `ServiceAdvertisementServiceImpl` | Descriptive advertisement publishing; remote catalog management |
| `ContractNegotiationServiceImpl` | Major.Minor version matching; contract hash verification |
| `RpcExecutionServiceImpl` | SPEC-009 authorization gateway → durable dedup → idempotency → handler dispatch |
| `RpcInvocationServiceImpl` | Outbound lifecycle: immutable `InvocationId`, state machine, deadline enforcement |
| `ServiceRuntimeRecoveryServiceImpl` | Boot/crash recovery with `NON_IDEMPOTENT_COMMAND → UNKNOWN_OUTCOME` invariant |
| `OfflineServiceRuntimeImpl` | Normative public facade wiring all services |

### Stage 4 — `:core:service-discovery-testing`
| Artifact | Description |
|---|---|
| `InMemoryRpcRuntimeRepository` | In-memory implementation for acceptance tests |
| `TestLocalPeerSecurityCore` | SPEC-009 stub with per-capability allow/deny and call audit log |
| `ServiceDiscoveryTestFixtures` | Shared test environment factory and fixture builders |
| `OfflineServiceRuntimeAcceptanceScenariosTest` | 13 acceptance scenarios (A–M) |

---

## 3. Acceptance Test Results

All 13 scenarios passed with `BUILD SUCCESSFUL`.

| Scenario | Description | Result |
|---|---|---|
| **A** | Service Registration & Availability state transitions | ✅ PASS |
| **B** | Advertisement publishing is purely descriptive — no authorization implied | ✅ PASS |
| **C** | Advertisement processing creates a remote service catalog record | ✅ PASS |
| **D** | Contract negotiation succeeds for compatible Major.Minor version | ✅ PASS |
| **E** | Contract negotiation fails for incompatible version | ✅ PASS |
| **F** | Authorized inbound RPC executes handler and commits `SUCCESS` result | ✅ PASS |
| **G** | Unauthorized inbound RPC denied before handler execution; durable `REJECTED` audit record | ✅ PASS |
| **H** | Durable deduplication: duplicate `InvocationId` returns cached result, handler not re-executed | ✅ PASS |
| **I** | Idempotency key: same key + same payload returns cached result across different `InvocationId`s | ✅ PASS |
| **J** | Idempotency key conflict: same key + different payload hash rejected | ✅ PASS |
| **K** | Crash recovery: `NON_IDEMPOTENT_COMMAND` in `RUNNING` → `UNKNOWN_OUTCOME`, never re-executed | ✅ PASS |
| **L** | Late response after deadline: invocation marked `EXPIRED`, handler not executed, correct audit | ✅ PASS |
| **M** | Transport interruption + reencounter: same `InvocationId` returns original result; SPEC-009 called exactly once | ✅ PASS |

---

## 4. Functional Requirements Compliance

| Requirement | SPEC-011 Reference | Compliance |
|---|---|---|
| Local service registration with versioned contracts | §4.1 | ✅ `LocalServiceRegistryImpl` |
| Service state lifecycle (REGISTERED → AVAILABLE → PAUSED → UNREGISTERED) | §4.2 | ✅ `LocalServiceState` enum + registry |
| Descriptive advertisement — no authorization implied | §5.1 | ✅ `ServiceAdvertisementServiceImpl`; Scenario B |
| Remote service catalog from received advertisements | §5.2 | ✅ Scenario C |
| Contract version negotiation (Major.Minor matching) | §6 | ✅ `ContractNegotiationServiceImpl`; Scenarios D, E |
| Contract hash verification | §6.3 | ✅ SHA-256 with `OFFLINE_SERVICE_CONTRACT_V1` domain tag |
| SPEC-009 authorization before any handler execution | §7.1 | ✅ `RpcExecutionServiceImpl` step 4; Scenarios F, G, M |
| Immutable `InvocationId` across retries and reconnections | §7.2 | ✅ `RpcInvocationServiceImpl`; Scenarios H, M |
| Durable deduplication via persisted state | §7.3 | ✅ `repository.findExecutionByInvocation()`; Scenarios H, M |
| Idempotency key conflict detection | §7.4 | ✅ Scenarios I, J |
| Deadline enforcement and `EXPIRED` state | §7.5 | ✅ `ServiceRuntimeRecoveryServiceImpl`; Scenario L |
| Crash recovery: `NON_IDEMPOTENT_COMMAND → UNKNOWN_OUTCOME` | §8.1 | ✅ `ServiceRuntimeRecoveryServiceImpl`; Scenario K |
| Result commitment before response | §7.6 | ✅ `COMMITTING_RESULT` state before response dispatch |
| Authorization audit record on denial | §9.1 | ✅ `REJECTED` execution + `AUTHORIZATION_DENIED` result; Scenario G |

---

## 5. Non-Functional Requirements Compliance

| Requirement | Compliance |
|---|---|
| **Zero framework dependencies in domain** | ✅ `:core:service-discovery-domain` has no Spring, Hilt, or Android deps |
| **Strong typing — no primitive obsession** | ✅ All domain concepts wrapped in `@JvmInline value class` |
| **Transport-agnostic contracts** | ✅ No transport types in `OfflineServiceRuntimeContracts.kt` |
| **Acyclic dependency graph** | ✅ Domain ← Storage Contracts ← Core Contracts ← Application ← Testing |
| **SPEC-009 unidirectional integration** | ✅ SPEC-011 calls SPEC-009; SPEC-009 has no SPEC-011 imports |
| **No in-memory-only deduplication** | ✅ All dedup via `RpcRuntimeRepository`; Scenario M verifies across simulated reconnect |
| **Advertisement purely descriptive** | ✅ Verified in Scenario B: removing capability grant does not remove advertisement |
| **Offline operation** | ✅ No network, no DNS, no central registry in any module |

---

## 6. Security Invariant Audit

### Authorization Gateway

The enforcement order in `RpcExecutionServiceImpl.processInboundRequest()` is:

```
Frame validation → Service/Method lookup → SPEC-009 authorize() → ACCEPTED record → Handler execution → Result commit
```

**Verified:** Scenario G confirms the handler execution count remains zero when authorization is denied.
**Verified:** Scenario M confirms SPEC-009 is called exactly once for a given invocation (dedup path bypasses auth).

### Idempotency Command Safety

`NON_IDEMPOTENT_COMMAND` executions interrupted in `RUNNING` or `COMMITTING_RESULT` state are **never automatically re-executed**. Recovery sets state to `UNKNOWN_OUTCOME` and saves a durable result. Any subsequent re-delivery returns the `UNKNOWN_OUTCOME` result without invoking the handler.

**Verified:** Scenario K confirms zero handler executions after crash recovery.

### Deadline Expiration Safety

Invocations with expired deadlines are marked `EXPIRED` during recovery. The `EXPIRED` state is distinct from `FAILED` and `UNKNOWN_OUTCOME`, giving callers accurate information for retry decisions.

**Verified:** Scenario L confirms `EXPIRED` state with zero handler executions.

---

## 7. Dependency Graph

```
:core:service-discovery-domain          (leaf — zero internal deps)
         ↑
:storage:service-discovery-storage-contracts
:core:service-discovery-contracts
         ↑
:core:service-discovery-application     (integrates :core:security-contracts → SPEC-009)
         ↑
:core:service-discovery-testing         (acceptance suite)
```

No cycles detected. SPEC-009 integration is strictly unidirectional.

---

## 8. Decision

The implementation of SPEC-011 is **accepted**.

All 13 acceptance scenarios pass. All mandatory SPEC-011 functional and non-functional requirements are satisfied. All user-specified implementation constraints are verified.

**Accepted by:** Implementation Review
**Commit Hash:** `caf4d70`
