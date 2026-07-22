# SPEC-011 — Offline Service Discovery, Capability Advertisement & Durable Local RPC Protocol

**Estado:** Draft  
**Tipo:** Core Distributed Services & RPC Specification  
**Prioridad:** P0 — Fundacional  
**Dependencias:** SPEC-003, SPEC-004, SPEC-005, SPEC-006, SPEC-007, SPEC-008, SPEC-009, SPEC-010  
**Plataforma inicial:** Android  
**Alcance del Core:** Multiplataforma por diseño  
**Conectividad requerida:** Ninguna infraestructura externa  
**Internet requerido:** No  
**Servidor requerido:** No  
**Broker requerido:** No  
**DNS requerido:** No  
**Service Registry central requerido:** No  
**Transporte específico requerido:** No  

---

# 1. Propósito

Definir e implementar el subsistema mediante el cual un nodo puede:

* registrar servicios locales;
* describir contratos versionados;
* anunciar servicios disponibles;
* descubrir servicios ofrecidos por peers;
* diferenciar presencia de servicio de autorización para usarlo;
* mantener un catálogo local de servicios remotos;
* detectar aparición, actualización y desaparición de servicios;
* negociar versiones de contrato;
* consultar metadata de servicios;
* invocar operaciones remotas;
* ejecutar RPC unary request/response;
* soportar comandos sin respuesta de aplicación;
* soportar resultados pequeños inline;
* soportar resultados grandes mediante Object Transfer;
* soportar parámetros grandes mediante Object Transfer;
* persistir requests antes de transmitirlos;
* persistir resultados antes de confirmarlos;
* soportar reintentos;
* soportar idempotencia;
* detectar duplicados;
* soportar deadlines;
* soportar expiración;
* soportar cancelación;
* soportar ejecución en curso;
* soportar respuestas tardías;
* soportar desconexiones;
* soportar reencuentros;
* soportar cambio de transporte;
* sobrevivir a process death;
* integrarse con autorización de SPEC-009;
* integrarse con identidad humana de SPEC-010;
* producir audit events;
* producir métricas;
* funcionar completamente offline;
* ser testeable sin radios ni hardware real.

La SPEC define la transición:

```text
TRUSTED / AUTHORIZED PEER

            │

            ▼

REMOTE SERVICE ADVERTISEMENT

            │

            ▼

LOCAL REMOTE SERVICE CATALOG

            │

            ▼

CONTRACT NEGOTIATION

            │

            ▼

RPC INVOCATION CREATED DURABLY

            │

            ▼

AUTHORIZATION

            │

            ▼

REQUEST DELIVERY

            │

            ▼

DUPLICATE / IDEMPOTENCY CHECK

            │

            ▼

REMOTE EXECUTION

            │

      ┌─────┼──────────────┐
      │     │              │
      ▼     ▼              ▼

   RESULT  ERROR       LARGE OBJECT
      │     │              │
      └─────┴──────┬───────┘
                   │
                   ▼

          DURABLE RESPONSE COMMIT

                   │

                   ▼

              ACKNOWLEDGEMENT
```

---

# 2. Problema fundamental

Después de SPEC-010 existe:

```text
Persistent Node Identity
Authenticated Discovery
Secure Sessions
Hybrid Logical Links
Durable Object Transfer
Durable Offline Messaging
Local Trust
Capability Authorization
Human Identity Verification
Persistent Peer Directory
```

Pero todavía falta responder formalmente:

```text
¿Qué puede hacer un nodo además de enviar mensajes?
¿Cómo anuncia un nodo que ofrece una función?
¿Cómo descubre otro nodo esa función sin servidor?
¿Cómo sabe qué versión del servicio está disponible?
¿Cómo sabe qué operaciones soporta?
¿Cómo representa parámetros y resultados?
¿Cómo invoca una operación?
¿Qué ocurre si se corta la conexión?
¿Qué ocurre si el request llega dos veces?
¿Qué ocurre si la operación se ejecutó pero se perdió la respuesta?
¿Cómo evitamos ejecutar dos veces una operación no idempotente?
¿Cómo se cancela una operación?
¿Qué significa deadline en un sistema desconectado?
¿Cómo se devuelven resultados grandes?
¿Cómo se autoriza cada método?
¿Cómo evitamos que anunciar un servicio implique permitir su uso?
¿Cómo recuperamos operaciones después de process death?
```

Messaging permite:
`DELIVER DATA TO PEER`

RPC requiere:
`REQUEST REMOTE OPERATION + CONTROL EXECUTION SEMANTICS + RETURN RESULT OR ERROR`

Por tanto:
> El protocolo necesita una capa formal de servicios, contratos e invocaciones remotas durables sobre el Core existente.

---

# 3. Objetivo arquitectónico

Construir: `OFFLINE DISTRIBUTED SERVICE RUNTIME` que permita:
Local Service Registry, Service Contracts, Service Advertisement, Remote Service Discovery, Remote Service Catalog, Contract Version Negotiation, Method Discovery, Capability Mapping, Durable RPC Invocation, Durable RPC Execution, Unary Request/Response, One-Way Commands, Large Parameter References, Large Result References, Idempotency, Duplicate Suppression, Execution Deduplication, Deadlines, Expiration, Cancellation, Late Result Handling, Retry, Reencounter Recovery, Transport Switch Survival, Process Death Recovery, Authorization, Audit, Observability without relying on external infrastructure.

---

# 4. Principios Fundamentales

1. **SERVICE DISCOVERY ≠ SERVICE AUTHORIZATION**: Advertising a service does not grant invocation authorization. All invocations must cross SPEC-009.
2. **SERVICE ADVERTISEMENT IS UNTRUSTED CLAIMED AVAILABILITY**: Until peer authenticated, advertisement validated, contract recognized, authorization evaluated.
3. **RPC REQUEST MUST HAVE STABLE INVOCATION IDENTITY**: Retries, reencounters, transport switches, or secure session replacements MUST NOT change `InvocationId`.
4. **DELIVERY RETRY ≠ EXECUTION RETRY**: Resending bytes must not cause duplicate handler execution.
5. **AT-MOST-ONCE EXECUTION REQUIRES DURABLE DEDUPLICATION STATE**: Must survive process death.
6. **ACKNOWLEDGED RESULT MUST HAVE BEEN DURABLY COMMITTED**: No ACK before result persistence.
7. **DEADLINE ≠ CONNECTIVITY TIMEOUT**: Deadline defines semantic validity duration for offline execution.
8. **CANCEL REQUEST ≠ GUARANTEED EXECUTION ROLLBACK**: Cancellation is cooperative (`TOO_LATE_TO_CANCEL`).
9. **LARGE PAYLOADS MUST USE SPEC-007 OBJECT TRANSFER**: RPC delegates large parameter/result handling to SPEC-007.
10. **SERVICE CONTRACT VERSION MUST BE EXPLICIT**: Major.Minor matching rule; contract hashes validated.
11. **REMOTE EXECUTION MUST BE AUTHORIZED BEFORE APPLICATION HANDLER SIDE EFFECT**.
12. **SERVICE AVAILABILITY IS TEMPORAL; SERVICE IDENTITY IS LOGICAL**.

---

# 5. Scope & Subsystems

* **In-Scope**: Local service & contract registry, advertisement manager, remote service catalog, contract version negotiation, durable RPC invocation store & state machine, durable RPC execution store & state machine, duplicate suppression & idempotency checks, deadline & cancellation handling, SPEC-007 object reference integration, SPEC-009 capability authorization mapping, crash recovery & reconciliation.
* **Out-of-Scope**: Internet service discovery, DNS, mDNS, gRPC transport, streaming RPCs, distributed consensus/transactions, dynamic code downloading.

---

# 6. Verification & Acceptance Scenarios

The implementation will be validated against 11 Acceptance Scenarios (A through K):
* **Scenario A (Service Registration & Advertisement)**: Register local service and broadcast authenticated advertisement.
* **Scenario B (Remote Service Discovery & Catalog Update)**: Receive advertisement, validate payload, and populate remote catalog as `AVAILABLE`.
* **Scenario C (Contract Version Negotiation)**: Match client requirement Major.Minor and validate `contractHash`.
* **Scenario D (Unary RPC Execution & Result Persistence)**: Send request, authorize via SPEC-009, execute handler, commit result durably, return response and ACK.
* **Scenario E (One-Way Command Execution)**: Send command, validate acceptance ACK, execute asynchronously.
* **Scenario F (Idempotency & Duplicate Suppression)**: Re-send request with same `InvocationId` or `IdempotencyKey` $\to$ returns cached result without re-executing handler.
* **Scenario G (Large Parameter / Result Reference)**: Large request/result payload uses SPEC-007 `ObjectReference`.
* **Scenario H (Deadline & Expiration Handling)**: Stale request past deadline rejected with `DEADLINE_EXCEEDED` / `INVOCATION_EXPIRED`.
* **Scenario I (Cooperative Cancellation)**: Cancel request during execution $\to$ transitions through `CANCELLATION_REQUESTED` and returns `CANCELLED`.
* **Scenario J (Disconnect & Reencounter Resumption)**: Invocation paused during link loss resumes cleanly on reencounter without creating duplicate invocation.
* **Scenario K (Crash Recovery & Non-Idempotent Safety)**: Boot recovery expires past sessions, recovers pending invocations, and marks interrupted non-idempotent executions as `UNKNOWN_OUTCOME` without blind re-execution.
