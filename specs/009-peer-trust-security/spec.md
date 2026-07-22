# SPEC-009 — Peer Trust, Authorization & Local Capability Security Model

**Estado:** Draft
**Tipo:** Core Security & Authorization Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-003, SPEC-004, SPEC-005, SPEC-006, SPEC-007, SPEC-008
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Conectividad requerida:** Ninguna infraestructura externa
**Internet requerido:** No
**Servidor requerido:** No
**Autoridad central requerida:** No
**Cuenta requerida:** No
**Transporte específico requerido:** No

---

# 1. Propósito

Definir e implementar el modelo local de seguridad mediante el cual cada nodo decide:

```text
WHO
MAY DO
WHAT
UNDER WHICH CONDITIONS
FOR HOW LONG
WITH WHICH LIMITS
AND HOW ACCESS IS REVOKED
```

El sistema debe permitir:
* distinguir identidad de confianza;
* distinguir autenticación de autorización;
* mantener trust state local;
* negar por defecto operaciones sensibles;
* definir capabilities explícitas;
* conceder capabilities a peers;
* limitar capabilities mediante constraints;
* autorizar cada operación sensible;
* persistir grants durablemente;
* soportar grants temporales;
* soportar grants de una sola operación;
* soportar revocación local;
* aplicar revocación inmediatamente a nuevas operaciones;
* invalidar autorización activa cuando corresponda;
* aplicar quotas por peer;
* aplicar rate limits por peer;
* limitar mensajes;
* limitar transferencias;
* limitar almacenamiento;
* limitar concurrencia;
* proteger contra confused deputy;
* proteger contra capability escalation;
* producir decisiones explicables;
* generar audit events;
* sobrevivir a process death;
* funcionar completamente offline;
* ser independiente del transporte;
* ser independiente de la aplicación concreta;
* ser testeable sin radios ni hardware real.

La SPEC define la transición:

```text
AUTHENTICATED PEER
        │
        ▼
LOCAL TRUST LOOKUP
        │
        ▼
CAPABILITY RESOLUTION
        │
        ▼
POLICY EVALUATION
        │
        ├───────────────┐
        │               │
        ▼               ▼
      DENY             ALLOW
        │               │
        │               ▼
        │         RESOURCE RESERVATION
        │               │
        │               ▼
        │        OPERATION EXECUTION
        │               │
        │               ▼
        │          USAGE ACCOUNTING
        │               │
        └───────┬───────┘
                │
                ▼
             AUDIT
```

---

# 2. Problema fundamental

Después de SPEC-008 existe:
```text
Persistent Node Identity
Authenticated Peer Discovery
Secure Sessions
Stable Logical Links
Durable Object Transfer
Durable Offline Messaging
```

Pero todavía no existe una respuesta formal para:
```text
¿Aceptamos mensajes de cualquier NodeId autenticado?
¿Permitimos transferencias de objetos automáticamente?
¿Cuántos bytes puede almacenar un peer en nuestro dispositivo?
¿Cuántos mensajes puede enviarnos?
¿Qué messageTypes puede utilizar?
¿Puede solicitar procesamiento de comandos?
¿Puede consumir recursos indefinidamente?
¿Puede conservar permisos para siempre?
¿Podemos confiar temporalmente en un peer?
¿Podemos autorizar una sola operación?
¿Podemos revocar permisos?
¿Qué ocurre con operaciones activas después de revocar?
¿Cómo evitamos que un peer use una capability para otra operación?
¿Cómo explicamos por qué una operación fue permitida o rechazada?
```

La autenticación solo demuestra:
`THIS PEER CONTROLS THE PRIVATE KEY CORRESPONDING TO THIS NODE IDENTITY`

No demuestra:
`THIS PEER IS TRUSTED / MAY SEND MESSAGES / MAY STORE DATA / MAY CONSUME UNLIMITED RESOURCES`

Por tanto:
> Toda operación sensible debe atravesar una frontera explícita de autorización local.

---

# 3. Objetivo arquitectónico

Construir:
```text
LOCAL SECURITY AUTHORITY
```
que permita:
Trust Classification, Capability Definition, Capability Grant, Capability Constraints, Authorization Evaluation, Resource Reservation, Usage Accounting, Rate Limiting, Quota Enforcement, Temporary Authorization, One-Shot Authorization, Revocation, Active Operation Invalidation, Security Audit, Crash Recovery
sin depender de Internet, Cloud IdP, OAuth, ACL Server, CA, o hardware específico.

---

# 4. Principios Fundamentales

1. **IDENTITY ≠ AUTHENTICATION ≠ TRUST ≠ AUTHORIZATION**
2. **AUTHENTICATED DOES NOT IMPLY AUTHORIZED**
3. **DEFAULT DENY**
4. **CAPABILITY GRANT MUST BE EXPLICIT + SCOPED + CONSTRAINED + REVOCABLE**
5. **AUTHORIZATION DECISION MUST PRECEDE PROTECTED SIDE EFFECT**
6. **ALLOW DECISION ≠ UNLIMITED FUTURE AUTHORITY**
7. **TRUST IS LOCAL**
8. **TRUST MUST NOT BE TRANSITIVE BY DEFAULT**
9. **CAPABILITY POSSESSION MUST NOT IMPLY CAPABILITY DELEGATION**
10. **SECURITY DECISIONS MUST BE EXPLAINABLE**

---

# 5. Objetivos Funcionales y Fuera de Alcance

## Objetivos Funcionales
1. Registro local de peers & trust state classification.
2. Capability definition, grants & scoped constraints (time, usage, resource).
3. Authorization evaluation with default deny & precedence.
4. Resource reservation, usage accounting, rate limits, quotas, concurrency limits.
5. Inbound message & object transfer authorization & resume authorization.
6. Grant, trust & peer revocation/blocking with immediate active operation invalidation.
7. Durability across process death, recovery, explanation & append-only security audit log.

## Fuera de Alcance
Centralized identity, OAuth/OIDC, Cloud ACL, CA/PKI, transitive trust, ZK credentials, hardware attestation, OS-level sandbox management.

---

# 6. Bounded Context: `LocalPeerSecurity`

## Entidades:
- `PeerSecurityProfile`
- `CapabilityDefinition`
- `CapabilityGrant`
- `AuthorizationDecision`
- `AuthorizationLease`
- `ResourceReservation`
- `UsageCounter`
- `RateLimitBucket`
- `SecurityAuditEvent`
- `PolicySnapshot`

## Value Objects:
- `CapabilityId`, `GrantId`, `DecisionId`, `AuthorizationLeaseId`, `ReservationId`, `PolicyId`, `PolicyVersion`, `TrustState`, `GrantState`, `AuthorizationEffect`, `AuthorizationReason`, `ResourceType`, `OperationType`, `ConstraintSet`, `UsageDimension`, `RevocationReason`

## Servicios:
- `PeerTrustService`
- `CapabilityRegistryService`
- `CapabilityGrantService`
- `AuthorizationService`
- `PolicyEvaluationService`
- `ResourceReservationService`
- `UsageAccountingService`
- `RateLimitService`
- `QuotaService`
- `AuthorizationLeaseService`
- `RevocationService`
- `SecurityAuditService`
- `SecurityRecoveryService`
- `AuthorizationGateway`
- `SecurityRepository`
- `LocalPeerSecurityCore`

---

# 7. Trust State Machine & Blocking

States: `UNKNOWN`, `OBSERVED`, `PENDING_APPROVAL`, `TRUSTED`, `RESTRICTED`, `BLOCKED`.

Transitions:
- `UNKNOWN` → `OBSERVED`
- `OBSERVED` → `PENDING_APPROVAL`, `TRUSTED`, `RESTRICTED`, `BLOCKED`
- `PENDING_APPROVAL` → `TRUSTED`, `RESTRICTED`, `BLOCKED`
- `TRUSTED` → `RESTRICTED`, `OBSERVED`, `BLOCKED`
- `RESTRICTED` → `TRUSTED`, `OBSERVED`, `BLOCKED`
- `BLOCKED` → `OBSERVED`

`BLOCKED` rejects all inbound offers, data, transfers, reconciliation, and future requests immediately.

---

# 8. Authorization Pipeline & Grant Evaluation

Pipeline:
1. Strict Input Validation
2. Authenticated Peer Validation
3. Peer Security Profile Lookup (BLOCKED → DENY)
4. Capability Registry Lookup (UNKNOWN → DENY)
5. Load Policy Snapshot
6. Load Matching Deny Grants (MATCH → DENY)
7. Load Matching Allow Grants (NONE → DENY)
8. Evaluate Trust Requirements
9. Evaluate Constraints (MessageType, PayloadSize, ObjectSize, MediaType, StorageBytes, MaxOps, MaxConcurrent, RateLimit, TimeWindow, ValidUntil, Encounter, ApplicationNamespace, ResourceId)
10. Evaluate Rate Limits
11. Evaluate Quotas
12. Evaluate Concurrency
13. Create Decision & Log Audit Event

Explicit DENY grants override ALLOW grants. Default is DENY.

---

# 9. Resource Accounting, Quotas & Rate Limits

- `AVAILABLE = LIMIT - CONSUMED - RESERVED`
- Operations reserve capacity (`ResourceReservation`) before execution.
- Usage committed via `consumeUsage(leaseId, actualUsage)` upon completion.
- Fixed Window Rate Limiter v1 (extensible to Token/Leaky bucket).
- Quotas track inbox bytes, object bytes, active transfers, active message deliveries, dead-letter count.

---

# 10. Leases, Revocation & Crash Recovery

- Long-running transfers acquire `AuthorizationLease`. Validated periodically.
- `revocationVersion` incremented on grant revocation, trust downgrade, or peer block.
- Operations stopped at safe checkpoints (`STOP_AT_NEXT_CHECKPOINT`).
- Fail-closed: state unavailable → DENY.
- Crash recovery reconciles active reservations, counters, leases, and grant states from durable storage.
