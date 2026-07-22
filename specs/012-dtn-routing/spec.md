# SPEC-012 — Offline Multi-Hop Routing, Store-Carry-Forward & Delay-Tolerant Delivery

**Estado:** Draft
**Tipo:** Core Routing, Relay & Delay-Tolerant Networking Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-002, SPEC-003, SPEC-004, SPEC-005, SPEC-006, SPEC-007, SPEC-008, SPEC-009, SPEC-010
**Integración futura:** SPEC-011 Offline Service Runtime
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Conectividad requerida:** Ninguna infraestructura externa
**Internet requerido:** No
**Servidor requerido:** No
**Broker requerido:** No
**Router central requerido:** No
**DNS requerido:** No
**GPS requerido:** No
**Transporte específico requerido:** No

---

# 1. Propósito

Definir e implementar el subsistema mediante el cual un nodo puede:

* crear unidades de entrega multi-hop;
* enviar datos a destinos no conectados directamente;
* almacenar envelopes temporalmente;
* transportar físicamente envelopes entre encuentros;
* reenviar envelopes;
* seleccionar próximos hops;
* aprender rutas mediante encuentros;
* mantener hints de reachability;
* mantener historial de forwarding;
* prevenir loops;
* limitar cantidad de hops;
* limitar tiempo de vida;
* limitar copias;
* soportar routing directo;
* soportar epidemic routing controlado;
* soportar spray-and-wait;
* soportar routing basado en encounter history;
* soportar routing basado en destination predictability;
* funcionar sin topología global;
* funcionar con conectividad intermitente;
* funcionar con particiones prolongadas;
* soportar nodos móviles;
* preservar confidencialidad end-to-end;
* impedir que relays lean payloads;
* autenticar envelopes;
* detectar modificación;
* detectar replay;
* detectar duplicate delivery;
* soportar custody transfer opcional;
* soportar custody refusal;
* soportar custody expiration;
* soportar delivery receipts;
* soportar receipts diferidos;
* soportar garbage collection;
* aplicar cuotas;
* aplicar backpressure;
* priorizar tráfico;
* proteger contra resource exhaustion;
* sobrevivir a process death;
* integrarse con SPEC-007 Object Transfer;
* integrarse con SPEC-008 Offline Messaging;
* integrarse con SPEC-009 Authorization;
* integrarse con SPEC-010 Peer Identity;
* producir audit events;
* producir métricas;
* ser testeable sin radios ni hardware real.

La SPEC define la transición:

```text
DIRECT PEER DELIVERY
        │
        ▼
DESTINATION NOT PRESENT
        │
        ▼
CREATE DTN ENVELOPE
        │
        ▼
END-TO-END ENCRYPT PAYLOAD
        │
        ▼
STORE LOCALLY
        │
        ▼
ENCOUNTER RELAY NODE
        │
        ▼
EXCHANGE ROUTING SUMMARY
        │
        ▼
SELECT FORWARDING CANDIDATES
        │
        ▼
TRANSFER ENVELOPE
        │
        ▼
RELAY STORES ENVELOPE
        │
        ▼
RELAY PHYSICALLY MOVES
        │
        ▼
NEW ENCOUNTER
        │
        ▼
FORWARD AGAIN
        │
        ▼
DESTINATION RECEIVES
        │
        ▼
END-TO-END DECRYPT
        │
        ▼
DURABLE DELIVERY COMMIT
        │
        ▼
DELIVERY RECEIPT PROPAGATION
        │
        ▼
NETWORK GARBAGE COLLECTION
```

---

# 2. Problema fundamental

Después de SPEC-011 existe:

```text
Persistent Node Identity
Authenticated Discovery
Secure Sessions
Hybrid Logical Links
Durable Object Transfer
Durable Messaging
Trust
Capabilities
Human Identity Verification
Offline Service Runtime
```

Pero todas las comunicaciones todavía dependen de `SOURCE MEETS DESTINATION`. No existe conexión directa cuando el destino no está en rango.

Por tanto:
> El protocolo necesita una capa Delay-Tolerant Networking capaz de almacenar, transportar físicamente y reenviar unidades cifradas entre nodos oportunistas.

---

# 3. Objetivo arquitectónico

Construir `OFFLINE DELAY-TOLERANT ROUTING CORE` sin depender de Internet, tablas globales, router central, GPS, o transportes específicos.

---

# 4. Principios Arquitectónicos

1. **RELAY NODE MUST NOT NEED PAYLOAD PLAINTEXT**: Un relay almacena y reenvía ciphertext sin descifrar la aplicación.
2. **NO END-TO-END PATH DOES NOT IMPLY NO DELIVERY**: La movilidad física y encuentros oportunistas completan la ruta.
3. **ROUTING DECISION MUST USE BOUNDED LOCAL KNOWLEDGE**: Sin información global de la red.
4. **FORWARDING MUST NOT CREATE UNBOUNDED REPLICATION**: Hop Limit, TTL, Copy Budget, Quotas.
5. **RECEIVED ENVELOPE ≠ ACCEPTED CUSTODY**: Recibir no implica responsabilidad durable.
6. **CUSTODY ACK MUST REQUIRE DURABLE ENVELOPE COMMIT**: Confirmación sólo tras commit en persistencia.
7. **DELIVERY RECEIPT MUST NOT BE TRUSTED WITHOUT AUTHENTICATED DESTINATION EVIDENCE**: Firma criptográfica del destino.
8. **TTL EXPIRATION MUST EVENTUALLY REMOVE ENVELOPE STATE**: Expiración limpia estado.
9. **DUPLICATE DELIVERY MUST NOT CREATE DUPLICATE APPLICATION EFFECTS**: Deduplicación durable.
10. **ROUTING METADATA MUST REVEAL ONLY WHAT IS NECESSARY**: Minimizar metadata expuesta.
11. **RELAYING IS A RESOURCE-CONSUMING CAPABILITY**: Sujeto a autorización SPEC-009 y cuotas.
12. **ROUTE PREDICTION IS A HINT, NOT A GUARANTEE**.

---

# 5. Módulos & Capas

- **Stage 1 (Domain)**: `:core:dtn-routing-domain`
  - Value Objects: `EnvelopeId`, `PayloadId`, `RoutingStrategyId`, `EnvelopePriority`, `EnvelopeState`, `CustodyState`, `DeliveryState`, `HopCount`, `HopLimit`, `CopyBudget`, `EnvelopeTtl`, `PayloadCiphertextHash`, `ReceiptId`, `PredictabilityScore`
  - Entities: `DtnEnvelope`, `StoredEnvelope`, `ForwardingRecord`, `CustodyRecord`, `DeliveryReceipt`, `DeliveredEnvelopeTombstone`, `EncounterRoutingState`, `DestinationPredictabilityRecord`, `RoutingSummary`, `RoutingTransferPlan`
  - Canonical Encoding & Domain Tag `OFFLINE_DTN_E2E_ENVELOPE_V1`
  - Error Taxonomy: `DtnRoutingError`
- **Stage 2 (Contracts)**: `:storage:dtn-routing-storage-contracts` & `:core:dtn-routing-contracts`
  - Storage Interface: `DtnRoutingRepository`
  - Core Facades & Strategy Contracts: `RoutingStrategy`, `ForwardingEngine`, `CustodyManager`, `DeliveryReceiptService`, `DelayTolerantRoutingCore`
- **Stage 3 (Application)**: `:core:dtn-routing-application`
  - Services: `DtnEnvelopeServiceImpl`, `EndToEndEnvelopeCryptoServiceImpl`, `RoutingSummaryServiceImpl`, `ForwardingEngineImpl`, `DirectRoutingStrategyImpl`, `ControlledEpidemicRoutingStrategyImpl`, `SprayAndWaitRoutingStrategyImpl`, `DestinationPredictabilityServiceImpl`, `CustodyManagerImpl`, `DeliveryReceiptServiceImpl`, `DuplicateSuppressionServiceImpl`, `DtnGarbageCollectorImpl`, `DtnRecoveryServiceImpl`, `DelayTolerantRoutingCoreImpl`
- **Stage 4 (Testing)**: `:core:dtn-routing-testing`
  - Test Doubles & Fixtures
  - Acceptance Suite (Scenarios A–N)
