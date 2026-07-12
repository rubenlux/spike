# SPEC-006 — Hybrid Transport Orchestration Protocol

**Estado:** Draft
**Tipo:** Core Transport Architecture Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-001, SPEC-002, SPEC-003, SPEC-004, SPEC-005
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Transportes iniciales:** BLE + Wi-Fi Direct
**Conectividad requerida:** Ninguna infraestructura externa
**Internet requerido:** No
**Router requerido:** No
**Servidor requerido:** No
**Cuenta requerida:** No

---

# 1. Propósito

Definir e implementar el protocolo de orquestación híbrida mediante el cual dos nodos autenticados y conectados por una `Secure Session` pueden:

* anunciar capacidades de transporte;
* negociar transportes compatibles;
* evaluar las condiciones actuales del enlace;
* seleccionar un transporte de control;
* seleccionar un transporte de datos;
* mantener BLE como plano de descubrimiento y control;
* establecer Wi-Fi Direct como plano de datos de alto rendimiento;
* resolver determinísticamente roles Wi-Fi Direct;
* vincular criptográficamente el nuevo enlace físico con la sesión segura existente;
* impedir transport hijacking;
* validar que el peer presente en Wi-Fi Direct es el mismo peer autenticado previamente;
* activar el nuevo transporte;
* permitir fallback a BLE;
* detectar degradación del enlace;
* soportar transport upgrade y downgrade;
* separar la vida del transporte físico de la vida de la operación lógica;
* exponer un `Logical Link` estable a las capas superiores;
* producir eventos, métricas y evidencia observable;
* funcionar completamente sin Internet.

La SPEC define la transición:

```text
AUTHENTICATED ENCOUNTER

        │

        ▼

SECURE SESSION OVER BLE

        │

        ▼

TRANSPORT CAPABILITY EXCHANGE

        │

        ▼

TRANSPORT POLICY EVALUATION

        │

        ▼

WIFI DIRECT ROLE RESOLUTION

        │

        ▼

WIFI DIRECT LINK ESTABLISHMENT

        │

        ▼

CRYPTOGRAPHIC TRANSPORT BINDING

        │

        ▼

WIFI DIRECT DATA PLANE ACTIVE

        │

        ├───────────────┐
        │               │
        ▼               ▼

BLE CONTROL PLANE   WIFI DIRECT DATA PLANE

        │               │
        └───────┬───────┘
                │
                ▼

          LOGICAL LINK
```

---

# 2. Problema fundamental

Después de SPEC-005 existe:

```text
Nearby Peer Discovery

+

Decentralized Identity

+

Mutual Authentication

+

Encounter

+

Secure Session

+

Encrypted Control Channel
```

Pero el sistema todavía depende del transporte utilizado para establecer la comunicación inicial.

BLE posee ventajas importantes:

```text
Low Energy

Efficient Discovery

Broad Device Support

Small Control Messages
```

Pero también posee limitaciones:

```text
Low Throughput

Small MTU

Fragmentation Overhead

High Transfer Latency

Unsuitable For Large Objects
```

Wi-Fi Direct ofrece:

```text
Higher Throughput

Larger Payloads

IP-Based Data Channels

Better Bulk Transfer Performance
```

Pero introduce nuevos problemas:

```text
Peer Role Negotiation

Connection Establishment Cost

Higher Energy Consumption

Transport Identity Confusion

Link Hijacking

Simultaneous Group Creation

Connection Failure

Link Degradation

Transport Switching

Fallback
```

Por tanto:

> El sistema necesita una capa de orquestación capaz de utilizar distintos transportes físicos sin permitir que las capas superiores dependan directamente de ellos.

Y además:

> Establecer una conexión Wi-Fi Direct no demuestra que el dispositivo conectado sea el mismo peer autenticado mediante BLE.

El nuevo transporte debe quedar criptográficamente vinculado al contexto seguro existente.

---

# 3. Objetivo arquitectónico

Construir:

```text
HYBRID TRANSPORT ORCHESTRATOR
```

que permita:

```text
BLE

Discovery Plane
Control Plane
Fallback Data Plane
```

y:

```text
Wi-Fi Direct

High-Throughput Data Plane
```

exponiendo a capas superiores:

```text
LogicalLink
```

independiente del transporte físico activo.

---

# 4. Objetivos funcionales

Al finalizar esta SPEC, dos nodos deben poder:

1. descubrirse mediante BLE;

2. autenticarse;

3. establecer Secure Session;

4. intercambiar capacidades de transporte;

5. detectar soporte mutuo de Wi-Fi Direct;

6. decidir si realizar upgrade;

7. resolver roles determinísticamente;

8. iniciar Wi-Fi Direct;

9. establecer un canal de datos;

10. autenticar el nuevo enlace;

11. vincularlo a la Secure Session existente;

12. activar Wi-Fi Direct como Data Plane;

13. mantener BLE como Control Plane;

14. detectar degradación o pérdida de Wi-Fi Direct;

15. ejecutar fallback;

16. continuar la operación lógica mediante otro transporte;

17. exponer estado observable a SDK y capas superiores.

---

# 5. Resultado esperado

```text
HYBRID TRANSPORT CORE
────────────────────────────────────

Transport Capability Model

Transport Capability Exchange

Transport Policy Engine

Transport Selection

Control Plane Selection

Data Plane Selection

Transport Upgrade Protocol

Transport Downgrade Protocol

Wi-Fi Direct Role Resolution

Transport Establishment Coordination

Transport Binding Token

Transport Binding Handshake

Transport Hijacking Protection

Logical Link Model

Logical Link Lifecycle

Physical Link Model

Physical Link Lifecycle

Transport Health Model

Transport Degradation Detection

Fallback Orchestration

Transport Switching

Split Control/Data Plane

Transport Events

Transport Metrics

Transport Contracts

Fake Transport Test Harness
```

Adaptadores iniciales:

```text
Android BLE Transport Adapter

Android Wi-Fi Direct Transport Adapter
```

---

# 6. Principio arquitectónico fundamental

```text
LOGICAL OPERATION ≠ LOGICAL LINK ≠ PHYSICAL TRANSPORT
```

Definiciones:

```text
LOGICAL OPERATION

Transfer
Message
Synchronization
Future Protocol Operation


LOGICAL LINK

Stable authenticated communication abstraction
between two nodes.


PHYSICAL TRANSPORT

BLE
Wi-Fi Direct
Future transport.
```

Las capas superiores deben depender de:

```text
LogicalLink
```

No de:

```text
BluetoothGatt

WifiP2pManager

Socket

NetworkInterface
```

---

# 7. Segundo principio fundamental

```text
TRANSPORT CONNECTED ≠ TRANSPORT AUTHENTICATED
```

Una conexión física nueva debe permanecer:

```text
UNBOUND
```

hasta completar `Transport Binding`.

---

# 8. Tercer principio fundamental

```text
TRANSPORT FAILURE ≠ OPERATION FAILURE
```

La pérdida de Wi-Fi Direct no debe cancelar automáticamente:

```text
Transfer

Object

Message

Synchronization
```

La capa superior debe poder continuar cuando exista otro transporte compatible.

---

# 9. Principios no negociables

## 9.1 Offline absoluto

Toda la orquestación MUST funcionar sin Internet.

---

## 9.2 Transport Independence

Hybrid Transport Core MUST NOT importar APIs Android.

---

## 9.3 BLE First Bootstrap

La implementación inicial utilizará BLE para:

```text
Discovery

Initial Authentication

Secure Session Bootstrap

Transport Negotiation

Transport Upgrade Coordination
```

---

## 9.4 Wi-Fi Direct High-Throughput Plane

Wi-Fi Direct será el transporte preferido inicialmente para bulk data cuando esté disponible y sea conveniente.

---

## 9.5 Explicit Transport Binding

Todo nuevo transporte físico MUST autenticarse criptográficamente antes de transportar información de aplicación.

---

## 9.6 No Identity By Address

El sistema MUST NOT identificar peers mediante:

```text
BLE MAC

Wi-Fi MAC

IP Address

Socket Address

SSID

Device Name
```

---

## 9.7 Secure Control Plane

Toda negociación de transporte MUST ocurrir dentro de una Secure Session.

---

## 9.8 Deterministic Role Resolution

Los roles Wi-Fi Direct MUST resolverse mediante política determinista.

---

## 9.9 Explicit Activation

Un transporte establecido físicamente MUST NOT convertirse automáticamente en Data Plane activo.

---

## 9.10 Explicit Fallback

Fallback MUST ser una transición formal del protocolo.

---

## 9.11 Bounded Resource Usage

Intentos, conexiones, upgrades y transportes activos MUST poseer límites configurables.

---

## 9.12 Observable State Machines

Cada cambio relevante MUST producir eventos y métricas.

---

# 10. Fuera de alcance

Esta SPEC no implementará:

* multi-hop;
* routing;
* mesh networking;
* store-carry-forward;
* relay;
* Internet fallback;
* WebRTC;
* Wi-Fi Aware;
* Nearby Connections;
* NFC;
* LoRa;
* cellular transport;
* QUIC;
* Multipath TCP;
* simultaneous striped transfer;
* congestion control multipath;
* Forward Error Correction;
* transport bonding;
* bandwidth aggregation;
* network coding;
* message synchronization;
* Transfer Scheduler completo;
* chunk scheduling avanzado;
* background execution definitivo.

---

# 11. Arquitectura

```text
┌──────────────────────────────────────────────┐
│                APPLICATION / SDK             │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│                 LOGICAL LINK                 │
│                                              │
│ sendControl()                                │
│ sendData()                                   │
│ getState()                                   │
│ getActiveTransports()                        │
│ requestUpgrade()                             │
│ requestFallback()                            │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│         HYBRID TRANSPORT ORCHESTRATOR        │
│                                              │
│ Capability Exchange                          │
│ Policy Evaluation                            │
│ Transport Selection                          │
│ Role Resolution                              │
│ Establishment Coordination                   │
│ Binding                                      │
│ Activation                                   │
│ Health Evaluation                            │
│ Switching                                    │
│ Fallback                                     │
└──────────────┬───────────────┬───────────────┘
               │               │
               ▼               ▼
┌────────────────────┐ ┌──────────────────────┐
│ BLE TRANSPORT      │ │ WIFI DIRECT          │
│ ADAPTER            │ │ TRANSPORT ADAPTER    │
│                    │ │                      │
│ Discovery          │ │ Group Creation       │
│ Control            │ │ Peer Connection      │
│ Fallback Data      │ │ Data Socket          │
└────────────────────┘ └──────────────────────┘
```

---

# 12. Bounded Context

La SPEC define:

```text
HybridTransport
LogicalLink
PhysicalLink
TransportBinding
TransportHealth
```

Entidades:

```text
LogicalLink

PhysicalLink

TransportUpgradeAttempt

TransportBindingSession

TransportSwitch

TransportEvent
```

Value Objects:

```text
LogicalLinkId

PhysicalLinkId

TransportType

TransportRole

TransportCapabilitySet

TransportPolicyDecision

TransportBindingId

TransportBindingToken

TransportEpoch

TransportPriority

TransportHealthSnapshot

TransportFailureReason
```

Servicios:

```text
TransportCapabilityService

TransportPolicyService

TransportSelectionService

WifiDirectRoleResolutionService

TransportEstablishmentService

TransportBindingService

LogicalLinkService

TransportActivationService

TransportHealthService

TransportFallbackService

TransportSwitchService

TransportRecoveryService
```

---

# 13. Transport Types

Inicialmente:

```text
BLE

WIFI_DIRECT
```

Futuros:

```text
WIFI_AWARE

LAN

USB

NFC

WEBRTC

CUSTOM
```

El Core debe permitir extensión sin modificar las invariantes fundamentales.

---

# 14. Transport Capability Model

```text
TransportCapability
────────────────────────────────

transportType

protocolVersion

roleCapabilities

maxFrameSize

estimatedThroughputClass

energyCostClass

setupLatencyClass

supportsControlPlane

supportsDataPlane

supportsBidirectionalTraffic

supportsPersistentConnection

adapterCapabilities
```

---

# 15. Capability Exchange

Después de Secure Session:

```text
NODE A                             NODE B

   │                                 │
   │──── TRANSPORT_CAPABILITIES ────►│
   │                                 │
   │◄─── TRANSPORT_CAPABILITIES ─────│
```

El intercambio ocurre cifrado mediante SPEC-005.

---

# 16. TRANSPORT_CAPABILITIES

```text
TransportCapabilitiesFrame
────────────────────────────────

protocolVersion

logicalLinkId

nodeId

supportedTransports[]

transportCapabilities[]

policyHints

capabilityNonce
```

`policyHints` no son órdenes.

Son información para decisión local.

---

# 17. Capability Validation

El receptor debe:

1. validar frame size;

2. parsear estrictamente;

3. validar protocolVersion;

4. verificar nodeId contra peer autenticado;

5. rechazar transportes duplicados inválidos;

6. validar límites;

7. almacenar snapshot;

8. ejecutar policy evaluation.

---

# 18. LogicalLink

```text
LogicalLink
────────────────────────────────

logicalLinkId

localNodeId

remoteNodeId

encounterId

secureSessionId

state

controlTransportId

dataTransportId

availableTransportIds[]

transportEpoch

createdAt

lastActivityAt

failureReason
```

---

# 19. LogicalLinkId

Formato recomendado:

```text
UUIDv7
```

No debe equivaler a:

```text
EncounterId

SecureSessionId

PhysicalLinkId

TransferId
```

---

# 20. Logical Link State Machine

Estados:

```text
CREATING

CONTROL_ONLY

UPGRADING

HYBRID_ACTIVE

DEGRADED

FALLING_BACK

SWITCHING

CLOSING

CLOSED

FAILED
```

Transiciones:

```text
CREATING
   │
   ▼
CONTROL_ONLY
   │
   ├──── upgrade requested ───► UPGRADING
   │                             │
   │                             ├── success ─► HYBRID_ACTIVE
   │                             │
   │                             └── failure ─► CONTROL_ONLY
   │
   ├──── control degraded ─────► DEGRADED
   │
   └──── close ────────────────► CLOSING

HYBRID_ACTIVE
   │
   ├──── data degraded ────────► DEGRADED
   │
   ├──── data lost ────────────► FALLING_BACK
   │
   ├──── switch requested ─────► SWITCHING
   │
   └──── close ────────────────► CLOSING

FALLING_BACK
   │
   ├──── fallback success ─────► CONTROL_ONLY
   │
   └──── no viable transport ──► FAILED
```

---

# 21. PhysicalLink

```text
PhysicalLink
────────────────────────────────

physicalLinkId

logicalLinkId

transportType

localRole

remoteRole

state

transportReference

bindingState

transportEpoch

establishedAt

boundAt

activatedAt

lastActivityAt

healthSnapshot

failureReason
```

---

# 22. Physical Link State Machine

Estados:

```text
NEW

ESTABLISHING

PHYSICALLY_CONNECTED

BINDING

BOUND

ACTIVATING

ACTIVE

DEGRADED

DISCONNECTING

DISCONNECTED

FAILED
```

Regla:

```text
PHYSICALLY_CONNECTED

MUST NOT

→ ACTIVE
```

sin pasar por:

```text
BINDING

BOUND
```

---

# 23. Transport Roles

Roles genéricos:

```text
INITIATOR

ACCEPTOR
```

Roles Wi-Fi Direct:

```text
GROUP_OWNER

GROUP_CLIENT
```

No deben confundirse con:

```text
Secure Session INITIATOR / RESPONDER
```

---

# 24. Wi-Fi Direct Role Resolution

La asignación debe ser determinista.

Política inicial:

```text
Role Score =
    Power Capability
  + Current Battery Policy
  + Platform Capability
  + Existing Group State
  + Stable Tie Breaker
```

El `Stable Tie Breaker`:

```text
Canonical NodeId Byte Comparison
```

---

# 25. Role Proposal

```text
WIFI_DIRECT_ROLE_PROPOSAL
────────────────────────────────

upgradeAttemptId

logicalLinkId

proposerNodeId

proposedLocalRole

proposedRemoteRole

roleScoreSummary

proposalNonce
```

---

# 26. Role Agreement

```text
WIFI_DIRECT_ROLE_ACCEPT

or

WIFI_DIRECT_ROLE_COUNTER

or

WIFI_DIRECT_ROLE_REJECT
```

La negociación debe terminar en:

```text
AGREED ROLE ASSIGNMENT
```

antes de iniciar establecimiento físico.

---

# 27. Simultaneous Upgrade Requests

Ambos nodos pueden solicitar upgrade simultáneamente.

Debe converger determinísticamente.

Regla inicial:

```text
Compare UpgradeAttemptId

Select Canonical Attempt
```

Si los IDs se generan concurrentemente sin conocimiento mutuo:

```text
Lowest Canonical UUID Bytes Wins
```

El intento redundante se cancela.

---

# 28. Transport Policy Engine

Input:

```text
Local Capabilities

Remote Capabilities

Logical Link State

Current Active Transports

Expected Payload Size

Operation Priority

Energy Policy

Transport Health

Previous Failures

User Policy
```

Output:

```text
TransportPolicyDecision
────────────────────────────────

preferredControlTransport

preferredDataTransport

upgradeRecommended

fallbackOrder[]

reasonCodes[]

policyVersion
```

---

# 29. Política inicial

```text
IF

Wi-Fi Direct supported by both peers

AND

Expected Payload Size >= WIFI_DIRECT_UPGRADE_THRESHOLD

AND

No active Wi-Fi Direct failure backoff

THEN

Upgrade to Wi-Fi Direct Data Plane


ELSE

Remain on BLE
```

Debe ser configurable y testeable.

---

# 30. Upgrade Protocol Overview

```text
NODE A                                NODE B

   │                                    │
   │──── TRANSPORT_UPGRADE_PROPOSE ────►│
   │                                    │
   │◄─── TRANSPORT_UPGRADE_ACCEPT ──────│
   │                                    │
   │──── ROLE_PROPOSAL ─────────────────►│
   │                                    │
   │◄─── ROLE_ACCEPT ───────────────────│
   │                                    │
   │──── TRANSPORT_ESTABLISH_START ─────►│
   │                                    │
   │         WIFI DIRECT SETUP           │
   │                                    │
   │──── PHYSICAL LINK CONNECTED ────────│
   │                                    │
   │         BINDING HANDSHAKE            │
   │                                    │
   │──── TRANSPORT_BOUND ────────────────►│
   │                                    │
   │◄─── TRANSPORT_BOUND ────────────────│
   │                                    │
   │──── TRANSPORT_ACTIVATE ─────────────►│
   │                                    │
   │◄─── TRANSPORT_ACTIVATE_ACK ─────────│
   │                                    │

          WIFI DIRECT DATA PLANE ACTIVE
```

---

# 31. TransportUpgradeAttempt

```text
TransportUpgradeAttempt
────────────────────────────────

upgradeAttemptId

logicalLinkId

requestedTransportType

initiatorNodeId

state

localRole

remoteRole

createdAt

establishmentStartedAt

physicalConnectedAt

boundAt

activatedAt

failureReason
```

---

# 32. Upgrade Attempt State Machine

```text
PROPOSED

NEGOTIATING

ROLE_RESOLVING

ESTABLISHING

PHYSICAL_CONNECTED

BINDING

BOUND

ACTIVATING

ACTIVE

REJECTED

CANCELLED

FAILED

TIMED_OUT
```

---

# 33. Transport Establishment

El Core invoca:

```text
TransportAdapter.establish(
    EstablishmentRequest
)
```

El adapter devuelve eventos:

```text
ESTABLISHMENT_STARTED

PEER_DISCOVERED

ROLE_ASSIGNED

PHYSICAL_CONNECTED

CONNECTION_FAILED

CONNECTION_LOST
```

El Core no debe interpretar APIs específicas Android.

---

# 34. Wi-Fi Direct Data Channel

Después de formar grupo:

```text
GROUP OWNER

opens local server endpoint


GROUP CLIENT

connects to endpoint
```

El endpoint exacto pertenece al adapter.

El Core recibe:

```text
OpaqueTransportChannel
```

---

# 35. Problema de Transport Hijacking

Un atacante puede:

1. observar actividad Wi-Fi Direct;

2. conectarse al endpoint;

3. intentar hacerse pasar por el peer esperado.

Por tanto:

```text
IP CONNECTION

≠

AUTHENTICATED PEER
```

Debe existir `Transport Binding Protocol`.

---

# 36. Transport Binding

Objetivo:

```text
Bind:

New Physical Link

to

Authenticated Nodes

+

Secure Session

+

Logical Link

+

Upgrade Attempt

+

Transport Epoch
```

---

# 37. Transport Binding Secret

La SPEC-005 debe exponer mediante mecanismo de exporter:

```text
exportKeyMaterial(
    label,
    context,
    length
)
```

Label:

```text
OFFLINE_PROTOCOL_TRANSPORT_BINDING_V1
```

Context:

```text
localNodeId

remoteNodeId

logicalLinkId

upgradeAttemptId

transportType

transportEpoch
```

Resultado:

```text
transportBindingSecret
```

No debe transmitirse.

---

# 38. Binding Token

Cada peer genera:

```text
bindingNonce
```

y calcula:

```text
bindingToken =
HMAC-SHA-256(
    transportBindingSecret,
    DOMAIN_TAG
    ||
    senderNodeId
    ||
    receiverNodeId
    ||
    logicalLinkId
    ||
    upgradeAttemptId
    ||
    physicalLinkId
    ||
    transportType
    ||
    transportEpoch
    ||
    bindingNonce
)
```

Domain Tag:

```text
OFFLINE_PROTOCOL_TRANSPORT_BINDING_TOKEN_V1
```

---

# 39. Binding Handshake

Sobre el nuevo transporte:

```text
NODE A                              NODE B

   │                                  │
   │──── TRANSPORT_BIND_HELLO ───────►│
   │                                  │
   │◄─── TRANSPORT_BIND_HELLO ────────│
   │                                  │
   │──── TRANSPORT_BIND_PROOF ────────►│
   │                                  │
   │◄─── TRANSPORT_BIND_PROOF ────────│
   │                                  │
   │──── TRANSPORT_BIND_CONFIRM ──────►│
   │                                  │
   │◄─── TRANSPORT_BIND_CONFIRM ──────│
```

Solo después:

```text
PhysicalLink → BOUND
```

---

# 40. TRANSPORT_BIND_HELLO

```text
TransportBindHello
────────────────────────────────

bindingProtocolVersion

logicalLinkId

upgradeAttemptId

physicalLinkId

transportType

transportEpoch

senderNodeId

receiverNodeId

bindingNonce
```

---

# 41. TRANSPORT_BIND_PROOF

```text
TransportBindProof
────────────────────────────────

logicalLinkId

upgradeAttemptId

physicalLinkId

transportEpoch

senderNodeId

bindingNonce

bindingToken
```

---

# 42. Binding Validation

El receptor debe verificar:

1. protocol version;

2. logicalLinkId;

3. upgradeAttemptId;

4. expected remote NodeId;

5. expected local NodeId;

6. transportType;

7. transportEpoch;

8. physicalLinkId semantics;

9. nonce freshness;

10. bindingToken constant-time comparison;

11. binding state machine.

---

# 43. Binding Replay Protection

Un Binding Token antiguo no debe ser válido para:

```text
Different Secure Session

Different Logical Link

Different Upgrade Attempt

Different Physical Link

Different Transport Type

Different Transport Epoch

Different Peer Direction
```

Los nonces aceptados deben mantenerse durante la vida del binding attempt.

---

# 44. Transport Activation

Después de binding mutuo:

```text
BOUND

    │

    ▼

TRANSPORT_ACTIVATE

    │

    ▼

TRANSPORT_ACTIVATE_ACK

    │

    ▼

ACTIVE
```

El frame de activación viaja por Secure Session BLE.

Esto permite confirmar desde el canal previamente autenticado que ambos peers consideran válido el nuevo enlace.

---

# 45. Hybrid Active State

```text
LogicalLink:

HYBRID_ACTIVE


Control Plane:

BLE


Data Plane:

WIFI_DIRECT
```

Las capas superiores reciben:

```text
LogicalLinkHandle
```

y no necesitan conocer detalles del cambio.

---

# 46. Control Plane

Responsabilidades:

```text
Transport Negotiation

Upgrade Coordination

Fallback Coordination

Health Signals

Transfer Control

Session Control

Errors

Close
```

Inicialmente:

```text
BLE Secure Session
```

---

# 47. Data Plane

Responsabilidades:

```text
Bulk Payload

Chunks

Large Objects

Future Streams
```

Inicialmente:

```text
Wi-Fi Direct
```

---

# 48. Seguridad del Data Plane

El hecho de haber ejecutado Transport Binding no elimina la necesidad de proteger los datos.

La arquitectura debe elegir explícitamente entre:

```text
OPTION A

Secure Records from SPEC-005 over Wi-Fi Direct


OPTION B

New Data-Plane Traffic Keys derived from exporter secret
```

Decisión inicial:

```text
OPTION B
```

para separar:

```text
Control Plane Keys

Data Plane Keys
```

---

# 49. Data Plane Key Derivation

Desde:

```text
transportBindingSecret
```

derivar:

```text
dataPlaneSendKey

dataPlaneReceiveKey

dataPlaneSendNonceBase

dataPlaneReceiveNonceBase

dataPlaneRekeySecret
```

mediante HKDF-SHA-256 y labels independientes.

---

# 50. Data Plane Secure Record

```text
DataPlaneRecord
────────────────────────────────

recordVersion

logicalLinkId

physicalLinkId

transportEpoch

keyEpoch

sequenceNumber

streamType

ciphertextLength

ciphertext

authenticationTag
```

AEAD:

```text
ChaCha20-Poly1305
```

---

# 51. Data Plane Sequence Spaces

Cada PhysicalLink activo debe mantener:

```text
sendSequence

receiveSequence
```

independientes de la Secure Session BLE.

Nunca compartir nonce space entre:

```text
BLE Control Plane

Wi-Fi Direct Data Plane
```

---

# 52. Transport Epoch

Cada activación de Data Plane posee:

```text
transportEpoch
```

Inicial:

```text
0
```

Cada reemplazo del Data Plane:

```text
transportEpoch++
```

Esto evita confundir records de enlaces antiguos con enlaces nuevos.

---

# 53. Transport Health Model

```text
TransportHealthSnapshot
────────────────────────────────

transportType

physicalLinkId

observedAt

state

latencyEstimate

throughputEstimate

errorRateEstimate

recentFailures

lastSuccessfulActivityAt

energyCostClass

adapterSignals
```

No todos los adapters deben producir todas las métricas.

---

# 54. Health States

```text
HEALTHY

DEGRADED

UNSTABLE

UNAVAILABLE
```

---

# 55. Degradation Detection

La política puede considerar:

```text
Repeated Send Failures

Repeated Receive Failures

Timeouts

Connection Loss

Throughput Collapse

Latency Increase

Adapter Error Signals

No Successful Activity

Operating System Transport Events
```

RSSI por sí solo no debe decidir degradación definitiva.

---

# 56. Fallback

Flujo:

```text
WIFI DIRECT DATA PLANE

        │

        ▼

DEGRADATION / LOSS

        │

        ▼

FALLBACK POLICY

        │

        ▼

SELECT BLE

        │

        ▼

FALLBACK_PROPOSE

        │

        ▼

FALLBACK_ACCEPT

        │

        ▼

ACTIVATE BLE DATA PATH

        │

        ▼

CLOSE OLD WIFI DIRECT LINK

        │

        ▼

CONTROL_ONLY
```

---

# 57. Fallback Invariant

Antes de cerrar el transporte anterior cuando todavía funciona parcialmente:

```text
NEW DATA PATH READY

BEFORE

OLD DATA PATH CLOSED
```

cuando sea técnicamente posible.

---

# 58. Transport Switch

Un switch representa:

```text
Old PhysicalLink

→

New PhysicalLink
```

Entidad:

```text
TransportSwitch
────────────────────────────────

switchId

logicalLinkId

fromPhysicalLinkId

toPhysicalLinkId

reason

state

startedAt

completedAt

failureReason
```

---

# 59. Transport Switch State Machine

```text
REQUESTED

PREPARING_NEW_LINK

NEW_LINK_BOUND

NEW_LINK_ACTIVE

DRAINING_OLD_LINK

OLD_LINK_CLOSED

COMPLETED

ROLLED_BACK

FAILED
```

---

# 60. In-Flight Data During Switch

La SPEC-006 no implementa todavía un scheduler completo.

Regla inicial:

```text
Physical transport may fail.

Logical operation state must remain durable.

Unacknowledged data is retransmitted by upper protocol.
```

Hybrid Transport Core no garantiza exactamente una vez para payloads.

---

# 61. Duplicate Data Risk

Las capas superiores deben asumir:

```text
AT-LEAST-ONCE PHYSICAL DELIVERY POSSIBILITY
```

durante recovery y switching.

La deduplicación pertenece a protocolos superiores mediante IDs estables.

---

# 62. Physical Link Failure

Ante fallo:

1. marcar PhysicalLink FAILED;

2. detener nuevos writes;

3. emitir evento;

4. evaluar fallback;

5. preservar LogicalLink si existe transporte viable;

6. cerrar recursos del adapter;

7. destruir data-plane keys asociadas;

8. incrementar transport epoch cuando corresponda.

---

# 63. BLE Failure

Si BLE Control Plane falla mientras Wi-Fi Direct continúa activo:

```text
HYBRID_ACTIVE

      │

      ▼

CONTROL PLANE LOST

      │

      ▼

DEGRADED
```

La versión inicial no promociona automáticamente Wi-Fi Direct a Control Plane.

Debe intentarse:

```text
BLE RECOVERY
```

Si falla:

```text
LogicalLink FAILED or CLOSED
```

según política.

---

# 64. Wi-Fi Direct Failure

```text
HYBRID_ACTIVE

      │

      ▼

DATA PLANE LOST

      │

      ▼

FALLING_BACK

      │

      ▼

BLE DATA PATH

      │

      ▼

CONTROL_ONLY
```

---

# 65. Transport Recovery

Después de process death:

```text
TransportRecoveryService.recover()
```

debe:

1. localizar LogicalLinks no terminales;

2. localizar PhysicalLinks no terminales;

3. asumir que handles físicos anteriores son inválidos;

4. marcar PhysicalLinks como DISCONNECTED;

5. no restaurar data-plane keys;

6. no restaurar transport bindings activos;

7. preservar historial;

8. requerir Secure Session nueva cuando SPEC-005 lo requiera;

9. permitir reanudar operaciones lógicas superiores posteriormente.

---

# 66. Persistence

Persistir:

```text
LogicalLink Metadata

PhysicalLink Metadata

Upgrade Attempts

Transport Switches

Capability Snapshots

Policy Decisions

Health Summaries

Failure Reasons

Transport Events
```

No persistir:

```text
Transport Binding Secret

Data Plane Traffic Keys

Nonce Bases

Rekey Secrets
```

---

# 67. Transport Events

Eventos mínimos:

```text
LOGICAL_LINK_CREATION_STARTED

LOGICAL_LINK_CREATED

TRANSPORT_CAPABILITIES_SENT

TRANSPORT_CAPABILITIES_RECEIVED

TRANSPORT_CAPABILITIES_VALIDATED

TRANSPORT_POLICY_EVALUATED

TRANSPORT_UPGRADE_RECOMMENDED

TRANSPORT_UPGRADE_PROPOSED

TRANSPORT_UPGRADE_ACCEPTED

TRANSPORT_UPGRADE_REJECTED

TRANSPORT_ROLE_RESOLUTION_STARTED

TRANSPORT_ROLE_RESOLVED

TRANSPORT_ESTABLISHMENT_STARTED

TRANSPORT_PHYSICALLY_CONNECTED

TRANSPORT_BINDING_STARTED

TRANSPORT_BIND_HELLO_SENT

TRANSPORT_BIND_HELLO_RECEIVED

TRANSPORT_BIND_PROOF_SENT

TRANSPORT_BIND_PROOF_RECEIVED

TRANSPORT_BINDING_SUCCEEDED

TRANSPORT_BINDING_FAILED

TRANSPORT_ACTIVATION_STARTED

TRANSPORT_ACTIVATED

LOGICAL_LINK_HYBRID_ACTIVE

TRANSPORT_HEALTH_DEGRADED

TRANSPORT_HEALTH_UNSTABLE

TRANSPORT_CONNECTION_LOST

TRANSPORT_FALLBACK_STARTED

TRANSPORT_FALLBACK_COMPLETED

TRANSPORT_FALLBACK_FAILED

TRANSPORT_SWITCH_STARTED

TRANSPORT_SWITCH_COMPLETED

TRANSPORT_SWITCH_ROLLED_BACK

TRANSPORT_SWITCH_FAILED

PHYSICAL_LINK_CLOSED

LOGICAL_LINK_CLOSED

LOGICAL_LINK_FAILED
```

---

# 68. Error Taxonomy

```text
HybridTransportError

├── CapabilityError
│   ├── InvalidTransportCapabilities
│   ├── CapabilityNodeMismatch
│   ├── UnsupportedTransportVersion
│   └── NoCompatibleTransport
│
├── PolicyError
│   ├── TransportPolicyEvaluationFailure
│   ├── NoViableTransport
│   └── TransportPolicyConflict
│
├── RoleResolutionError
│   ├── WifiDirectRoleConflict
│   ├── InvalidRoleProposal
│   ├── RoleResolutionTimeout
│   └── NonDeterministicRoleResolution
│
├── UpgradeError
│   ├── UpgradeAlreadyInProgress
│   ├── UpgradeRejected
│   ├── UpgradeCancelled
│   ├── UpgradeTimeout
│   └── DuplicateUpgradeConflict
│
├── EstablishmentError
│   ├── TransportAdapterUnavailable
│   ├── PhysicalConnectionFailure
│   ├── PhysicalConnectionTimeout
│   └── UnexpectedPhysicalPeer
│
├── BindingError
│   ├── BindingProtocolMismatch
│   ├── BindingLogicalLinkMismatch
│   ├── BindingUpgradeAttemptMismatch
│   ├── BindingPhysicalLinkMismatch
│   ├── BindingNodeMismatch
│   ├── BindingTransportMismatch
│   ├── BindingEpochMismatch
│   ├── BindingReplayDetected
│   ├── InvalidBindingToken
│   └── BindingConfirmationFailure
│
├── ActivationError
│   ├── TransportNotBound
│   ├── ActivationRejected
│   ├── ActivationTimeout
│   └── ActivationStateConflict
│
├── DataPlaneError
│   ├── DataPlaneKeyDerivationFailure
│   ├── DataPlaneRecordTooLarge
│   ├── DataPlaneReplayDetected
│   ├── DataPlaneSequenceViolation
│   ├── DataPlaneAeadFailure
│   └── DataPlaneEpochMismatch
│
├── FallbackError
│   ├── FallbackUnavailable
│   ├── FallbackRejected
│   ├── FallbackActivationFailure
│   └── NoFallbackTransport
│
├── SwitchError
│   ├── TransportSwitchConflict
│   ├── NewLinkPreparationFailure
│   ├── OldLinkDrainFailure
│   ├── TransportSwitchRollbackFailure
│   └── TransportSwitchStateConflict
│
└── RecoveryError
    ├── LogicalLinkRecoveryFailure
    ├── PhysicalLinkRecoveryFailure
    └── TransportStateInconsistency
```

---

# 69. Límites configurables

```text
MAX_TRANSPORT_CAPABILITIES

MAX_ACTIVE_LOGICAL_LINKS

MAX_PHYSICAL_LINKS_PER_LOGICAL_LINK

MAX_CONCURRENT_UPGRADE_ATTEMPTS

MAX_UPGRADE_ATTEMPTS_PER_WINDOW

UPGRADE_BACKOFF

TRANSPORT_ESTABLISHMENT_TIMEOUT

ROLE_RESOLUTION_TIMEOUT

TRANSPORT_BINDING_TIMEOUT

TRANSPORT_ACTIVATION_TIMEOUT

MAX_BINDING_NONCES

MAX_DATA_PLANE_RECORD_SIZE

MAX_RECORDS_PER_DATA_PLANE_KEY

MAX_BYTES_PER_DATA_PLANE_KEY

TRANSPORT_HEALTH_WINDOW

TRANSPORT_DEGRADATION_THRESHOLD

TRANSPORT_FAILURE_THRESHOLD

FALLBACK_TIMEOUT

TRANSPORT_SWITCH_TIMEOUT

WIFI_DIRECT_UPGRADE_THRESHOLD
```

---

# 70. Observabilidad

Métricas mínimas:

```text
logical_links_created_total

logical_links_closed_total

logical_links_failed_total

transport_capability_exchanges_total

transport_policy_evaluations_total

transport_upgrades_started_total

transport_upgrades_completed_total

transport_upgrades_failed_total

transport_role_conflicts_total

physical_links_established_total

physical_links_failed_total

transport_bindings_started_total

transport_bindings_completed_total

transport_bindings_failed_total

transport_binding_replays_rejected_total

transport_hijacking_attempts_rejected_total

transport_activations_total

transport_activation_failures_total

hybrid_links_active_total

transport_health_degradations_total

transport_fallbacks_started_total

transport_fallbacks_completed_total

transport_fallbacks_failed_total

transport_switches_total

data_plane_records_sent_total

data_plane_records_received_total

data_plane_records_rejected_total

transport_upgrade_duration_ms

transport_binding_duration_ms

transport_switch_duration_ms
```

---

# 71. Logging

Logs estructurados pueden incluir:

```text
timestamp

logicalLinkId

physicalLinkId

upgradeAttemptId

switchId

encounterId

secureSessionId

localNodeId

remoteNodeId

transportType

localRole

remoteRole

transportEpoch

stateBefore

stateAfter

policyDecision

healthState

eventType

durationMs

errorCode
```

No deben incluir:

```text
Transport Binding Secrets

Data Plane Keys

Nonce Bases

Plaintext Payloads

Wi-Fi Credentials

Sensitive OS Transport Metadata
```

---

# 72. API pública inicial

```text
HybridTransportCore
────────────────────────────────────

createLogicalLink(encounterId, secureSessionId)

exchangeCapabilities(logicalLinkId)

evaluateTransportPolicy(logicalLinkId, operationContext)

requestUpgrade(logicalLinkId, transportType)

acceptUpgrade(upgradeAttemptId)

rejectUpgrade(upgradeAttemptId, reason)

resolveTransportRoles(upgradeAttemptId)

establishTransport(upgradeAttemptId)

bindPhysicalLink(physicalLinkId)

activateTransport(physicalLinkId)

getLogicalLink(logicalLinkId)

getPhysicalLink(physicalLinkId)

getActiveTransports(logicalLinkId)

getTransportHealth(physicalLinkId)

requestFallback(logicalLinkId, reason)

requestSwitch(logicalLinkId, targetTransport)

closePhysicalLink(physicalLinkId, reason)

closeLogicalLink(logicalLinkId, reason)

recover()

subscribeEvents(listener)
```

---

# 73. LogicalLinkHandle

Interfaz para capas superiores:

```text
LogicalLinkHandle
────────────────────────────────

getLogicalLinkId()

getRemoteNodeId()

getState()

sendControl(frameStream)

openDataStream(streamMetadata)

sendData(streamId, byteStream)

closeDataStream(streamId)

getActiveControlTransport()

getActiveDataTransport()

requestTransportUpgrade()

requestFallback()

subscribeLinkEvents()

close()
```

---

# 74. Transport Adapter Contract

```text
TransportAdapter
────────────────────────────────

getTransportType()

getCapabilities()

establish(request)

accept(request)

openChannel(reference)

send(channel, frameStream)

receive(channel)

getHealth(channel)

close(channel)

subscribeEvents(listener)
```

---

# 75. BLE Adapter Responsibilities

```text
Advertising

Scanning

Control Channel Establishment

Secure Session Byte Transport

Fallback Data Transport

BLE Health Signals
```

No implementa:

```text
Identity

Secure Session

Transport Policy

Transport Binding Logic

Transfer Semantics
```

---

# 76. Wi-Fi Direct Adapter Responsibilities

```text
Peer Discovery Coordination

Group Creation

Group Joining

Role Application

Connection Lifecycle

Endpoint Creation

Socket Establishment

Byte Transport

Health Signals

Resource Cleanup
```

No implementa:

```text
Node Identity

Peer Authentication

Transport Binding Policy

Data Plane Cryptography

Logical Link Lifecycle

Transfer Semantics
```

---

# 77. Integración con SPEC-005

```text
SECURE SESSION CORE

        │

        ├── send encrypted control frames

        ├── receive encrypted control frames

        └── export key material
                │
                ▼

HYBRID TRANSPORT CORE
```

Contrato requerido:

```text
SecureSessionExporter
────────────────────────────────

exportKeyMaterial(
    sessionId,
    label,
    context,
    length
)
```

---

# 78. Integración con SPEC-002

```text
TRANSFER CORE

       │

       ▼

LOGICAL LINK HANDLE

       │

       ▼

HYBRID TRANSPORT CORE

       │

       ├── BLE
       └── WIFI DIRECT
```

Transfer Core no debe conocer qué transporte físico está activo.

---

# 79. Testing Strategy

## Unit Tests

```text
Capability Validation

Logical Link State Machine

Physical Link State Machine

Upgrade Attempt State Machine

Transport Switch State Machine

Policy Evaluation

Role Resolution

Simultaneous Upgrade Resolution

Binding Context Construction

Binding Token Validation

Binding Replay Protection

Transport Epoch Handling

Fallback Selection

Recovery Policy

Error Taxonomy
```

---

## Protocol Test Vectors

```text
Transport Capabilities Encoding

Role Proposal Encoding

Upgrade Proposal Encoding

Binding Context Encoding

Exporter Context Encoding

Binding Token HMAC

Data Plane Key Derivation

Data Plane AAD

Data Plane Nonce Construction

Data Plane AEAD
```

---

## Property-Based Tests

```text
physical connection never implies authenticated transport

unbound transport never becomes ACTIVE

binding token from another session never validates

binding token from another logical link never validates

binding token from another upgrade attempt never validates

binding token from another transport epoch never validates

binding token from opposite direction never validates

same policy inputs produce same decision

simultaneous upgrade attempts converge deterministically

LogicalLink survives Data Plane failure when fallback exists

transport switch never activates unbound new link

old transport epoch records never validate under new epoch

control and data plane keys are always distinct

process restart never restores bound PhysicalLink
```

---

## Integration Tests

```text
Two Nodes

Real Identity Core

Real Discovery Core

Real Secure Session Core

Hybrid Transport Core

Fake BLE Adapter

Fake Wi-Fi Direct Adapter

Capability Exchange

Upgrade Negotiation

Role Resolution

Physical Establishment

Binding

Activation

Hybrid Data Transfer

Wi-Fi Direct Failure

BLE Fallback

Transport Recovery
```

---

## Android Hardware Tests

```text
Two Real Android Devices

BLE Discovery

BLE Secure Session

Capability Exchange

Wi-Fi Direct Upgrade

Deterministic Role Resolution

Group Creation

Socket Establishment

Transport Binding

Encrypted Data Plane

Wi-Fi Direct Disconnect

BLE Fallback

Wi-Fi Direct Re-upgrade
```

---

## Fault Injection Tests

```text
malformed capabilities

capability NodeId mismatch

simultaneous upgrade

role negotiation conflict

Wi-Fi Direct establishment timeout

unexpected socket peer

invalid binding token

replayed binding proof

binding proof from old session

binding proof from old transport epoch

activation ACK lost

Wi-Fi Direct loss during activation

Wi-Fi Direct loss during data transfer

BLE loss during HYBRID_ACTIVE

data-plane ciphertext modification

data-plane replay

process death during role resolution

process death during binding

process death after activation

fallback failure

switch rollback failure
```

---

# 80. Escenarios obligatorios de aceptación

## Scenario A — BLE Only

```text
Two Nodes

BLE Available

Wi-Fi Direct Unavailable
```

Resultado:

```text
LogicalLink CONTROL_ONLY

Control Plane = BLE

Data Path = BLE
```

---

## Scenario B — Hybrid Upgrade

```text
BLE Secure Session

+

Both Support Wi-Fi Direct

+

Large Payload Expected
```

Resultado:

```text
Upgrade Negotiated

Wi-Fi Direct Established

Transport Bound

Transport Activated

LogicalLink HYBRID_ACTIVE
```

---

## Scenario C — Unknown Wi-Fi Peer

Un dispositivo no autenticado se conecta al endpoint Wi-Fi Direct.

Resultado:

```text
Binding Failed

PhysicalLink Rejected

No Application Data Delivered
```

---

## Scenario D — Binding Replay

Reutilizar token de upgrade anterior.

Resultado:

```text
BindingReplayDetected

PhysicalLink Not Activated
```

---

## Scenario E — Wrong Secure Session

Usar binding proof derivado desde otra Secure Session.

Resultado:

```text
InvalidBindingToken

PhysicalLink Not Activated
```

---

## Scenario F — Simultaneous Upgrade

```text
A requests Wi-Fi Direct upgrade

B requests Wi-Fi Direct upgrade
```

Resultado:

```text
One Canonical Upgrade Attempt

Deterministic Convergence

No Duplicate Active Wi-Fi Direct Links
```

---

## Scenario G — Wi-Fi Direct Failure

```text
LogicalLink HYBRID_ACTIVE

Wi-Fi Direct Lost
```

Resultado:

```text
Fallback Started

BLE Data Path Activated

LogicalLink CONTROL_ONLY

Logical Operation Preserved
```

---

## Scenario H — BLE Failure

```text
LogicalLink HYBRID_ACTIVE

BLE Control Plane Lost
```

Resultado:

```text
LogicalLink DEGRADED

BLE Recovery Attempted

No Silent Promotion Of Wi-Fi Direct To Control Plane
```

---

## Scenario I — Transport Epoch Isolation

```text
Wi-Fi Direct Link Epoch 0 Closed

New Wi-Fi Direct Link Epoch 1 Activated

Replay Record From Epoch 0
```

Resultado:

```text
DataPlaneEpochMismatch

Record Rejected
<truncated 13417 bytes>

NOTE: The output was truncated because it was too long. Use a more targeted query or a smaller range to get the information you need.
