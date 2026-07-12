# SPEC-007 — Durable Object Transfer & Resumption Protocol

**Estado:** Draft
**Tipo:** Core Data Transfer Protocol Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-002, SPEC-003, SPEC-004, SPEC-005, SPEC-006
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Conectividad requerida:** Ninguna infraestructura externa
**Internet requerido:** No
**Servidor requerido:** No
**Transporte específico requerido:** No

---

# 1. Propósito

Definir e implementar el protocolo durable mediante el cual dos nodos pueden transferir objetos binarios arbitrarios de forma:

* offline;
* autenticada;
* cifrada en tránsito;
* transport-independent;
* durable;
* reanudable;
* verificable;
* idempotente;
* resistente a duplicados;
* resistente a pérdida de conexión;
* resistente a cambios de transporte;
* resistente a process death;
* capaz de continuar después de un reencuentro posterior;
* capaz de detectar corrupción parcial;
* capaz de evitar retransmisiones innecesarias;
* observable;
* testeable sin hardware real.

La SPEC define la transición:

```text
LOCAL OBJECT

      │

      ▼

OBJECT REGISTRATION

      │

      ▼

IMMUTABLE OBJECT MANIFEST

      │

      ▼

TRANSFER OFFER

      │

      ▼

TRANSFER ACCEPTANCE

      │

      ▼

CHUNK PLAN

      │

      ▼

CHUNK TRANSMISSION

      │

      ▼

DURABLE CHUNK COMMIT

      │

      ▼

ACKNOWLEDGEMENT

      │

      ├───────────────┐
      │               │
      ▼               ▼

TRANSPORT LOSS    PROCESS DEATH

      │               │
      └───────┬───────┘
              │
              ▼

        STATE RECOVERY

              │

              ▼

       RESUMPTION SYNC

              │

              ▼

     MISSING CHUNKS ONLY

              │

              ▼

      OBJECT VERIFICATION

              │

              ▼

         COMPLETED
```

---

# 2. Problema fundamental

Después de SPEC-006 existe:

```text
Decentralized Node Identity

+

Authenticated Peer Discovery

+

Secure Sessions

+

Hybrid Logical Links

+

BLE Control Plane

+

Wi-Fi Direct Data Plane

+

Transport Upgrade

+

Transport Fallback
```

Pero todavía no existe un protocolo durable que permita responder correctamente:

```text
¿Qué se está transfiriendo?

¿Qué bytes pertenecen al objeto?

¿Qué chunks existen?

¿Qué chunks fueron enviados?

¿Qué chunks fueron recibidos?

¿Qué chunks fueron verificados?

¿Qué chunks fueron persistidos?

¿Qué chunks faltan?

¿Qué ocurre si el transporte desaparece?

¿Qué ocurre si cambia BLE ↔ Wi-Fi Direct?

¿Qué ocurre si muere el proceso?

¿Qué ocurre si los nodos se reencuentran horas después?

¿Cómo sabemos que el objeto final es correcto?

¿Cómo evitamos retransmitir chunks ya confirmados?

¿Cómo evitamos completar un objeto corrupto?
```

Por tanto:

> La unidad durable del sistema no puede ser la conexión física ni el stream.

Debe existir una abstracción persistente:

```text
DurableTransfer
```

capaz de sobrevivir a múltiples:

```text
Secure Sessions

Logical Links

Physical Links

Transport Epochs

Application Processes

Physical Encounters
```

---

# 3. Objetivo arquitectónico

Construir:

```text
DURABLE OBJECT TRANSFER CORE
```

que permita:

```text
Object Registration

Immutable Object Manifest

Transfer Negotiation

Chunk Planning

Chunk Transmission

Durable Chunk Commit

Acknowledgement

Missing Chunk Detection

Resumption

Whole Object Verification

Crash Recovery

Transfer Completion
```

sobre:

```text
LogicalLink
```

sin depender de:

```text
BLE

Wi-Fi Direct

Android APIs

Sockets

IP Addresses

Continuous Connectivity
```

---

# 4. Principio arquitectónico fundamental

```text
TRANSFER ≠ SESSION ≠ LOGICAL LINK ≠ PHYSICAL LINK
```

Relación:

```text
DurableTransfer

    │

    ├── may use SecureSession #1
    │
    ├── may use LogicalLink #1
    │
    ├── may use Wi-Fi Direct Link #1
    │
    ├── may fallback to BLE
    │
    ├── may survive Process Death
    │
    ├── may resume during Encounter #2
    │
    ├── may use SecureSession #2
    │
    └── completes later
```

---

# 5. Segundo principio fundamental

```text
RECEIVED ≠ DURABLY COMMITTED
```

Un chunk recibido en memoria:

```text
MUST NOT

be acknowledged as durable
```

hasta que:

```text
bytes persisted

+

integrity verified

+

metadata committed
```

---

# 6. Tercer principio fundamental

```text
TRANSFER COMPLETED ≠ ALL CHUNKS RECEIVED
```

La transferencia solo puede completarse cuando:

```text
All Chunks Durably Committed

+

All Chunk Hashes Valid

+

Whole Object Hash Valid

+

Final Object Materialization Successful

+

Completion State Persisted
```

---

# 7. Cuarto principio fundamental

```text
TRANSPORT DELIVERY MAY BE DUPLICATED

TRANSFER COMMIT MUST BE IDEMPOTENT
```

Un mismo chunk puede recibirse múltiples veces.

Solo debe existir un resultado durable válido.

---

# 8. Objetivos funcionales

Al finalizar esta SPEC, el sistema debe poder:

1. registrar un objeto local;

2. calcular su identidad criptográfica;

3. construir un manifest inmutable;

4. ofrecer el objeto a un peer;

5. aceptar o rechazar la transferencia;

6. crear DurableTransfer;

7. dividir lógicamente el objeto en chunks;

8. enviar chunks por LogicalLink;

9. recibir chunks fuera del transporte específico;

10. verificar cada chunk;

11. persistir chunks válidos;

12. confirmar únicamente chunks durables;

13. detectar chunks faltantes;

14. retransmitir únicamente chunks faltantes;

15. sobrevivir a transport loss;

16. sobrevivir a transport switch;

17. sobrevivir a process death;

18. reanudar durante una nueva Secure Session;

19. reanudar durante un nuevo Encounter;

20. verificar el objeto completo;

21. materializar el objeto final;

22. cerrar la transferencia durablemente;

23. liberar recursos temporales;

24. producir eventos y métricas.

---

# 9. Resultado esperado

```text
DURABLE TRANSFER CORE
────────────────────────────────────

Object Identity

Object Manifest

Transfer Offer

Transfer Acceptance

Transfer Rejection

Durable Transfer Model

Chunk Model

Chunk Plan

Chunk Request Protocol

Chunk Data Protocol

Chunk Commit Protocol

Acknowledgement Protocol

Missing Chunk Set

Resumption Protocol

Progress Reconciliation

Whole Object Verification

Object Materialization

Crash Recovery

Transfer Cancellation

Transfer Expiration

Garbage Collection

Storage Quotas

Backpressure Contracts

Transfer Events

Transfer Metrics

Protocol Test Vectors
```

---

# 10. Fuera de alcance

Esta SPEC no implementará:

* multi-peer transfer;
* swarm transfer;
* BitTorrent-like exchange;
* erasure coding;
* network coding;
* Forward Error Correction;
* content discovery;
* distributed object search;
* routing;
* multi-hop;
* relay;
* store-carry-forward entre nodos intermedios;
* delta synchronization;
* directory synchronization;
* conflict resolution;
* CRDT;
* application-level messaging;
* compression negotiation avanzada;
* adaptive chunk sizing completo;
* multipath chunk scheduling;
* parallel transfer scheduler avanzado;
* content-defined chunking;
* deduplicación global entre objetos;
* cloud backup;
* Internet transport.

---

# 11. Criptografía inicial

```text
Object Hash:

SHA-256


Chunk Hash:

SHA-256


Manifest Hash:

SHA-256


Transport Encryption:

Provided by SPEC-005 / SPEC-006


Secure Random:

Operating System CSPRNG
```

La SPEC no implementa cifrado adicional del objeto en reposo.

---

# 12. Arquitectura

```text
┌──────────────────────────────────────────────┐
│               APPLICATION / SDK              │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│            DURABLE TRANSFER SERVICE          │
│                                              │
│ registerObject()                             │
│ offerTransfer()                              │
│ acceptTransfer()                             │
│ rejectTransfer()                             │
│ pauseTransfer()                              │
│ resumeTransfer()                             │
│ cancelTransfer()                             │
│ recover()                                    │
└───────────────┬───────────────┬──────────────┘
                │               │
                ▼               ▼
┌──────────────────────┐ ┌─────────────────────┐
│ OBJECT MANIFEST      │ │ TRANSFER PROTOCOL   │
│                      │ │                     │
│ Object Identity      │ │ Offer               │
│ Chunk Layout         │ │ Accept              │
│ Chunk Hashes         │ │ Chunk Request       │
│ Manifest Hash        │ │ Chunk Data          │
└──────────┬───────────┘ │ ACK                 │
           │             │ Resume              │
           │             └──────────┬──────────┘
           │                        │
           ▼                        ▼
┌──────────────────────────────────────────────┐
│             DURABLE TRANSFER STORE           │
│                                              │
│ Transfer Metadata                            │
│ Chunk State                                  │
│ Received Chunk Storage                       │
│ Progress State                               │
│ Recovery State                               │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│               LOGICAL LINK PORT              │
│                                              │
│ sendControl()                                │
│ sendData()                                   │
│ receiveControl()                             │
│ receiveData()                                │
│ subscribeEvents()                            │
└──────────────────────────────────────────────┘
```

---

# 13. Bounded Context

La SPEC define:

```text
ObjectTransfer
```

Entidades:

```text
TransferObject

ObjectManifest

DurableTransfer

TransferChunk

TransferPeerProgress

TransferAttempt

TransferEvent
```

Value Objects:

```text
ObjectId

ManifestId

TransferId

ChunkId

ChunkIndex

ChunkOffset

ChunkLength

ChunkHash

ObjectHash

ManifestHash

TransferDirection

TransferState

ChunkState

TransferPriority

TransferFailureReason
```

Servicios:

```text
ObjectRegistrationService

ObjectManifestService

TransferNegotiationService

ChunkPlanningService

ChunkTransmissionService

ChunkCommitService

TransferProgressService

TransferResumptionService

ObjectVerificationService

ObjectMaterializationService

TransferRecoveryService

TransferGarbageCollectionService
```

---

# 14. ObjectId

Identidad del contenido:

```text
ObjectId =
SHA-256(
    OBJECT_DOMAIN_TAG
    ||
    objectLength
    ||
    objectBytes
)
```

Domain Tag:

```text
OFFLINE_PROTOCOL_OBJECT_ID_V1
```

Consecuencia:

```text
Same Bytes

+

Same Length

→

Same ObjectId
```

`ObjectId` identifica contenido.

No identifica una transferencia.

---

# 15. TransferId

Cada operación de transferencia posee:

```text
TransferId
```

Formato recomendado:

```text
UUIDv7
```

Dos transferencias del mismo ObjectId entre los mismos nodos pueden tener TransferId diferentes.

---

# 16. ChunkId

```text
ChunkId =
SHA-256(
    CHUNK_DOMAIN_TAG
    ||
    ObjectId
    ||
    ChunkIndex
    ||
    ChunkOffset
    ||
    ChunkLength
    ||
    ChunkBytes
)
```

Domain Tag:

```text
OFFLINE_PROTOCOL_CHUNK_ID_V1
```

---

# 17. Chunk Size

La versión inicial utilizará:

```text
FIXED-SIZE CHUNKING
```

Configuración:

```text
DEFAULT_CHUNK_SIZE

MIN_CHUNK_SIZE

MAX_CHUNK_SIZE
```

Recomendación inicial:

```text
DEFAULT_CHUNK_SIZE = 256 KiB
```

Debe validarse mediante benchmarks reales.

---

# 18. Object Manifest

```text
ObjectManifest
────────────────────────────────

manifestVersion

objectId

objectLength

objectHashAlgorithm

chunkingAlgorithm

chunkSize

chunkCount

chunks[]

mediaType

objectName

applicationMetadata

createdAt
```

Cada chunk:

```text
ManifestChunk
────────────────────────────────

chunkIndex

offset

length

chunkHash
```

---

# 19. Manifest Inmutability

Después de crear:

```text
ObjectManifest
```

los campos criptográficamente relevantes no deben modificarse.

Si cambia:

```text
Object Bytes

Object Length

Chunk Size

Chunk Layout

Chunk Hash
```

debe crearse un nuevo manifest.

---

# 20. Canonical Manifest Encoding

Debe existir:

```text
Canonical Binary Encoding
```

El Manifest Hash:

```text
manifestHash =
SHA-256(
    MANIFEST_DOMAIN_TAG
    ||
    canonicalManifestBytes
)
```

Domain Tag:

```text
OFFLINE_PROTOCOL_OBJECT_MANIFEST_V1
```

---

# 21. Metadata no confiable

Campos como:

```text
objectName

mediaType

applicationMetadata
```

son metadata declarativa.

No deben utilizarse para:

```text
filesystem path construction

security decisions

authorization

automatic execution
```

sin validación adicional.

---

# 22. DurableTransfer

```text
DurableTransfer
────────────────────────────────

transferId

objectId

manifestId

localNodeId

remoteNodeId

direction

state

priority

totalBytes

committedBytes

totalChunks

committedChunks

createdAt

acceptedAt

startedAt

lastProgressAt

completedAt

cancelledAt

failureReason
```

---

# 23. Transfer Direction

```text
OUTBOUND

INBOUND
```

---

# 24. Transfer State Machine

Estados:

```text
CREATED

OFFERING

OFFERED

ACCEPTING

ACCEPTED

TRANSFERRING

PAUSED

INTERRUPTED

RESUMING

VERIFYING

MATERIALIZING

COMPLETED

REJECTED

CANCELLED

EXPIRED

FAILED
```

---

# 25. Transfer State Transitions

```text
CREATED
   │
   ▼
OFFERING
   │
   ├──── send failure ─────► INTERRUPTED
   ▼
OFFERED
   │
   ├──── reject ───────────► REJECTED
   ├──── timeout ──────────► EXPIRED
   ▼
ACCEPTING
   │
   ▼
ACCEPTED
   │
   ▼
TRANSFERRING
   │
   ├──── pause ────────────► PAUSED
   ├──── link loss ────────► INTERRUPTED
   ├──── process death ────► INTERRUPTED
   ├──── all committed ────► VERIFYING
   ▼
RESUMING
   │
   ▼
TRANSFERRING
   │
   ▼
VERIFYING
   │
   ├──── invalid object ───► FAILED
   ▼
MATERIALIZING
   │
   ├──── storage failure ──► FAILED
   ▼
COMPLETED
```

---

# 26. Transfer Offer

```text
TRANSFER_OFFER
────────────────────────────────

protocolVersion

transferId

senderNodeId

receiverNodeId

objectId

manifestHash

objectLength

chunkCount

chunkSize

mediaType

objectName

transferPriority

offerNonce
```

Debe viajar por:

```text
Secure Control Plane
```

---

# 27. Transfer Offer Validation

El receptor debe:

1. validar protocolVersion;

2. validar senderNodeId;

3. validar receiverNodeId;

4. validar peer autenticado;

5. validar tamaños máximos;

6. validar objectLength;

7. validar chunkCount;

8. validar chunkSize;

9. validar quotas;

10. detectar TransferId duplicado;

11. detectar ObjectId ya disponible localmente;

12. persistir oferta;

13. emitir evento.

---

# 28. Existing Object Optimization

Si receptor ya posee:

```text
ObjectId
```

verificado:

```text
TRANSFER_ALREADY_HAVE_OBJECT
```

El sistema puede completar la transferencia lógicamente sin retransmitir bytes.

Debe validarse manifest compatibility.

---

# 29. Transfer Acceptance

```text
TRANSFER_ACCEPT
────────────────────────────────

transferId

receiverNodeId

acceptedManifestHash

storageReservationId

receiverCapabilities

acceptNonce
```

---

# 30. Transfer Rejection

```text
TRANSFER_REJECT
────────────────────────────────

transferId

receiverNodeId

reasonCode
```

Razones iniciales:

```text
USER_REJECTED

INSUFFICIENT_STORAGE

QUOTA_EXCEEDED

UNSUPPORTED_OBJECT_SIZE

UNSUPPORTED_MANIFEST

POLICY_REJECTED

DUPLICATE_TRANSFER

INVALID_OFFER
```

---

# 31. Manifest Transfer

Después de aceptar:

```text
MANIFEST_REQUEST

MANIFEST_DATA

MANIFEST_ACK
```

El receptor:

1. recibe manifest;

2. valida tamaño;

3. calcula manifestHash;

4. compara expected manifestHash;

5. valida ObjectId;

6. valida chunk layout;

7. valida offsets;

8. valida longitudes;

9. valida chunk count;

10. persiste manifest.

---

# 32. Chunk State

Estados:

```text
MISSING

REQUESTED

RECEIVING

RECEIVED

VERIFYING

VERIFIED

COMMITTING

COMMITTED

RETRY_PENDING

FAILED
```

---

# 33. Chunk State Machine

```text
MISSING
   │
   ▼
REQUESTED
   │
   ▼
RECEIVING
   │
   ├──── interrupted ─────► RETRY_PENDING
   ▼
RECEIVED
   │
   ▼
VERIFYING
   │
   ├──── hash mismatch ───► RETRY_PENDING
   ▼
VERIFIED
   │
   ▼
COMMITTING
   │
   ├──── storage error ───► RETRY_PENDING / FAILED
   ▼
COMMITTED
```

---

# 34. Chunk Request

El receptor controla inicialmente qué chunks necesita.

```text
CHUNK_REQUEST
────────────────────────────────

transferId

requestId

requestedChunks[]

maxBytes

receiverWindow

transportHint
```

`transportHint` no obliga al Hybrid Transport Core.

---

# 35. Requested Chunk Encoding

Para v1:

```text
Chunk Range Encoding
```

Ejemplo:

```text
0-15

20-31

44

50-63
```

Debe existir encoding binario canónico.

---

# 36. Chunk Data

```text
CHUNK_DATA
────────────────────────────────

protocolVersion

transferId

objectId

chunkIndex

chunkOffset

chunkLength

chunkHash

payloadLength

payload
```

El payload viaja por:

```text
LogicalLink Data Plane
```

---

# 37. Chunk Validation Pipeline

```text
CHUNK_DATA

      │

      ▼

Validate Maximum Frame Size

      │

      ▼

Strict Parse

      │

      ▼

Validate TransferId

      │

      ▼

Validate ObjectId

      │

      ▼

Validate ChunkIndex

      │

      ▼

Validate Offset

      │

      ▼

Validate Length

      │

      ▼

Compare Manifest Chunk Metadata

      │

      ▼

Persist To Temporary Storage

      │

      ▼

Compute SHA-256

      │

      ▼

Compare Chunk Hash

      │

      ├──── INVALID ──► DELETE TEMP / RETRY

      ▼

Durable Commit

      │

      ▼

ACK
```

---

# 38. Streaming Requirement

El sistema MUST soportar procesamiento streaming.

Prohibido requerir:

```text
Entire Object In Memory

Entire Large Chunk Set In Memory

Entire Received Object ByteArray
```

---

# 39. Temporary Chunk Storage

Cada inbound transfer debe poseer almacenamiento temporal aislado.

Conceptualmente:

```text
transfer-temp/

    transferId/

        chunks/

            00000000.part

            00000001.part

            ...
```

El Core debe depender de:

```text
TransferStoragePort
```

No de filesystem APIs específicas.

---

# 40. Durable Chunk Commit

Orden obligatorio:

```text
Receive Bytes

      │

      ▼

Persist Temporary Chunk

      │

      ▼

Flush / Durability Barrier

      │

      ▼

Verify Chunk Hash

      │

      ▼

Move / Mark Chunk Durable

      │

      ▼

Commit Chunk Metadata

      │

      ▼

Update Transfer Progress

      │

      ▼

Emit Event

      │

      ▼

Send ACK
```

---

# 41. Commit Invariant

```text
ACK(COMMITTED)

MUST IMPLY

Chunk Bytes Recoverable After Process Restart
```

---

# 42. Idempotent Commit

Si el mismo chunk válido llega nuevamente:

```text
IF chunk already COMMITTED

AND hash matches manifest

THEN

do not write duplicate durable state

return ACK(COMMITTED)
```

---

# 43. Chunk Acknowledgement

```text
CHUNK_ACK
────────────────────────────────

transferId

ackId

committedChunkRanges[]

committedBytes

receiverWindow

ackSequence
```

ACK significa:

```text
DURABLY COMMITTED
```

No significa:

```text
received in socket

received in memory

received in temporary buffer
```

---

# 44. Sender Progress

El sender mantiene:

```text
RemoteTransferProgress
────────────────────────────────

transferId

remoteCommittedChunks

remoteCommittedBytes

lastAckSequence

lastAckAt
```

---

# 45. ACK Idempotency

ACK duplicado:

```text
MUST NOT

increment progress twice
```

---

# 46. ACK Ordering

Para v1:

```text
ackSequence monotonic
```

ACK antiguo puede ignorarse.

ACK futuro inválido debe rechazarse.

---

# 47. Missing Chunk Set

El receptor mantiene:

```text
MissingChunks =
ManifestChunks
-
CommittedChunks
```

Debe poder representarse sin cargar millones de objetos en memoria.

---

# 48. Chunk Bitmap

Implementación inicial recomendada:

```text
Persistent Chunk Bitmap
```

Estados mínimos:

```text
0 = NOT COMMITTED

1 = COMMITTED
```

Estados operacionales adicionales pueden mantenerse separadamente.

---

# 49. Transfer Progress

```text
TransferProgress
────────────────────────────────

transferId

totalBytes

committedBytes

totalChunks

committedChunks

progressPercentage

lastProgressAt
```

La fuente de verdad es:

```text
COMMITTED CHUNKS
```

No bytes enviados.

---

# 50. Backpressure

El receptor debe controlar la presión de envío mediante:

```text
receiverWindow
```

El sender MUST NOT superar:

```text
MAX_IN_FLIGHT_BYTES

MAX_IN_FLIGHT_CHUNKS

receiverWindow
```

---

# 51. Transfer Window

```text
TransferWindow
────────────────────────────────

maxInFlightChunks

maxInFlightBytes

availableChunkSlots

availableBytes

updatedAt
```

---

# 52. Sender Scheduling v1

Política inicial:

```text
Lowest Missing Chunk Index First
```

Debe ser reemplazable.

---

# 53. Transport Independence

ChunkTransmissionService usa:

```text
LogicalLinkHandle
```

Nunca:

```text
BLE

Wi-Fi Direct

Socket

IP Address
```

---

# 54. Transport Switch During Transfer

```text
TRANSFERRING

Wi-Fi Direct Active

      │

      ▼

Wi-Fi Direct Lost

      │

      ▼

LogicalLink Fallback

      │

      ▼

BLE Data Path

      │

      ▼

Transfer Continues

      │

      ▼

Missing / Unacked Chunks Retransmitted
```

TransferId permanece igual.

---

# 55. Transport Epoch Independence

DurableTransfer no depende de:

```text
transportEpoch
```

Un transfer puede atravesar:

```text
Transport Epoch 0

Transport Epoch 1

Transport Epoch 2
```

sin cambiar TransferId.

---

# 56. Secure Session Independence

DurableTransfer no depende de una Secure Session específica.

Una transferencia puede comenzar en:

```text
SecureSession A
```

y continuar posteriormente en:

```text
SecureSession B
```

si:

```text
same authenticated remote NodeId

+

valid resumption protocol
```

---

# 57. Encounter Independence

Una transferencia puede sobrevivir:

```text
Encounter #1

disconnect

hours later

Encounter #2
```

El nuevo peer debe autenticarse como el mismo NodeId.

---

# 58. Transfer Interruption

Triggers:

```text
LogicalLink Lost

Secure Session Lost

Transport Unavailable

Process Death

Application Stop

Operating System Resource Revocation

Recoverable Storage Error
```

Resultado:

```text
TRANSFERRING

→

INTERRUPTED
```

No:

```text
FAILED
```

salvo error terminal.

---

# 59. Resumption Discovery

Después de autenticar un peer:

```text
findResumableTransfers(remoteNodeId)
```

Resultado:

```text
OUTBOUND interrupted transfers

INBOUND interrupted transfers
```

---

# 60. Resumption Protocol Overview

```text
NODE A                              NODE B

   │                                  │
   │──── RESUME_SUMMARY ─────────────►│
   │                                  │
   │◄─── RESUME_SUMMARY ──────────────│
   │                                  │
   │──── RESUME_STATE_REQUEST ───────►│
   │                                  │
   │◄─── RESUME_STATE_RESPONSE ───────│
   │                                  │
   │──── RESUME_PLAN ────────────────►│
   │                                  │
   │◄─── RESUME_ACCEPT ───────────────│
   │                                  │

          TRANSFER CONTINUES
```

---

# 61. RESUME_SUMMARY

```text
ResumeSummary
────────────────────────────────

protocolVersion

senderNodeId

receiverNodeId

resumableTransfers[]

summaryNonce
```

Cada entrada:

```text
ResumableTransferSummary
────────────────────────────────

transferId

objectId

manifestHash

direction

state

committedChunks

totalChunks

lastProgressAt
```

---

# 62. Resumption Security

El sistema debe verificar:

```text
Current Peer NodeId

==

DurableTransfer.remoteNodeId
```

Una transferencia no puede reanudarse con otro NodeId.

---

# 63. Resume State Request

```text
RESUME_STATE_REQUEST
────────────────────────────────

transferId

requestedStateRepresentation

requestNonce
```

Representaciones iniciales:

```text
CHUNK_BITMAP

MISSING_RANGES

COMMITTED_RANGES
```

---

# 64. Resume State Response

```text
RESUME_STATE_RESPONSE
────────────────────────────────

transferId

manifestHash

stateRepresentation

statePayload

committedBytes

stateVersion

responseNonce
```

---

# 65. Resumption Reconciliation

Regla fundamental:

```text
RECEIVER DURABLE COMMIT STATE

IS AUTHORITATIVE

FOR RECEIVER PROGRESS
```

El sender no puede afirmar que un chunk está entregado si receptor no lo posee COMMITTED.

---

# 66. Resume Plan

```text
ResumePlan
────────────────────────────────

transferId

manifestHash

chunksToSend[]

senderObservedCommittedState

receiverAuthoritativeStateVersion

resumeNonce
```

---

# 67. Resume Plan Validation

El receptor debe verificar:

1. TransferId;

2. ObjectId;

3. manifestHash;

4. remoteNodeId;

5. transfer direction;

6. stateVersion;

7. requested chunk ranges;

8. limits;

9. current transfer state.

---

# 68. Resumption Idempotency

Ejecutar resumption múltiples veces debe converger al mismo estado durable.

---

# 69. Process Death Recovery

```text
ACTIVE TRANSFER

      │

      ▼

PROCESS DEATH

      │

      ▼

RESTART

      │

      ▼

recover()

      │

      ▼

LOAD NON-TERMINAL TRANSFERS

      │

      ▼

RECONCILE CHUNK STORAGE

      │

      ▼

REBUILD PROGRESS FROM COMMITTED STATE

      │

      ▼

MARK ACTIVE TRANSFERS INTERRUPTED

      │

      ▼

WAIT FOR AUTHENTICATED PEER

      │

      ▼

RESUME
```

---

# 70. Recovery Source of Truth

Después de restart:

```text
Durable Metadata

+

Committed Chunk Storage

+

Manifest
```

son fuente de verdad.

No:

```text
In-Memory Counters

Previous Socket State

Previous LogicalLink State

Previous SecureSession State
```

---

# 71. Storage Reconciliation

`recover()` debe detectar:

```text
Temporary Chunk Without Metadata

Metadata COMMITTED But Chunk Missing

Chunk File With Wrong Length

Chunk File With Wrong Hash

Unknown Chunk File

Completed Transfer Without Final Object

Final Object Present But Completion State Missing
```

Cada inconsistencia debe tener política explícita.

---

# 72. Whole Object Verification

Cuando:

```text
committedChunks == totalChunks
```

ejecutar:

```text
VERIFYING
```

Pipeline:

```text
Read Committed Chunks In Manifest Order

      │

      ▼

Stream Into SHA-256

      │

      ▼

Compute ObjectId / Object Hash

      │

      ▼

Compare Expected ObjectId

      │

      ├──── INVALID ──► FAILED / REPAIR POLICY

      ▼

MATERIALIZING
```

---

# 73. Materialization

El sistema debe crear el objeto final sin cargarlo completamente en memoria.

Proceso:

```text
Create Temporary Final Object

      │

      ▼

Stream Committed Chunks In Order

      │

      ▼

Flush / Durability Barrier

      │

      ▼

Verify Final Length

      │

      ▼

Verify Final Object Hash

      │

      ▼

Atomic Publish

      │

      ▼

Persist COMPLETED

      │

      ▼

Emit Event
```

---

# 74. Atomic Completion Invariant

```text
COMPLETED

MUST IMPLY

Final Object Is Recoverable And Verified
```

---

# 75. Object Repair Policy v1

Si whole-object verification falla:

1. marcar transferencia FAILED;

2. conservar manifest;

3. conservar evidencia;

4. no publicar objeto final;

5. permitir diagnóstico;

6. no intentar identificar automáticamente chunks corruptos mediante whole hash.

Futura SPEC puede introducir Merkle Trees.

---

# 76. Cancellation

```text
TRANSFER_CANCEL
────────────────────────────────

transferId

senderNodeId

reasonCode

cancelNonce
```

Cancelación debe ser idempotente.

---

# 77. Cancellation Policy

Cancelar:

```text
Stops Scheduling New Chunks

Preserves Audit Metadata

Releases Active Resources

May Delete Temporary Chunk Storage According To Policy
```

---

# 78. Transfer Expiration

Ofertas y transferencias inactivas pueden expirar.

Configuraciones:

```text
OFFER_EXPIRATION

INACTIVE_TRANSFER_EXPIRATION

INTERRUPTED_TRANSFER_RETENTION

COMPLETED_TRANSFER_RETENTION

FAILED_TRANSFER_RETENTION
```

---

# 79. Garbage Collection

Debe existir:

```text
TransferGarbageCollectionService
```

Responsabilidades:

```text
Expired Offers

Cancelled Temporary Data

Failed Transfer Temporary Data

Orphan Temporary Chunks

Old Completed Transfer Metadata

Unused Manifests

Storage Quota Enforcement
```

---

# 80. Storage Reservation

Antes de aceptar una transferencia inbound:

```text
reserveStorage(
    objectLength,
    transferId
)
```

Debe evitar aceptar objetos imposibles de almacenar.

---

# 81. Storage Quotas

Configurable:

```text
MAX_OBJECT_SIZE

MAX_TOTAL_INBOUND_TEMP_STORAGE

MAX_ACTIVE_INBOUND_TRANSFERS

MAX_ACTIVE_OUTBOUND_TRANSFERS

MAX_CHUNKS_PER_OBJECT

MAX_MANIFEST_SIZE

MAX_APPLICATION_METADATA_SIZE
```

---

# 82. Disk Full

Si almacenamiento se agota durante transferencia:

```text
TRANSFERRING

→

PAUSED or FAILED
```

según posibilidad de recuperación.

No debe enviarse ACK de chunks no durables.

---

# 83. Transfer Events

Eventos mínimos:

```text
OBJECT_REGISTRATION_STARTED

OBJECT_REGISTERED

OBJECT_MANIFEST_CREATED

TRANSFER_CREATED

TRANSFER_OFFER_SENT

TRANSFER_OFFER_RECEIVED

TRANSFER_OFFER_ACCEPTED

TRANSFER_OFFER_REJECTED

MANIFEST_REQUESTED

MANIFEST_RECEIVED

MANIFEST_VERIFIED

TRANSFER_STARTED

CHUNK_REQUESTED

CHUNK_SEND_STARTED

CHUNK_SENT

CHUNK_RECEIVED

CHUNK_VERIFICATION_STARTED

CHUNK_VERIFIED

CHUNK_VERIFICATION_FAILED

CHUNK_COMMIT_STARTED

CHUNK_COMMITTED

CHUNK_ACK_SENT

CHUNK_ACK_RECEIVED

TRANSFER_PROGRESS_UPDATED

TRANSFER_INTERRUPTED

TRANSFER_RESUMPTION_DISCOVERED

TRANSFER_RESUMPTION_STARTED

TRANSFER_STATE_RECONCILED

TRANSFER_RESUMED

TRANSFER_VERIFICATION_STARTED

TRANSFER_OBJECT_VERIFIED

TRANSFER_MATERIALIZATION_STARTED

TRANSFER_MATERIALIZED

TRANSFER_COMPLETED

TRANSFER_PAUSED

TRANSFER_CANCELLED

TRANSFER_EXPIRED

TRANSFER_FAILED

TRANSFER_RECOVERY_STARTED

TRANSFER_RECOVERY_COMPLETED

TRANSFER_STORAGE_RECONCILIATION_FAILED

TRANSFER_GARBAGE_COLLECTION_STARTED

TRANSFER_GARBAGE_COLLECTION_COMPLETED
```

---

# 84. Error Taxonomy

```text
DurableTransferError

├── ObjectError
│   ├── ObjectNotFound
│   ├── ObjectReadFailure
│   ├── ObjectSizeExceeded
│   ├── ObjectHashMismatch
│   └── ObjectRegistrationFailure
│
├── ManifestError
│   ├── ManifestTooLarge
│   ├── MalformedManifest
│   ├── ManifestHashMismatch
│   ├── InvalidChunkLayout
│   ├── InvalidChunkCount
│   ├── InvalidChunkOffset
│   ├── InvalidChunkLength
│   └── ManifestObjectMismatch
│
├── NegotiationError
│   ├── InvalidTransferOffer
│   ├── TransferPeerMismatch
│   ├── TransferAlreadyExists
│   ├── TransferRejected
│   ├── TransferOfferExpired
│   └── StorageReservationFailure
│
├── ChunkError
│   ├── ChunkNotFound
│   ├── InvalidChunkIndex
│   ├── ChunkOffsetMismatch
│   ├── ChunkLengthMismatch
│   ├── ChunkHashMismatch
│   ├── ChunkStorageFailure
│   ├── ChunkCommitFailure
│   └── ChunkStateConflict
│
├── AcknowledgementError
│   ├── InvalidAckSequence
│   ├── AckProgressRegression
│   ├── AckUnknownChunk
│   └── AckStateConflict
│
├── FlowControlError
│   ├── ReceiverWindowExceeded
│   ├── TooManyInFlightChunks
│   ├── TooManyInFlightBytes
│   └── BackpressureViolation
│
├── ResumptionError
│   ├── ResumePeerMismatch
│   ├── ResumeTransferNotFound
│   ├── ResumeManifestMismatch
│   ├── ResumeDirectionMismatch
│   ├── ResumeStateVersionConflict
│   ├── InvalidResumeState
│   └── ResumePlanConflict
│
├── VerificationError
│   ├── IncompleteObject
│   ├── WholeObjectHashMismatch
│   ├── FinalObjectLengthMismatch
│   └── ObjectVerificationFailure
│
├── MaterializationError
│   ├── FinalObjectCreationFailure
│   ├── FinalObjectWriteFailure
│   ├── FinalObjectFlushFailure
│   ├── AtomicPublishFailure
│   └── FinalObjectUnavailable
│
├── RecoveryError
│   ├── TransferRecoveryFailure
│   ├── ChunkMetadataStorageMismatch
│   ├── MissingCommittedChunk
│   ├── OrphanTemporaryChunk
│   ├── RecoveryProgressMismatch
│   └── CompletionRecoveryConflict
│
└── LifecycleError
    ├── InvalidTransferStateTransition
    ├── TransferAlreadyTerminal
    ├── TransferExpired
    └── TransferCancellationConflict
```

---

# 85. Límites configurables

```text
DEFAULT_CHUNK_SIZE

MIN_CHUNK_SIZE

MAX_CHUNK_SIZE

MAX_OBJECT_SIZE

MAX_CHUNKS_PER_OBJECT

MAX_MANIFEST_SIZE

MAX_TRANSFER_METADATA_SIZE

MAX_APPLICATION_METADATA_SIZE

MAX_ACTIVE_INBOUND_TRANSFERS

MAX_ACTIVE_OUTBOUND_TRANSFERS

MAX_IN_FLIGHT_CHUNKS

MAX_IN_FLIGHT_BYTES

MAX_CHUNK_RETRY_COUNT

CHUNK_REQUEST_TIMEOUT

CHUNK_ACK_TIMEOUT

TRANSFER_IDLE_TIMEOUT

OFFER_EXPIRATION

INACTIVE_TRANSFER_EXPIRATION

INTERRUPTED_TRANSFER_RETENTION

COMPLETED_TRANSFER_RETENTION

FAILED_TRANSFER_RETENTION

MAX_TOTAL_INBOUND_TEMP_STORAGE
```

---

# 86. Observabilidad

Métricas mínimas:

```text
objects_registered_total

object_manifests_created_total

transfer_offers_sent_total

transfer_offers_received_total

transfer_offers_accepted_total

transfer_offers_rejected_total

transfers_started_total

transfers_completed_total

transfers_failed_total

transfers_cancelled_total

transfers_interrupted_total

transfers_resumed_total

chunks_requested_total

chunks_sent_total

chunks_received_total

chunks_verified_total

chunks_verification_failed_total

chunks_committed_total

chunks_retransmitted_total

chunk_acknowledgements_sent_total

chunk_acknowledgements_received_total

transfer_bytes_sent_total

transfer_bytes_received_total

transfer_bytes_committed_total

transfer_bytes_retransmitted_total

transfer_resumption_bytes_saved_total

transfer_recovery_runs_total

transfer_recovery_failures_total

whole_object_verification_failures_total

transfer_materialization_failures_total

transfer_duration_ms

chunk_commit_duration_ms

transfer_resumption_duration_ms
```

---

# 87. Logging

Logs pueden incluir:

```text
timestamp

transferId

objectId

manifestHash

localNodeId

remoteNodeId

direction

transferState

chunkIndex

chunkState

chunkLength

committedBytes

committedChunks

totalBytes

totalChunks

logicalLinkId

eventType

durationMs

errorCode
```

No deben incluir:

```text
Object Payload

Chunk Payload

Sensitive Application Metadata

Traffic Keys

Transport Binding Secrets
```

---

# 88. API pública inicial

```text
DurableTransferCore
────────────────────────────────────

registerObject(objectSource, metadata)

getObject(objectId)

createTransfer(objectId, remoteNodeId, options)

offerTransfer(transferId, logicalLinkId)

acceptTransfer(transferId, storagePolicy)

rejectTransfer(transferId, reason)

startTransfer(transferId, logicalLinkId)

pauseTransfer(transferId)

resumeTransfer(transferId, logicalLinkId)

cancelTransfer(transferId, reason)

getTransfer(transferId)

getTransferProgress(transferId)

getMissingChunks(transferId)

getResumableTransfers(remoteNodeId)

processControlFrame(logicalLinkId, frame)

processDataFrame(logicalLinkId, frameStream)

recover()

runGarbageCollection()

subscribeEvents(listener)
```

---

# 89. ObjectSource Contract

```text
ObjectSource
────────────────────────────────

getLength()

openReadStream()

getMetadata()
```

---

# 90. TransferStoragePort

```text
TransferStoragePort
────────────────────────────────

reserveStorage(transferId, expectedBytes)

releaseReservation(reservationId)

createTemporaryChunk(transferId, chunkIndex)

writeChunk(chunkHandle, byteStream)

flushChunk(chunkHandle)

commitChunk(transferId, chunkIndex, chunkHandle, metadata)

openCommittedChunk(transferId, chunkIndex)

deleteTemporaryChunk(chunkHandle)

createFinalObject(transferId)

appendChunkToFinalObject(finalHandle, chunkStream)

flushFinalObject(finalHandle)

publishFinalObject(finalHandle, objectId)

openFinalObject(objectId)

deleteTransferTemporaryStorage(transferId)

getAvailableStorage()

reconcile()
```

---

# 91. DurableTransferRepository

```text
DurableTransferRepository
────────────────────────────────

saveTransfer(transfer)

getTransfer(transferId)

findByRemoteNode(remoteNodeId)

findNonTerminalTransfers()

saveManifest(manifest)

getManifest(manifestId)

saveChunkState(transferId, chunkIndex, state)

markChunkCommitted(transferId, chunkIndex, metadata)

getCommittedChunkBitmap(transferId)

saveRemoteProgress(transferId, progress)

appendEvent(event)

transaction(block)
```

---

# 92. LogicalLink Integration

```text
DURABLE TRANSFER CORE

        │

        ├── sendControl()
        │
        ├── sendData()
        │
        ├── receiveControl()
        │
        ├── receiveData()
        │
        └── subscribeLinkEvents()
        │
        ▼

LOGICAL LINK

        │

        ▼

HYBRID TRANSPORT CORE
```

---

# 93. Security Requirements

La SPEC depende de:

```text
Authenticated NodeId

Secure Control Plane

Authenticated Data Plane
```

Además debe proteger contra:

```text
Malformed Manifests

Oversized Objects

Oversized Chunk Counts

Chunk Hash Substitution

TransferId Confusion

ObjectId Confusion

Resume Peer Substitution

Resume Manifest Substitution

Duplicate Chunk Commit

Fake ACK Progress

Path Traversal Metadata

Storage Exhaustion

Memory Exhaustion
```

---

# 94. Protocol Test Vectors

Deben existir vectores para:

```text
ObjectId

ChunkId

Manifest Canonical Encoding

Manifest Hash

Chunk Layout

Chunk Range Encoding

Transfer Offer Encoding

Transfer Accept Encoding

Chunk Request Encoding

Chunk Data Header Encoding

Chunk ACK Encoding

Resume Summary Encoding

Resume State Encoding

Resume Plan Encoding

Whole Object Verification
```

---

# 95. Testing Strategy

## Unit Tests

```text
Object Registration

Fixed Chunk Planning

Manifest Construction

Manifest Validation

Transfer State Machine

Chunk State Machine

Chunk Range Encoding

Chunk Bitmap

ACK Sequence Handling

Progress Calculation

Missing Chunk Calculation

Receiver Window

Resumption Reconciliation

Storage Reservation

Garbage Collection

Error Taxonomy
```

---

## Property-Based Tests

```text
same object bytes produce same ObjectId

different object bytes produce different ObjectId with overwhelming probability

manifest chunk lengths sum to object length

chunk offsets never overlap

chunk offsets cover object exactly

committedBytes never exceed totalBytes

committedChunks never exceed totalChunks

ACK never precedes durable commit

duplicate valid chunk commit is idempotent

duplicate ACK never increments progress twice

receiver durable state dominates sender belief

resumption never retransmits authoritative committed chunks

transport switch never changes TransferId

Secure Session replacement never changes TransferId

Encounter replacement never changes TransferId

process restart reconstructs progress from committed state

COMPLETED always implies valid whole object hash

malformed metadata never escapes storage namespace
```

---

## Integration Tests

```text
Two Nodes

Identity Core

Discovery Core

Secure Session Core

Hybrid Transport Core

Durable Transfer Core

Fake Storage

Fake BLE

Fake Wi-Fi Direct

Object Registration

Offer

Accept

Manifest Exchange

Chunk Transfer

Chunk Commit

ACK

Wi-Fi Direct Failure

BLE Fallback

Process Death

Restart

New Encounter

New Secure Session

Resumption

Missing Chunks Only

Whole Object Verification

Completion
```

---

## Large Object Tests

```text
1 Byte

Empty Object

255 KiB

256 KiB

257 KiB

1 MiB

100 MiB

1 GiB

4 GiB+

Object With Millions Of Chunks Under Small Chunk Configuration
```

---

## Fault Injection Tests

```text
manifest truncated

manifest modified

wrong manifestHash

chunk payload modified

wrong chunk index

wrong chunk offset

wrong chunk length

duplicate chunk

chunk commit crash before metadata commit

chunk metadata commit crash before ACK

ACK lost

duplicate ACK

old ACK

future ACK

Wi-Fi Direct lost mid-chunk

fallback during chunk transmission

process death while receiving chunk

process death after chunk flush

process death after chunk commit

process death before ACK

process death during whole object verification

process death during materialization

disk full

committed chunk missing after restart

orphan temporary chunk

final object present without COMPLETED state

new peer attempts resume

same peer with new Secure Session resumes

same peer after hours resumes
```

---

# 96. Escenarios obligatorios de aceptación

## Scenario A — Small Object Transfer

```text
Object Registered

Transfer Offered

Transfer Accepted

Manifest Verified

Chunks Transferred

Chunks Committed

Whole Object Verified
```

Resultado:

```text
COMPLETED
```

---

## Scenario B — Large Object Streaming

```text
4 GiB Object
```

Resultado:

```text
No Full Object In Memory

Bounded Memory Usage

COMPLETED
```

---

## Scenario C — Duplicate Chunk

Enviar el mismo chunk dos veces.

Resultado:

```text
One Durable Commit

Idempotent ACK

No Duplicate Progress
```

---

## Scenario D — Corrupted Chunk

Modificar payload.

Resultado:

```text
ChunkHashMismatch

Chunk Not Committed

No ACK(COMMITTED)

Retry Allowed
```

---

## Scenario E — Wi-Fi Direct Loss

```text
TRANSFERRING

Wi-Fi Direct Lost

BLE Available
```

Resultado:

```text
LogicalLink Fallback

TransferId Preserved

Uncommitted Chunks Retransmitted

Transfer Continues
```

---

## Scenario F — Process Death After Commit Before ACK

```text
Chunk Persisted

Hash Valid

Metadata COMMITTED

Process Dies

ACK Not Sent
```

Después:

```text
recover()

Resume

Sender Retransmits Chunk
```

Resultado:

```text
Receiver Detects Existing COMMITTED Chunk

No Duplicate Write

ACK(COMMITTED)

Correct Progress
```

---

## Scenario G — Process Death Before Commit

```text
Chunk Partially Received

Process Dies
```

Resultado:

```text
Temporary Data Reconciled

Chunk Remains Missing

Chunk Retransmitted
```

---

## Scenario H — Reencounter Hours Later

```text
Transfer Interrupted

Devices Separate

Hours Pass

Devices Meet Again

New Encounter

New Secure Session
```

Resultado:

```text
Same NodeId Authenticated

Resumable Transfer Discovered

Progress Reconciled

Only Missing Chunks Sent

Transfer Completes
```

---

## Scenario I — Different Peer Attempts Resume

Resultado:

```text
ResumePeerMismatch

Transfer Not Resumed
```

---

## Scenario J — Receiver Already Has Object

Resultado:

```text
TRANSFER_ALREADY_HAVE_OBJECT

No Payload Transfer

Logical Completion
```

---

## Scenario K — Whole Object Hash Failure

Resultado:

```text
FAILED

Final Object Not Published

Evidence Preserved
```

---

## Scenario L — Disk Full

Resultado:

```text
No ACK For Non-Durable Chunks

Transfer PAUSED or FAILED

No False Progress
```

---

## Scenario M — Transport Switch Multiple Times

```text
Wi-Fi Direct Epoch 0

BLE Fallback

Wi-Fi Direct Epoch 1

BLE Fallback

Wi-Fi Direct Epoch 2
```

Resultado:

```text
Same TransferId

Correct Durable Progress

No Committed Chunk Lost

COMPLETED
```

---

## Scenario N — Crash During Materialization

Resultado después de restart:

```text
Completion State Reconciled

No Corrupt Object Published

Materialization Retried or Transfer Failed Explicitly
```

---

# 97. Requisitos funcionales

### FR-001

El sistema MUST registrar objetos mediante streaming.

### FR-002

El sistema MUST calcular ObjectId criptográfico.

### FR-003

El sistema MUST crear manifest inmutable.

### FR-004

El sistema MUST utilizar chunking determinista.

### FR-005

El sistema MUST verificar manifestHash.

### FR-006

El sistema MUST persistir DurableTransfer.

### FR-007

TransferId MUST sobrevivir cambios de transporte.

### FR-008

TransferId MUST sobrevivir nuevas Secure Sessions.

### FR-009

TransferId MUST sobrevivir nuevos Encounters.

### FR-010

El sistema MUST verificar cada chunk.

### FR-011

Chunk inválido MUST NOT marcarse COMMITTED.

### FR-012

ACK MUST enviarse solo después de durable commit.

### FR-013

Commit duplicado MUST ser idempotente.

### FR-014

ACK duplicado MUST ser idempotente.

### FR-015

El receptor MUST mantener Missing Chunk Set.

### FR-016

El receptor MUST controlar backpressure.

### FR-017

El sender MUST respetar receiverWindow.

### FR-018

El sistema MUST soportar transport loss.

### FR-019

El sistema MUST soportar transport switching.

### FR-020

El sistema MUST soportar process death.

### FR-021

El sistema MUST reconciliar storage después de restart.

### FR-022

El sistema MUST descubrir transferencias reanudables por remoteNodeId.

### FR-023

Resumption MUST requerir el mismo authenticated NodeId.

### FR-024

Receiver committed state MUST ser autoritativo.

### FR-025

Resumption MUST retransmitir solo chunks faltantes.

### FR-026

El sistema MUST verificar el objeto completo.

### FR-027

COMPLETED MUST implicar objeto final durable y válido.

### FR-028

El sistema MUST materializar objetos mediante streaming.

### FR-029

El sistema MUST publicar objeto final atómicamente.

### FR-030

El sistema MUST soportar cancelación idempotente.

### FR-031

El sistema MUST soportar expiración.

### FR-032

El sistema MUST soportar garbage collection.

### FR-033

El sistema MUST aplicar storage quotas.

### FR-034

El sistema MUST emitir eventos.

### FR-035

El sistema MUST producir métricas.

### FR-036

Durable Transfer Core MUST ser independiente del transporte físico.

---

# 98. Requisitos no funcionales

### NFR-001 — Offline Sovereignty

El protocolo debe funcionar completamente offline.

### NFR-002 — Durability

ACK(COMMITTED) debe sobrevivir process death.

### NFR-003 — Idempotency

Duplicados no deben corromper progreso ni almacenamiento.

### NFR-004 — Bounded Memory

Objetos grandes deben procesarse mediante streaming.

### NFR-005 — Transport Independence

Transfer Core no debe depender de BLE ni Wi-Fi Direct.

### NFR-006 — Session Independence

Transferencia durable no debe depender de una Secure Session específica.

### NFR-007 — Encounter Independence

Transferencia durable no debe depender de un Encounter específico.

### NFR-008 — Integrity

Chunks y objeto completo deben verificarse criptográficamente.

### NFR-009 — Crash Consistency

El sistema debe recuperarse de process death en cualquier punto crítico.

### NFR-010 — Storage Safety

Metadata externa no debe controlar paths directamente.

### NFR-011 — Resource Bounding

Objetos, chunks, manifests, buffers y transferencias activas deben poseer límites.

### NFR-012 — Cross-Implementation Compatibility

ObjectId, ChunkId, ManifestHash y wire encodings deben poseer vectores interoperables.

### NFR-013 — Observability

Cada transición crítica debe producir evidencia observable.

### NFR-014 — Testability

El Core debe probarse sin radios, Android ni archivos reales.

---

# 99. Criterios de éxito

La SPEC será `ACCEPTED` cuando:

1. un objeto pueda registrarse mediante streaming;

2. ObjectId sea determinista;

3. manifest sea inmutable;

4. chunk layout sea determinista;

5. transfer offer sea autenticada por canal seguro;

6. receptor pueda aceptar o rechazar;

7. manifest se verifique antes de transferir payload;

8. chunks se procesen mediante streaming;

9. chunks corruptos se rechacen;

10. ACK ocurra únicamente después de durable commit;

11. commits duplicados sean idempotentes;

12. ACKs duplicados sean idempotentes;

13. progreso derive de chunks committed;

14. backpressure sea respetado;

15. Wi-Fi Direct loss no destruya TransferId;

16. fallback BLE permita continuar;

17. transport switch no destruya progreso;

18. process death no pierda chunks committed;

19. recovery reconstruya progreso;

20. reencuentro posterior descubra transferencia;

21. nueva Secure Session permita resume;

22. peer diferente no pueda reanudar;

23. receiver durable state sea autoritativo;

24. solo chunks faltantes sean retransmitidos;

25. objeto completo se verifique;

26. objeto final se materialice mediante streaming;

27. COMPLETED implique objeto durable válido;

28. disk full no produzca ACK falso;

29. garbage collection libere recursos correctamente;

30. Core no dependa del transporte.

---

# 100. Criterios de rechazo

La SPEC debe rechazarse si:

* TransferId depende de SecureSessionId;

* TransferId cambia después de fallback;

* TransferId cambia después de reencuentro;

* se envía ACK antes de durable commit;

* chunk corrupto puede quedar COMMITTED;

* duplicate chunk incrementa progreso dos veces;

* duplicate ACK incrementa progreso dos veces;

* sender state prevalece sobre receiver durable state;

* process death pierde progreso committed;

* reanudación requiere la misma conexión física;

* reanudación requiere la misma Secure Session;

* otro NodeId puede reanudar transferencia;

* objeto completo se carga en memoria;

* final object se publica antes de verificación completa;

* metadata remota controla filesystem paths;

* Core importa APIs BLE o Wi-Fi Direct.

---

# 101. Entregables

```text
/specs/007-durable-object-transfer/spec.md

/core/object-transfer-domain/

/core/object-transfer-application/

/core/object-transfer-contracts/

/core/object-transfer-testing/

/storage/transfer-storage-contracts/

/storage/fake-transfer-storage/

/docs/architecture/DURABLE-TRANSFER-MODEL.md

/docs/architecture/OBJECT-IDENTITY-AND-MANIFEST.md

/docs/protocol/TRANSFER-NEGOTIATION-PROTOCOL.md

/docs/protocol/CHUNK-TRANSFER-PROTOCOL.md

/docs/protocol/TRANSFER-RESUMPTION-PROTOCOL.md

/docs/storage/CHUNK-COMMIT-SEMANTICS.md

/docs/storage/TRANSFER-CRASH-CONSISTENCY.md

/docs/testing/DURABLE-TRANSFER-TEST-VECTORS.md

/docs/adr/ADR-020-OBJECT-ID-STRATEGY.md

/docs/adr/ADR-021-FIXED-SIZE-CHUNKING-V1.md

/docs/adr/ADR-022-DURABLE-CHUNK-ACK-SEMANTICS.md

/docs/adr/ADR-023-RECEIVER-AUTHORITATIVE-RESUMPTION.md

/docs/adr/ADR-024-ATOMIC-OBJECT-MATERIALIZATION.md
```

---

# 102. Decisiones abiertas

La implementación debe producir evidencia para decidir:

1. chunk size óptimo real;

2. tamaño diferente según transporte;

3. fixed-size frente a content-defined chunking;

4. SHA-256 frente a BLAKE3 para objetos y chunks;

5. lista completa de hashes frente a Merkle Tree;

6. manifests muy grandes;

7. bitmap comprimido;

8. Roaring Bitmap;

9. chunk request batching;

10. ACK acumulativos;

11. selective acknowledgements;

12. ACK compression;

13. adaptive receiver window;

14. retransmission timeout adaptativo;

15. scheduler basado en rareza o costo;

16. transferencia paralela de chunks;

17. múltiples Data Plane streams;

18. zero-copy I/O;

19. memory-mapped I/O;

20. deduplicación local global;

21. deduplicación cross-transfer;

22. partial object consumption;

23. streaming playback antes de completion;

24. directory manifests;

25. Merkle DAG;

26. encrypted objects at rest;

27. per-transfer authorization;

28. user confirmation policies;

29. transfer priority scheduling;

30. background execution Android;

31. power-aware transfer scheduling;

32. storage-aware admission control.

---

# 103. Relación con SPEC anteriores

```text
SPEC-001
HYBRID OFFLINE LINK SPIKE

        │

        ▼

Validates Physical Feasibility


SPEC-002
TRANSPORT-INDEPENDENT TRANSFER CORE

        │

        ▼

Defines Durable Transfer Abstraction


SPEC-003
DECENTRALIZED NODE IDENTITY

        │

        ▼

Defines Persistent Node Identity


SPEC-004
OFFLINE PEER DISCOVERY & ENCOUNTER

        │

        ▼

Finds And Authenticates Nearby Peers


SPEC-005
SECURE SESSION PROTOCOL

        │

        ▼

Protects Session Communication


SPEC-006
HYBRID TRANSPORT ORCHESTRATION

        │

        ▼

Provides Stable LogicalLink


SPEC-007
DURABLE OBJECT TRANSFER & RESUMPTION

        │

        ├── identifies objects cryptographically
        ├── creates immutable manifests
        ├── divides objects into chunks
        ├── commits chunks durably
        ├── acknowledges only durable state
        ├── survives transport loss
        ├── survives process death
        ├── survives new Secure Sessions
        ├── survives new Encounters
        ├── retransmits only missing chunks
        └── verifies and materializes final objects
```

---

# 104. Resultado final

```text
                         DURABLE TRANSFER

                                │

                                ▼

                         OBJECT MANIFEST

                                │

                                ▼

                         TRANSFER OFFER

                                │

                                ▼

                            ACCEPTED

                                │

                                ▼

                           CHUNK PLAN

                                │

                                ▼

                    ┌───────────────────────┐
                    │     LOGICAL LINK      │
                    └───────────┬───────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
                    ▼                       ▼

                  BLE                 WIFI DIRECT

              FALLBACK PATH            DATA PLANE

                    │                       │
                    └───────────┬───────────┘
                                │
                                ▼

                          CHUNK RECEIVED

                                │

                                ▼

                          HASH VERIFIED

                                │

                                ▼

                         DURABLE COMMIT

                                │

                                ▼

                               ACK

                                │

               ┌────────────────┼────────────────┐
               │                │                │
               ▼                ▼                ▼

         TRANSPORT LOSS   PROCESS DEATH    DEVICES SEPARATE

               │                │                │
               └────────────────┼────────────────┘
                                │
                                ▼

                       AUTHENTICATED REENCOUNTER

                                │

                                ▼

                         RESUMPTION SYNC

                                │

                                ▼

                         MISSING CHUNKS

                                │

                                ▼

                      WHOLE OBJECT VERIFY

                                │

                                ▼

                       ATOMIC MATERIALIZATION

                                │

                                ▼

                            COMPLETED
```

**La conexión puede desaparecer.**

**El transporte puede cambiar.**

**La sesión segura puede terminar.**

**El proceso puede morir.**

**Los dispositivos pueden separarse durante horas.**

**La transferencia conserva su identidad, su progreso durable y puede continuar desde los chunks faltantes cuando el mismo peer autenticado reaparece.**
