# SPEC-008 — Offline Messaging & Durable Delivery Protocol

**Estado:** Draft
**Tipo:** Core Messaging Protocol Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-002, SPEC-003, SPEC-004, SPEC-005, SPEC-006, SPEC-007
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Conectividad requerida:** Ninguna infraestructura externa
**Internet requerido:** No
**Servidor requerido:** No
**Cuenta requerida:** No
**Transporte específico requerido:** No

---

# 1. Propósito

Definir e implementar el protocolo durable mediante el cual dos nodos autenticados pueden intercambiar mensajes pequeños y medianos de forma:

* offline;
* persistente;
* autenticada;
* cifrada en tránsito;
* transport-independent;
* session-independent;
* encounter-independent;
* resistente a duplicados;
* resistente a desconexiones;
* resistente a transport switching;
* resistente a process death;
* reanudable;
* con entrega `at-least-once` a nivel físico;
* con procesamiento idempotente a nivel de protocolo;
* con estados de entrega explícitos;
* con ACK semántico;
* con expiración;
* con cancelación limitada;
* con control de presión;
* con límites de almacenamiento;
* observable;
* interoperable;
* testeable sin radios ni hardware real.

La SPEC define la transición:

```text
APPLICATION MESSAGE

        │

        ▼

MESSAGE CREATION

        │

        ▼

DURABLE OUTBOX COMMIT

        │

        ▼

DELIVERY SCHEDULING

        │

        ▼

AUTHENTICATED PEER AVAILABLE

        │

        ▼

MESSAGE OFFER / DELIVERY

        │

        ▼

DURABLE INBOX COMMIT

        │

        ▼

RECEIVED ACK

        │

        ▼

APPLICATION DELIVERY

        │

        ▼

PROCESSED ACK

        │

        ├──────────────┐
        │              │
        ▼              ▼

DISCONNECT        PROCESS DEATH

        │              │
        └───────┬──────┘
                │
                ▼

          STATE RECOVERY

                │

                ▼

        DELIVERY RECONCILIATION

                │

                ▼

          RETRY IF REQUIRED

                │

                ▼

              DONE
```

---

# 2. Problema fundamental

Después de SPEC-007 existe capacidad para transferir objetos durables.

Pero todavía no existe una abstracción adecuada para:

```text
Chat Messages

Commands

Application Events

Small Notifications

Protocol Messages

Task Requests

Task Results

State Updates

Receipts

Application-Level Signals
```

Utilizar `DurableTransfer` para cada mensaje produciría una abstracción incorrecta.

Los mensajes poseen semánticas diferentes:

```text
Object Transfer

    large payload
    chunking
    manifest
    byte-level progress
    resumable chunks


Messaging

    discrete logical envelope
    durable outbox
    durable inbox
    deduplication
    semantic acknowledgements
    expiration
    ordering
    application processing state
```

Por tanto:

> El sistema necesita una primitiva durable de mensajería independiente de las conexiones físicas, sesiones seguras y encuentros.

---

# 3. Objetivo arquitectónico

Construir:

```text
DURABLE OFFLINE MESSAGING CORE
```

que permita:

```text
Create Message

Persist Outbox

Schedule Delivery

Deliver Through LogicalLink

Persist Inbox

Deduplicate

Acknowledge Durable Receipt

Deliver To Application

Acknowledge Application Processing

Retry After Disconnect

Recover After Process Death

Resume After Reencounter

Expire Messages

Garbage Collect Terminal State
```

---

# 4. Principio arquitectónico fundamental

```text
MESSAGE ≠ FRAME ≠ SESSION ≠ LOGICAL LINK ≠ PHYSICAL LINK
```

Un `DurableMessage` puede atravesar:

```text
Secure Session #1

LogicalLink #1

Wi-Fi Direct

BLE Fallback

Process Death

Encounter #2

Secure Session #2

LogicalLink #2
```

manteniendo:

```text
Same MessageId
```

---

# 5. Segundo principio fundamental

```text
SENT ≠ RECEIVED ≠ PROCESSED
```

Estados semánticos distintos:

```text
SENT

Sender transmitted message bytes.


RECEIVED

Receiver durably persisted message.


PROCESSED

Receiver application durably completed processing.
```

Estos estados MUST NOT confundirse.

---

# 6. Tercer principio fundamental

```text
ACK(RECEIVED)

MUST IMPLY

MESSAGE RECOVERABLE AFTER PROCESS RESTART
```

El receptor no puede confirmar recepción durable mientras el mensaje exista únicamente:

```text
in socket

in memory

in temporary buffer

in application callback
```

---

# 7. Cuarto principio fundamental

```text
NETWORK DELIVERY MAY BE DUPLICATED

INBOX COMMIT MUST BE IDEMPOTENT
```

El mismo `MessageId` puede recibirse múltiples veces.

Solo debe existir una entrada lógica durable.

---

# 8. Quinto principio fundamental

```text
PROTOCOL EXACTLY-ONCE DELIVERY IS NOT CLAIMED
```

La SPEC garantiza:

```text
At-Least-Once Physical Delivery Possibility

+

Durable Inbox Deduplication

+

Idempotent Protocol State Transitions

+

Application Processing Contract
```

No garantiza exactamente una vez para efectos externos arbitrarios.

---

# 9. Objetivos funcionales

Al finalizar esta SPEC, el sistema debe poder:

1. crear mensajes;

2. asignar MessageId estable;

3. persistir mensajes en Outbox antes de intentar envío;

4. enviar mensajes a un NodeId autenticado;

5. conservar mensajes cuando peer no está disponible;

6. descubrir mensajes pendientes al reencontrar al peer;

7. transmitir mensajes mediante LogicalLink;

8. validar envelope;

9. persistir mensaje en Inbox;

10. deduplicar MessageId;

11. enviar ACK(RECEIVED) únicamente después de durable commit;

12. entregar mensajes a aplicación;

13. registrar procesamiento exitoso;

14. enviar ACK(PROCESSED);

15. distinguir RECEIVED de PROCESSED;

16. retransmitir mensajes no confirmados;

17. sobrevivir a pérdida de transporte;

18. sobrevivir a cambio de transporte;

19. sobrevivir a process death;

20. continuar durante una nueva Secure Session;

21. continuar durante un nuevo Encounter;

22. expirar mensajes;

23. cancelar mensajes no entregados cuando sea posible;

24. controlar backpressure;

25. aplicar cuotas;

26. producir eventos y métricas.

---

# 10. Resultado esperado

```text
DURABLE MESSAGING CORE
────────────────────────────────────

Message Identity

Message Envelope

Durable Outbox

Durable Inbox

Message Creation

Message Scheduling

Delivery Protocol

Durable Receive Commit

Inbox Deduplication

Received ACK

Application Delivery

Processing Lease

Processed ACK

Delivery Reconciliation

Retry Protocol

Peer Reencounter Delivery

Message Expiration

Message Cancellation

Dead Letter State

Backpressure

Storage Quotas

Crash Recovery

Garbage Collection

Messaging Events

Messaging Metrics

Protocol Test Vectors
```

---

# 11. Fuera de alcance

Esta SPEC no implementará:

* group messaging;
* broadcast messaging;
* multicast;
* pub/sub;
* topics;
* channels;
* chat rooms;
* multi-device user identity;
* user accounts;
* centralized mailbox;
* Internet push notifications;
* multi-hop message routing;
* relay;
* store-carry-forward mediante nodos intermedios;
* gossip;
* epidemic dissemination;
* distributed consensus;
* global total ordering;
* exactly-once external side effects;
* CRDT;
* collaborative state synchronization;
* message search;
* full-text indexing;
* E2EE entre usuarios por encima de Node Identity;
* disappearing-message UX;
* attachment transfer dentro del message payload;
* large payload transport.

Objetos grandes deben utilizar SPEC-007.

---

# 12. Arquitectura

```text
┌──────────────────────────────────────────────┐
│               APPLICATION / SDK              │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│            DURABLE MESSAGING SERVICE         │
│                                              │
│ createMessage()                              │
│ enqueueMessage()                             │
│ cancelMessage()                              │
│ getMessageStatus()                           │
│ subscribeInbox()                             │
│ markProcessed()                              │
│ retryMessage()                               │
│ recover()                                    │
└──────────────┬────────────────┬──────────────┘
               │                │
               ▼                ▼
┌──────────────────────┐ ┌─────────────────────┐
│ DURABLE OUTBOX       │ │ DURABLE INBOX       │
│                      │ │                     │
│ Pending              │ │ Received            │
│ Scheduled            │ │ Deduplicated        │
│ In Flight            │ │ Processing          │
│ Received Remote      │ │ Processed           │
│ Processed Remote     │ │ Dead Letter         │
│ Expired              │ │ Expired             │
└──────────┬───────────┘ └──────────┬──────────┘
           │                        │
           └────────────┬───────────┘
                        │
                        ▼
┌──────────────────────────────────────────────┐
│           MESSAGE DELIVERY PROTOCOL          │
│                                              │
│ Offer / Batch Summary                        │
│ Message Data                                 │
│ Received ACK                                 │
│ Processed ACK                                │
│ Reconciliation                               │
│ Retry                                        │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│               LOGICAL LINK PORT              │
└──────────────────────────────────────────────┘
```

---

# 13. Bounded Context

La SPEC define:

```text
OfflineMessaging
```

Entidades:

```text
DurableMessage

OutboxMessage

InboxMessage

MessageDeliveryAttempt

MessageProcessingLease

PeerMessageProgress

MessageEvent
```

Value Objects:

```text
MessageId

MessageType

MessagePayloadHash

MessagePriority

MessageDirection

OutboxState

InboxState

DeliveryState

ProcessingState

MessageExpiration

DeliveryAttemptId

ProcessingLeaseId

MessageFailureReason
```

Servicios:

```text
MessageCreationService

OutboxService

InboxService

MessageSchedulingService

MessageDeliveryService

MessageReceiveService

MessageDeduplicationService

MessageAcknowledgementService

MessageProcessingService

MessageReconciliationService

MessageRecoveryService

MessageExpirationService

MessageGarbageCollectionService
```

---

# 14. MessageId

Formato inicial recomendado:

```text
UUIDv7
```

Propiedades:

```text
Globally Unique With Overwhelming Probability

Stable Across Retries

Stable Across Transport Switches

Stable Across Secure Sessions

Stable Across Encounters

Independent From Payload Hash
```

Un retry MUST reutilizar el mismo MessageId.

---

# 15. Message Payload Hash

```text
payloadHash =
SHA-256(
    MESSAGE_PAYLOAD_DOMAIN_TAG
    ||
    payloadLength
    ||
    payloadBytes
)
```

Domain Tag:

```text
OFFLINE_PROTOCOL_MESSAGE_PAYLOAD_V1
```

---

# 16. DurableMessage

```text
DurableMessage
────────────────────────────────

messageId

senderNodeId

receiverNodeId

messageType

payloadLength

payloadHash

createdAt

expiresAt

priority

deliveryRequirement

applicationMetadata
```

El payload puede almacenarse separadamente.

---

# 17. Message Envelope

```text
MessageEnvelope
────────────────────────────────

protocolVersion

messageId

senderNodeId

receiverNodeId

messageType

createdAt

expiresAt

priority

payloadLength

payloadHash

applicationMetadata

payload
```

---

# 18. Message Size

La SPEC debe definir:

```text
MAX_MESSAGE_PAYLOAD_SIZE
```

Recomendación inicial:

```text
256 KiB
```

Payloads superiores deben utilizar:

```text
SPEC-007 Durable Object Transfer
```

y referenciar `ObjectId` desde el mensaje.

---

# 19. Application Metadata

Debe ser:

```text
bounded

strictly encoded

treated as untrusted
```

No debe controlar directamente:

```text
filesystem paths

code execution

authorization

routing outside authenticated NodeId

dynamic class loading
```

---

# 20. Message Types

`messageType` debe ser namespaced.

Formato conceptual:

```text
applicationNamespace/messageType/version
```

Ejemplos:

```text
chat/text/v1

task/request/v1

task/result/v1

sync/invalidate/v1
```

Tipos reservados:

```text
protocol/*
```

---

# 21. Delivery Requirement

Valores iniciales:

```text
RECEIVED

PROCESSED
```

Semántica:

```text
RECEIVED

Sender considers delivery successful when remote durable inbox commit is confirmed.


PROCESSED

Sender considers delivery successful only when remote application processing is confirmed.
```

---

# 22. Durable Outbox

Regla:

```text
APPLICATION SEND SUCCESS

MUST IMPLY

MESSAGE DURABLY COMMITTED TO OUTBOX
```

No debe devolverse éxito al caller si el mensaje existe solo en memoria.

---

# 23. OutboxMessage

```text
OutboxMessage
────────────────────────────────

messageId

remoteNodeId

state

deliveryRequirement

payloadReference

payloadHash

priority

createdAt

expiresAt

scheduledAt

firstAttemptAt

lastAttemptAt

receivedAckAt

processedAckAt

attemptCount

nextRetryAt

failureReason
```

---

# 24. Outbox State Machine

Estados:

```text
CREATED

QUEUED

SCHEDULED

IN_FLIGHT

REMOTE_RECEIVED

REMOTE_PROCESSED

RETRY_PENDING

PAUSED

CANCEL_PENDING

CANCELLED

EXPIRED

DEAD_LETTER

FAILED
```

---

# 25. Outbox Transitions

```text
CREATED
   │
   ▼
QUEUED
   │
   ▼
SCHEDULED
   │
   ▼
IN_FLIGHT
   │
   ├──── link loss ─────────────► RETRY_PENDING
   │
   ├──── timeout ───────────────► RETRY_PENDING
   │
   ├──── ACK(RECEIVED) ─────────► REMOTE_RECEIVED
   │
   └──── terminal error ────────► FAILED

REMOTE_RECEIVED
   │
   ├──── requirement RECEIVED ──► TERMINAL SUCCESS
   │
   ├──── ACK(PROCESSED) ────────► REMOTE_PROCESSED
   │
   ├──── expiration ────────────► EXPIRED
   │
   └──── processing failure ────► DEAD_LETTER / FAILED

RETRY_PENDING
   │
   ├──── peer available ────────► SCHEDULED
   ├──── expiration ────────────► EXPIRED
   └──── retry policy exceeded ─► DEAD_LETTER
```

---

# 26. Durable Inbox

Regla:

```text
ACK(RECEIVED)

MUST BE SENT

ONLY AFTER

INBOX DURABLE COMMIT
```

---

# 27. InboxMessage

```text
InboxMessage
────────────────────────────────

messageId

remoteNodeId

state

payloadReference

payloadHash

messageType

createdAt

receivedAt

committedAt

processingStartedAt

processedAt

expiresAt

deliveryRequirement

deliveryCount

processingAttemptCount

lastProcessingError

failureReason
```

---

# 28. Inbox State Machine

Estados:

```text
RECEIVING

VERIFYING

COMMITTING

RECEIVED

DELIVERY_PENDING

PROCESSING

PROCESSED

PROCESSING_RETRY_PENDING

DEAD_LETTER

EXPIRED

FAILED
```

---

# 29. Inbox Transitions

```text
RECEIVING
   │
   ▼
VERIFYING
   │
   ├──── invalid ───────────────► FAILED
   ▼
COMMITTING
   │
   ├──── storage failure ───────► FAILED
   ▼
RECEIVED
   │
   ▼
DELIVERY_PENDING
   │
   ▼
PROCESSING
   │
   ├──── success ───────────────► PROCESSED
   ├──── retryable failure ─────► PROCESSING_RETRY_PENDING
   └──── terminal failure ──────► DEAD_LETTER
```

---

# 30. Delivery Protocol Overview

```text
NODE A                              NODE B

   │                                  │
   │──── MESSAGE_OFFER ──────────────►│
   │                                  │
   │◄─── MESSAGE_REQUEST ─────────────│
   │                                  │
   │──── MESSAGE_DATA ───────────────►│
   │                                  │
   │        DURABLE INBOX COMMIT       │
   │                                  │
   │◄─── MESSAGE_RECEIVED_ACK ─────────│
   │                                  │
   │        APPLICATION PROCESSING     │
   │                                  │
   │◄─── MESSAGE_PROCESSED_ACK ────────│
```

Para mensajes pequeños, implementación futura puede optimizar:

```text
OFFER + DATA
```

en un solo frame.

La semántica no debe cambiar.

---

# 31. MESSAGE_OFFER

```text
MessageOffer
────────────────────────────────

protocolVersion

messageId

senderNodeId

receiverNodeId

messageType

payloadLength

payloadHash

createdAt

expiresAt

priority

deliveryRequirement

offerNonce
```

---

# 32. Offer Validation

El receptor debe:

1. validar protocolVersion;

2. validar senderNodeId;

3. validar receiverNodeId;

4. comparar senderNodeId con peer autenticado;

5. validar MessageId;

6. validar payloadLength;

7. validar expiresAt;

8. validar messageType;

9. validar metadata limits;

10. aplicar Inbox quotas;

11. consultar deduplication state;

12. responder según estado existente.

---

# 33. Duplicate Offer Handling

Si MessageId ya existe:

```text
IF InboxState == RECEIVED

→ resend ACK(RECEIVED)


IF InboxState == PROCESSED

→ resend ACK(RECEIVED)
→ resend ACK(PROCESSED)


IF InboxState == PROCESSING

→ resend ACK(RECEIVED)


IF InboxState == DEAD_LETTER

→ send MESSAGE_PROCESSING_FAILED


IF payloadHash differs

→ MESSAGE_ID_CONFLICT
```

---

# 34. MESSAGE_REQUEST

```text
MessageRequest
────────────────────────────────

messageId

requestNonce

receiverWindow
```

---

# 35. MESSAGE_DATA

```text
MessageData
────────────────────────────────

protocolVersion

messageId

senderNodeId

receiverNodeId

payloadLength

payloadHash

payload
```

Debe viajar mediante:

```text
LogicalLink
```

---

# 36. Receive Pipeline

```text
MESSAGE_DATA

      │

      ▼

Bounded Frame Validation

      │

      ▼

Strict Parse

      │

      ▼

Authenticated Peer Validation

      │

      ▼

MessageId Validation

      │

      ▼

Envelope Validation

      │

      ▼

Payload Length Validation

      │

      ▼

Payload Hash Verification

      │

      ▼

Deduplication Check

      │

      ▼

Durable Inbox Commit

      │

      ▼

ACK(RECEIVED)

      │

      ▼

Application Delivery Scheduling
```

---

# 37. Payload Streaming

Mensajes cercanos al límite máximo deben poder procesarse mediante streaming.

El Core MUST NOT requerir:

```text
MAX_MESSAGE_PAYLOAD_SIZE × MAX_CONCURRENT_MESSAGES
```

en memoria.

---

# 38. Inbox Commit Order

Orden obligatorio:

```text
Receive Payload

      │

      ▼

Persist Temporary Payload

      │

      ▼

Flush / Durability Barrier

      │

      ▼

Verify Payload Hash

      │

      ▼

Commit Inbox Metadata

      │

      ▼

Publish Inbox State RECEIVED

      │

      ▼

Send ACK(RECEIVED)
```

---

# 39. Message Deduplication

Clave inicial:

```text
(senderNodeId, messageId)
```

No únicamente:

```text
messageId
```

Esto evita conflictos accidentales entre namespaces de nodos.

---

# 40. MessageId Conflict

Si:

```text
Same senderNodeId

+

Same MessageId

+

Different payloadHash
```

Resultado:

```text
MESSAGE_ID_CONFLICT
```

El mensaje nuevo debe rechazarse.

Debe emitirse evento de seguridad.

---

# 41. MESSAGE_RECEIVED_ACK

```text
MessageReceivedAck
────────────────────────────────

messageId

receiverNodeId

payloadHash

receivedAt

ackSequence
```

Semántica:

```text
Remote durable inbox commit confirmed.
```

---

# 42. MESSAGE_PROCESSED_ACK

```text
MessageProcessedAck
────────────────────────────────

messageId

receiverNodeId

payloadHash

processedAt

processingResultCode

ackSequence
```

Semántica:

```text
Remote application processing completed durably.
```

---

# 43. ACK Authentication

Los ACK viajan dentro del Secure Control Plane.

Deben validarse contra:

```text
Authenticated Remote NodeId

MessageId

PayloadHash

Expected Delivery State
```

---

# 44. ACK Sequence

Cada peer mantiene secuencia monotónica por relación de mensajería.

```text
messageAckSequence
```

Debe sobrevivir process death.

---

# 45. ACK Idempotency

ACK duplicado:

```text
MUST NOT

advance state twice

increment metrics twice

trigger duplicate application completion callbacks
```

---

# 46. ACK Regression

Un ACK antiguo:

```text
MAY BE IGNORED
```

Un ACK que contradice estado durable:

```text
MUST BE REJECTED
```

---

# 47. Application Delivery Contract

La aplicación consume mensajes mediante:

```text
MessageHandler
```

Contrato:

```text
handle(
    messageContext,
    payloadStream
) -> ProcessingResult
```

---

# 48. ProcessingResult

```text
SUCCESS

RETRYABLE_FAILURE

TERMINAL_FAILURE
```

---

# 49. Processing Success

Antes de marcar:

```text
PROCESSED
```

el sistema debe asegurar que el resultado de procesamiento requerido por el contrato de aplicación es durable.

La aplicación debe poder coordinar:

```text
Application State Commit

+

Inbox Processed State
```

mediante mecanismo transaccional o patrón idempotente.

---

# 50. Exactly-Once Caveat

Si una aplicación:

1. realiza un efecto externo;

2. process muere;

3. antes de persistir PROCESSED;

el mensaje puede procesarse nuevamente.

Por tanto, aplicaciones deben utilizar:

```text
Idempotency Key = MessageId
```

para efectos repetibles.

---

# 51. Processing Lease

Para evitar procesamiento concurrente duplicado:

```text
MessageProcessingLease
────────────────────────────────

processingLeaseId

messageId

ownerId

acquiredAt

expiresAt

heartbeatAt

attemptNumber
```

---

# 52. Lease Rules

Solo un handler puede poseer lease activo por mensaje.

Después de process death:

```text
lease expires

message returns to PROCESSING_RETRY_PENDING
```

---

# 53. Processing Retry

Política configurable:

```text
PROCESSING_MAX_ATTEMPTS

PROCESSING_RETRY_BACKOFF

PROCESSING_LEASE_TIMEOUT
```

---

# 54. Dead Letter

Mensajes que no pueden procesarse después de política de retry:

```text
DEAD_LETTER
```

Debe conservar:

```text
Message Metadata

Payload Reference

Attempt Count

Last Error

Timestamps

Audit Events
```

según retention policy.

---

# 55. Sender Delivery Success

Para:

```text
deliveryRequirement = RECEIVED
```

éxito terminal:

```text
REMOTE_RECEIVED
```

Para:

```text
deliveryRequirement = PROCESSED
```

éxito terminal:

```text
REMOTE_PROCESSED
```

---

# 56. Peer Offline Behavior

Si remoteNodeId no está disponible:

```text
Message Remains QUEUED

or

RETRY_PENDING
```

No debe fallar únicamente porque el peer está offline.

---

# 57. Peer Availability Integration

Después de SPEC-004 / SPEC-006:

```text
AUTHENTICATED PEER AVAILABLE

        │

        ▼

MessageSchedulingService.onPeerAvailable(remoteNodeId)

        │

        ▼

Load Pending Outbox Messages

        │

        ▼

Apply Expiration

        │

        ▼

Apply Priority

        │

        ▼

Apply Receiver Window

        │

        ▼

Schedule Delivery
```

---

# 58. Reencounter Delivery

Un mensaje puede crearse:

```text
Monday 10:00
```

sin peer disponible.

Los nodos se encuentran:

```text
Monday 18:00
```

Resultado:

```text
New Encounter

New Secure Session

New LogicalLink

Same MessageId

Outbox Message Delivered
```

---

# 59. Session Independence

OutboxMessage no debe persistir:

```text
SecureSessionId as ownership identity
```

Puede registrar session IDs únicamente como observabilidad histórica.

---

# 60. LogicalLink Independence

Un mensaje puede intentar envío mediante múltiples LogicalLinks.

MessageId permanece igual.

---

# 61. Transport Independence

MessageDeliveryService usa:

```text
LogicalLinkHandle
```

No:

```text
BLE

Wi-Fi Direct

Socket

IP Address
```

---

# 62. Transport Loss During Delivery

Si transporte falla antes de ACK(RECEIVED):

```text
IN_FLIGHT

→

RETRY_PENDING
```

El mensaje puede retransmitirse.

Inbox deduplication evita doble commit lógico.

---

# 63. Transport Loss After Inbox Commit Before ACK

Caso:

```text
Receiver Durable Commit

Connection Lost

ACK(RECEIVED) Not Delivered
```

Sender retransmite.

Receiver detecta duplicado.

Resultado:

```text
No Duplicate Inbox Entry

ACK(RECEIVED) Resent
```

---

# 64. Process Death Sender

```text
Message Durable In Outbox

Process Dies

Restart

recover()
```

Resultado:

```text
Non-Terminal Outbox Messages Loaded

IN_FLIGHT → RETRY_PENDING

Scheduling Resumes When Peer Available
```

---

# 65. Process Death Receiver Before Commit

Resultado:

```text
No ACK(RECEIVED)

Temporary Payload Reconciled

Message Retransmitted
```

---

# 66. Process Death Receiver After Commit Before ACK

Resultado:

```text
Inbox Message Recovered As RECEIVED

Sender Retransmits

Receiver Deduplicates

ACK(RECEIVED) Resent
```

---

# 67. Process Death During Processing

Resultado:

```text
Processing Lease Eventually Expires

Message Returns To PROCESSING_RETRY_PENDING

Handler May Execute Again

MessageId Used For Application Idempotency
```

---

# 68. Delivery Reconciliation

Después de establecer Secure Session con peer conocido:

```text
MESSAGE_SYNC_SUMMARY
```

permite reconciliar estados pendientes.

---

# 69. MESSAGE_SYNC_SUMMARY

```text
MessageSyncSummary
────────────────────────────────

protocolVersion

senderNodeId

receiverNodeId

outboxPendingSummary

inboxReceiptSummary

inboxProcessedSummary

summaryVersion

summaryNonce
```

---

# 70. Reconciliation Scope

La v1 no intercambiará toda la base histórica.

Debe limitarse a:

```text
Non-Terminal Messages

Recent Terminal Receipts

Configured Reconciliation Window
```

---

# 71. Message State Query

```text
MESSAGE_STATE_QUERY
────────────────────────────────

messageIds[]

queryNonce
```

---

# 72. Message State Response

```text
MESSAGE_STATE_RESPONSE
────────────────────────────────

states[]
```

Cada estado:

```text
messageId

payloadHash

receiverState

receivedAt

processedAt
```

---

# 73. Receiver State Authority

Para estado de recepción:

```text
RECEIVER DURABLE INBOX STATE

IS AUTHORITATIVE
```

Para estado de envío:

```text
SENDER DURABLE OUTBOX STATE

IS AUTHORITATIVE
```

La reconciliación debe converger sin borrar evidencia durable válida.

---

# 74. Retry Policy

Inputs:

```text
Message Priority

Attempt Count

Peer Availability

Previous Failure

Expiration

Receiver Window

Backoff State
```

Output:

```text
nextRetryAt

retryAllowed

reasonCode
```

---

# 75. Retry Backoff

Política inicial:

```text
Exponential Backoff

+

Bounded Random Jitter

+

Maximum Retry Delay
```

No debe utilizar retry agresivo mientras peer está ausente.

---

# 76. Message Priority

Valores iniciales:

```text
LOW

NORMAL

HIGH

URGENT
```

`URGENT` no puede ignorar:

```text
Security Limits

Receiver Window

Storage Quotas

Resource Bounds
```

---

# 77. Scheduling v1

Orden inicial:

```text
Priority Descending

Then CreatedAt Ascending

Then MessageId Canonical Order
```

Debe ser determinista.

---

# 78. Backpressure

El receptor anuncia:

```text
MessageReceiverWindow
────────────────────────────────

maxInFlightMessages

maxInFlightBytes

availableMessageSlots

availableBytes

updatedAt
```

Sender MUST respetarlo.

---

# 79. Inbox Quotas

Configurable:

```text
MAX_INBOX_MESSAGES

MAX_INBOX_BYTES

MAX_INBOX_MESSAGES_PER_PEER

MAX_INBOX_BYTES_PER_PEER

MAX_UNPROCESSED_MESSAGES

MAX_DEAD_LETTER_MESSAGES
```

---

# 80. Outbox Quotas

Configurable:

```text
MAX_OUTBOX_MESSAGES

MAX_OUTBOX_BYTES

MAX_OUTBOX_MESSAGES_PER_PEER

MAX_OUTBOX_BYTES_PER_PEER
```

---

# 81. Quota Admission

Antes de aceptar MESSAGE_DATA:

```text
Reserve Inbox Capacity
```

Debe evitar:

```text
Storage Exhaustion

Memory Exhaustion

Single Peer Resource Monopoly
```

---

# 82. Message Expiration

`expiresAt` debe persistirse.

Un mensaje expirado:

```text
MUST NOT

be newly delivered to application
```

después de detectar expiración.

---

# 83. Clock Assumptions

Los dispositivos pueden tener relojes diferentes.

Por tanto:

```text
expiresAt
```

es una política local aproximada en v1.

No se garantiza expiración global simultánea.

Debe documentarse `MAX_CLOCK_SKEW_TOLERANCE`.

---

# 84. Expiration Sender Side

Si mensaje expira antes de ACK requerido:

```text
→ EXPIRED
```

Debe detenerse scheduling futuro.

---

# 85. Expiration Receiver Side

Si mensaje ya fue recibido durablemente pero expira antes de processing:

política configurable:

```text
DROP_EXPIRED_UNPROCESSED

or

PROCESS_ALREADY_RECEIVED
```

Default inicial:

```text
DROP_EXPIRED_UNPROCESSED
```

---

# 86. Cancellation

Cancelar mensaje no equivale a borrar un mensaje remoto ya recibido.

---

# 87. MESSAGE_CANCEL

```text
MessageCancel
────────────────────────────────

messageId

senderNodeId

payloadHash

reasonCode

cancelNonce
```

---

# 88. Cancellation Semantics

Si receptor aún no recibió mensaje:

```text
cancel local scheduling
```

Si receptor recibió pero no procesó:

```text
best-effort remote cancellation
```

Si receptor ya procesó:

```text
TOO_LATE
```

No existe rollback automático de efectos de aplicación.

---

# 89. Cancellation States

```text
CANCEL_PENDING

CANCELLED

CANCEL_REJECTED

TOO_LATE
```

---

# 90. Message Ordering

La v1 garantiza:

```text
NO GLOBAL ORDERING
```

y:

```text
NO STRICT PER-PEER DELIVERY ORDER
```

por defecto.

---

# 91. Optional Sender Sequence

Envelope puede incluir:

```text
senderSequence
```

monotónico por peer o stream lógico.

Permite a aplicaciones detectar:

```text
gaps

reordering

duplicates
```

No obliga al Core a bloquear entrega esperando mensajes faltantes.

---

# 92. Causal Metadata

Reservado para futuras SPEC:

```text
causalContext
```

No se implementa causal delivery en v1.

---

# 93. Large Attachments

Mensajes con archivos deben utilizar:

```text
SPEC-007 ObjectId
```

Ejemplo conceptual:

```text
messageType = chat/attachment/v1

payload = {
    objectId,
    manifestHash,
    mediaType,
    caption
}
```

El mensaje no contiene el objeto grande.

---

# 94. Security Requirements

La SPEC debe proteger contra:

```text
Message Spoofing

MessageId Collision Abuse

MessageId Conflict

Payload Modification

Fake ACK

ACK Replay

Peer Substitution

Oversized Messages

Inbox Flooding

Outbox Flooding

Processing Lease Abuse

Malformed Metadata

Unknown Message Types

Path Traversal

Resource Exhaustion

Retry Amplification
```

---

# 95. Unknown Message Type

Política inicial:

```text
Receive Durably

ACK(RECEIVED)

Do Not Process

Move To DEAD_LETTER or UNSUPPORTED
```

según configuración.

No debe descartarse silenciosamente después de ACK(RECEIVED).

---

# 96. Protocol Frame Types

```text
MESSAGE_OFFER

MESSAGE_REQUEST

MESSAGE_DATA

MESSAGE_RECEIVED_ACK

MESSAGE_PROCESSED_ACK

MESSAGE_PROCESSING_FAILED

MESSAGE_CANCEL

MESSAGE_CANCEL_ACK

MESSAGE_SYNC_SUMMARY

MESSAGE_STATE_QUERY

MESSAGE_STATE_RESPONSE

MESSAGE_ERROR
```

---

# 97. Error Taxonomy

```text
OfflineMessagingError

├── MessageCreationError
│   ├── InvalidMessageType
│   ├── MessagePayloadTooLarge
│   ├── MessageExpirationInvalid
│   ├── MessageMetadataTooLarge
│   └── MessageCreationFailure
│
├── OutboxError
│   ├── OutboxQuotaExceeded
│   ├── OutboxCommitFailure
│   ├── OutboxMessageNotFound
│   ├── OutboxStateConflict
│   └── OutboxPayloadUnavailable
│
├── InboxError
│   ├── InboxQuotaExceeded
│   ├── InboxCommitFailure
│   ├── InboxMessageNotFound
│   ├── InboxStateConflict
│   ├── InboxPayloadUnavailable
│   └── InboxCapacityReservationFailure
│
├── DeliveryError
│   ├── MessagePeerMismatch
│   ├── MessageIdConflict
│   ├── MessagePayloadHashMismatch
│   ├── MessagePayloadLengthMismatch
│   ├── MessageDeliveryTimeout
│   ├── MessageDeliveryInterrupted
│   └── MessageReceiverWindowExceeded
│
├── AcknowledgementError
│   ├── InvalidMessageAck
│   ├── MessageAckPeerMismatch
│   ├── MessageAckPayloadMismatch
│   ├── MessageAckSequenceRegression
│   ├── UnexpectedReceivedAck
│   └── UnexpectedProcessedAck
│
├── ProcessingError
│   ├── MessageHandlerNotFound
│   ├── ProcessingLeaseUnavailable
│   ├── ProcessingLeaseExpired
│   ├── ProcessingRetryExceeded
│   ├── ApplicationProcessingFailure
│   └── ProcessingStateConflict
│
├── ReconciliationError
│   ├── MessageSyncPeerMismatch
│   ├── InvalidSyncSummary
│   ├── MessageStateConflict
│   ├── ReconciliationWindowExceeded
│   └── MessageReconciliationFailure
│
├── CancellationError
│   ├── MessageCancellationTooLate
│   ├── MessageCancellationRejected
│   ├── MessageCancellationConflict
│   └── InvalidMessageCancellation
│
├── ExpirationError
│   ├── MessageExpired
│   ├── InvalidExpirationPolicy
│   └── ClockSkewPolicyViolation
│
└── RecoveryError
    ├── MessagingRecoveryFailure
    ├── OrphanOutboxPayload
    ├── OrphanInboxPayload
    ├── MissingOutboxPayload
    ├── MissingInboxPayload
    ├── StaleProcessingLease
    └── MessagingStateInconsistency
```

---

# 98. Persistencia

Persistir:

```text
Durable Message Metadata

Outbox State

Inbox State

Payload References

Payload Hashes

Delivery Attempts

ACK Sequences

Received ACK State

Processed ACK State

Processing Leases

Processing Attempts

Retry State

Expiration State

Cancellation State

Dead Letter State

Reconciliation State

Message Events
```

No persistir:

```text
Secure Session Traffic Keys

Data Plane Keys

Transport Binding Secrets

Physical Transport Handles
```

---

# 99. Crash Recovery

`recover()` debe:

1. cargar Outbox no terminal;

2. cargar Inbox no terminal;

3. marcar `IN_FLIGHT` como `RETRY_PENDING`;

4. reconciliar payload storage;

5. detectar payloads huérfanos;

6. detectar metadata sin payload;

7. expirar processing leases vencidos;

8. mover mensajes PROCESSING abandonados a retry;

9. reconstruir cuotas;

10. validar ACK sequences;

11. ejecutar expiration policy;

12. preservar estados terminales;

13. emitir eventos de recovery.

---

# 100. Garbage Collection

Debe existir:

```text
MessageGarbageCollectionService
```

Responsabilidades:

```text
Expired Outbox Messages

Expired Inbox Messages

Old REMOTE_RECEIVED Messages

Old REMOTE_PROCESSED Messages

Old PROCESSED Inbox Messages

Dead Letters Beyond Retention

Orphan Payloads

Old Delivery Attempts

Old Reconciliation State
```

---

# 101. Eventos

Eventos mínimos:

```text
MESSAGE_CREATED

MESSAGE_OUTBOX_COMMITTED

MESSAGE_QUEUED

MESSAGE_SCHEDULED

MESSAGE_DELIVERY_STARTED

MESSAGE_OFFER_SENT

MESSAGE_OFFER_RECEIVED

MESSAGE_REQUESTED

MESSAGE_DATA_SENT

MESSAGE_DATA_RECEIVED

MESSAGE_PAYLOAD_VERIFIED

MESSAGE_INBOX_COMMIT_STARTED

MESSAGE_INBOX_COMMITTED

MESSAGE_DUPLICATE_DETECTED

MESSAGE_ID_CONFLICT_DETECTED

MESSAGE_RECEIVED_ACK_SENT

MESSAGE_RECEIVED_ACK_RECEIVED

MESSAGE_APPLICATION_DELIVERY_SCHEDULED

MESSAGE_PROCESSING_STARTED

MESSAGE_PROCESSING_SUCCEEDED

MESSAGE_PROCESSING_RETRY_SCHEDULED

MESSAGE_PROCESSING_FAILED

MESSAGE_DEAD_LETTERED

MESSAGE_PROCESSED_ACK_SENT

MESSAGE_PROCESSED_ACK_RECEIVED

MESSAGE_DELIVERY_INTERRUPTED

MESSAGE_RETRY_SCHEDULED

MESSAGE_RECONCILIATION_STARTED

MESSAGE_RECONCILIATION_COMPLETED

MESSAGE_EXPIRED

MESSAGE_CANCELLATION_REQUESTED

MESSAGE_CANCELLED

MESSAGE_CANCELLATION_TOO_LATE

MESSAGE_RECOVERY_STARTED

MESSAGE_RECOVERY_COMPLETED

MESSAGE_GARBAGE_COLLECTION_STARTED

MESSAGE_GARBAGE_COLLECTION_COMPLETED
```

---

# 102. Métricas

```text
messages_created_total

messages_outbox_committed_total

messages_delivery_started_total

messages_sent_total

messages_received_total

messages_inbox_committed_total

messages_duplicate_received_total

message_id_conflicts_total

message_received_acks_sent_total

message_received_acks_received_total

message_processed_acks_sent_total

message_processed_acks_received_total

messages_processed_total

message_processing_retries_total

message_processing_failures_total

messages_dead_lettered_total

message_delivery_retries_total

message_delivery_interruptions_total

message_reconciliations_total

messages_expired_total

messages_cancelled_total

message_cancellations_too_late_total

message_outbox_bytes_total

message_inbox_bytes_total

message_delivery_latency_ms

message_processing_latency_ms

message_end_to_end_latency_ms

messaging_recovery_runs_total

messaging_recovery_failures_total
```

---

# 103. Logging

Logs estructurados pueden incluir:

```text
timestamp

messageId

senderNodeId

receiverNodeId

messageType

payloadLength

payloadHash

deliveryRequirement

priority

outboxState

inboxState

attemptCount

processingAttemptCount

logicalLinkId

secureSessionId

encounterId

eventType

durationMs

errorCode
```

No deben incluir:

```text
Message Payload

Sensitive Application Metadata

Traffic Keys

Binding Secrets

Application Secrets
```

---

# 104. API pública inicial

```text
OfflineMessagingCore
────────────────────────────────────

createMessage(remoteNodeId, messageType, payloadSource, options)

enqueueMessage(messageId)

sendMessage(remoteNodeId, messageType, payloadSource, options)

cancelMessage(messageId, reason)

retryMessage(messageId)

getMessage(messageId)

getMessageStatus(messageId)

getPendingMessages(remoteNodeId)

getInboxMessages(filter)

getDeadLetters(filter)

registerHandler(messageType, handler)

unregisterHandler(messageType)

markProcessed(messageId, result)

onPeerAvailable(remoteNodeId, logicalLinkId)

onPeerUnavailable(remoteNodeId)

processControlFrame(logicalLinkId, frame)

processDataFrame(logicalLinkId, frameStream)

reconcileWithPeer(remoteNodeId, logicalLinkId)

recover()

runGarbageCollection()

subscribeEvents(listener)
```

---

# 105. MessagePayloadSource

```text
MessagePayloadSource
────────────────────────────────

getLength()

openReadStream()
```

---

# 106. MessageStoragePort

```text
MessageStoragePort
────────────────────────────────

reserveOutboxCapacity(messageId, bytes)

reserveInboxCapacity(messageId, bytes)

createTemporaryOutboxPayload(messageId)

createTemporaryInboxPayload(messageId)

writePayload(handle, byteStream)

flushPayload(handle)

commitOutboxPayload(messageId, handle, metadata)

commitInboxPayload(messageId, handle, metadata)

openOutboxPayload(messageId)

openInboxPayload(messageId)

deleteTemporaryPayload(handle)

deleteOutboxPayload(messageId)

deleteInboxPayload(messageId)

getStorageUsage()

reconcile()
```

---

# 107. MessagingRepository

```text
MessagingRepository
────────────────────────────────

saveOutboxMessage(message)

saveInboxMessage(message)

getOutboxMessage(messageId)

getInboxMessage(senderNodeId, messageId)

findPendingOutbox(remoteNodeId)

findNonTerminalOutbox()

findNonTerminalInbox()

findExpiredMessages(now)

saveDeliveryAttempt(attempt)

saveProcessingLease(lease)

getProcessingLease(messageId)

savePeerProgress(progress)

saveAckSequence(peerNodeId, sequence)

appendEvent(event)

transaction(block)
```

---

# 108. Message Handler Contract

```text
MessageHandler
────────────────────────────────

getSupportedMessageType()

handle(messageContext, payloadStream)

supportsIdempotency()

getProcessingPolicy()
```

---

# 109. Integración con SPEC-007

```text
MESSAGE

    │

    ├── Small Payload
    │       │
    │       ▼
    │   SPEC-008
    │
    └── Large Attachment
            │
            ▼
         ObjectId
            │
            ▼
         SPEC-007
```

---

# 110. Integración con SPEC-006

```text
OFFLINE MESSAGING CORE

        │

        ▼

LOGICAL LINK

        │

        ▼

HYBRID TRANSPORT ORCHESTRATOR

        │

        ├── BLE
        └── WIFI DIRECT
```

---

# 111. Testing Strategy

## Unit Tests

```text
MessageId Stability

Payload Hashing

Message Envelope Validation

Outbox State Machine

Inbox State Machine

Durable Inbox Commit

Message Deduplication

MessageId Conflict Detection

Received ACK Semantics

Processed ACK Semantics

ACK Sequence Handling

Processing Lease

Processing Retry

Dead Letter

Scheduling

Priority Ordering

Retry Backoff

Expiration

Cancellation

Quota Enforcement

Recovery

Garbage Collection
```

---

## Property-Based Tests

```text
same retry always preserves MessageId

transport switch never changes MessageId

new Secure Session never changes MessageId

new Encounter never changes MessageId

ACK(RECEIVED) never precedes durable inbox commit

duplicate MESSAGE_DATA never creates duplicate logical inbox message

duplicate ACK never advances sender state twice

same sender + same MessageId + different payloadHash always conflicts

different authenticated peer cannot claim ACK

different authenticated peer cannot resume delivery state

REMOTE_PROCESSED always implies REMOTE_RECEIVED

PROCESSED always implies RECEIVED

expired message is never newly scheduled

receiver window is never exceeded

process restart never loses durable inbox receipt

process restart never loses durable outbox message

stale processing lease eventually becomes retryable

terminal states never regress
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

Offline Messaging Core

Fake BLE

Fake Wi-Fi Direct

Fake Message Storage

Create Message

Outbox Commit

Peer Offline

Peer Appears

Offer

Message Data

Inbox Commit

Received ACK

Application Processing

Processed ACK

Wi-Fi Direct Loss

BLE Fallback

Process Death

Restart

Reencounter

New Secure Session

Reconciliation

Retry

Deduplication

Completion
```

---

## Fault Injection Tests

```text
message offer malformed

wrong sender NodeId

wrong receiver NodeId

payload too large

payload truncated

payload modified

wrong payloadHash

duplicate offer

duplicate data

same MessageId different payload

inbox commit failure

process death before inbox commit

process death after inbox commit before ACK

ACK lost

duplicate ACK

ACK replay

ACK from wrong peer

transport lost during MESSAGE_DATA

transport switch before ACK

sender process death IN_FLIGHT

receiver process death PROCESSING

processing lease expires

handler retryable failure

handler terminal failure

message expires while peer offline

message expires after RECEIVED before PROCESSING

cancel before send

cancel during delivery

cancel after RECEIVED

cancel after PROCESSED

inbox quota exceeded

outbox quota exceeded

reconciliation summary malformed

state query replay

garbage collection during active message
```

---

# 112. Escenarios obligatorios de aceptación

## Scenario A — Peer Online

```text
Create Message

Durable Outbox Commit

Peer Available

Message Delivered

Durable Inbox Commit

ACK(RECEIVED)

Application Processes

ACK(PROCESSED)
```

Resultado:

```text
Sender = REMOTE_PROCESSED

Receiver = PROCESSED
```

---

## Scenario B — Peer Offline

```text
Create Message

Peer Absent
```

Resultado:

```text
Message Remains Durable In Outbox
```

Horas después:

```text
Peer Appears

Message Delivered

Same MessageId
```

---

## Scenario C — Connection Lost After Inbox Commit

Resultado:

```text
Sender Retries

Receiver Deduplicates

No Duplicate Inbox Entry

ACK(RECEIVED) Resent
```

---

## Scenario D — Duplicate Message Data

Resultado:

```text
One Durable Inbox Message

No Duplicate Application Scheduling

ACK State Replayed Correctly
```

---

## Scenario E — MessageId Conflict

```text
Same Sender

Same MessageId

Different PayloadHash
```

Resultado:

```text
MESSAGE_ID_CONFLICT

Second Payload Rejected

Security Event Emitted
```

---

## Scenario F — Process Death Sender

Resultado:

```text
Outbox Recovered

IN_FLIGHT → RETRY_PENDING

Delivery Continues Later
```

---

## Scenario G — Process Death Receiver After Commit

Resultado:

```text
Inbox Receipt Preserved

Duplicate Delivery Deduplicated

ACK(RECEIVED) Resent
```

---

## Scenario H — Process Death During Application Processing

Resultado:

```text
Lease Expires

Message Retried

Application Uses MessageId For Idempotency
```

---

## Scenario I — Reencounter Hours Later

Resultado:

```text
New Encounter

New Secure Session

Same Authenticated NodeId

Pending Outbox Discovered

Message Delivered

Same MessageId
```

---

## Scenario J — Different Peer Attempts State Reconciliation

Resultado:

```text
Peer Mismatch

No Messaging State Disclosed

No Message Delivered
```

---

## Scenario K — Wi-Fi Direct Failure

Resultado:

```text
LogicalLink Falls Back To BLE

MessageId Preserved

Delivery Retried Or Continued

No Duplicate Logical Delivery
```

---

## Scenario L — Received Requirement

```text
deliveryRequirement = RECEIVED
```

Resultado:

```text
Sender Success At ACK(RECEIVED)

No Need To Wait For Application Processing
```

---

## Scenario M — Processed Requirement

```text
deliveryRequirement = PROCESSED
```

Resultado:

```text
ACK(RECEIVED) Is Intermediate

Sender Success Only At ACK(PROCESSED)
```

---

## Scenario N — Expired Offline Message

Resultado:

```text
Message EXPIRED

Never Delivered After Expiration

Terminal Event Emitted
```

---

## Scenario O — Cancel After Processed

Resultado:

```text
TOO_LATE

No Rollback Claimed
```

---

# 113. Requisitos funcionales

### FR-001

El sistema MUST persistir mensajes outbound antes de reportar enqueue exitoso.

### FR-002

MessageId MUST permanecer estable durante retries.

### FR-003

MessageId MUST permanecer estable durante transport switches.

### FR-004

MessageId MUST permanecer estable durante nuevas Secure Sessions.

### FR-005

MessageId MUST permanecer estable durante nuevos Encounters.

### FR-006

El sistema MUST soportar peer offline.

### FR-007

El sistema MUST entregar mensajes pendientes al reaparecer peer autenticado.

### FR-008

El sistema MUST validar senderNodeId contra peer autenticado.

### FR-009

El sistema MUST validar receiverNodeId.

### FR-010

El sistema MUST verificar payloadHash.

### FR-011

El sistema MUST persistir Inbox durablemente.

### FR-012

ACK(RECEIVED) MUST enviarse solo después de durable Inbox commit.

### FR-013

Inbox commit MUST ser idempotente.

### FR-014

El sistema MUST deduplicar por senderNodeId + MessageId.

### FR-015

MessageId conflict MUST rechazarse.

### FR-016

El sistema MUST distinguir RECEIVED de PROCESSED.

### FR-017

ACK(PROCESSED) MUST enviarse solo después de processing durable.

### FR-018

ACKs MUST ser idempotentes.

### FR-019

ACKs MUST validarse contra authenticated NodeId.

### FR-020

El sistema MUST soportar deliveryRequirement RECEIVED.

### FR-021

El sistema MUST soportar deliveryRequirement PROCESSED.

### FR-022

El sistema MUST soportar processing retries.

### FR-023

El sistema MUST soportar processing leases.

### FR-024

El sistema MUST soportar Dead Letter.

### FR-025

El sistema MUST soportar transport loss.

### FR-026

El sistema MUST soportar transport switching.

### FR-027

El sistema MUST soportar process death.

### FR-028

El sistema MUST recuperar Outbox no terminal.

### FR-029

El sistema MUST recuperar Inbox no terminal.

### FR-030

El sistema MUST reconciliar estado con peer autenticado.

### FR-031

Receiver durable Inbox state MUST ser autoritativo para receipt.

### FR-032

El sistema MUST aplicar backpressure.

### FR-033

El sender MUST respetar receiver window.

### FR-034

El sistema MUST aplicar cuotas.

### FR-035

El sistema MUST soportar expiración.

### FR-036

El sistema MUST soportar cancelación best-effort.

### FR-037

El sistema MUST soportar garbage collection.

### FR-038

Payloads grandes MUST referenciar SPEC-007.

### FR-039

El sistema MUST emitir eventos.

### FR-040

El sistema MUST producir métricas.

### FR-041

Messaging Core MUST ser independiente del transporte físico.

---

# 114. Requisitos no funcionales

### NFR-001 — Offline Sovereignty

Mensajería debe operar sin Internet ni servidor.

### NFR-002 — Durability

Outbox e Inbox deben sobrevivir process death.

### NFR-003 — Idempotency

Duplicados no deben producir múltiples commits lógicos.

### NFR-004 — Delivery Semantics

RECEIVED y PROCESSED deben ser estados explícitamente distintos.

### NFR-005 — No False Exactly-Once Claim

El protocolo no debe afirmar exactly-once para efectos externos.

### NFR-006 — Transport Independence

Messaging Core no debe depender de BLE ni Wi-Fi Direct.

### NFR-007 — Session Independence

Mensajes durables no deben depender de SecureSession específica.

### NFR-008 — Encounter Independence

Mensajes durables no deben depender de Encounter específico.

### NFR-009 — Crash Consistency

Process death en puntos críticos debe producir estado recuperable.

### NFR-010 — Resource Bounding

Mensajes, payloads, queues, retries y leases deben poseer límites.

### NFR-011 — Storage Safety

Metadata remota no debe controlar recursos locales sin validación.

### NFR-012 — Security

Peer authentication debe preceder entrega, ACK y reconciliación.

### NFR-013 — Observability

Cada transición crítica debe producir evidencia observable.

### NFR-014 — Cross-Implementation Compatibility

Wire encoding, hashes y state reconciliation deben poseer vectores interoperables.

### NFR-015 — Testability

Messaging Core debe probarse sin Android, radios ni conexiones físicas reales.

---

# 115. Criterios de éxito

La SPEC será `ACCEPTED` cuando:

1. mensaje se persista antes de enqueue success;

2. peer offline no cause pérdida del mensaje;

3. mismo MessageId sobreviva retries;

4. mismo MessageId sobreviva transport switches;

5. mismo MessageId sobreviva nuevas Secure Sessions;

6. mismo MessageId sobreviva nuevos Encounters;

7. payload modificado sea rechazado;

8. Inbox commit sea durable;

9. ACK(RECEIVED) ocurra solo después de durable commit;

10. duplicados no creen múltiples InboxMessages;

11. MessageId conflict sea detectado;

12. RECEIVED y PROCESSED sean estados distintos;

13. ACK(PROCESSED) ocurra solo después de procesamiento durable;

14. ACKs duplicados sean idempotentes;

15. ACK de peer incorrecto sea rechazado;

16. process death sender preserve Outbox;

17. process death receiver preserve Inbox;

18. process death durante processing sea recuperable;

19. processing lease evite ejecución concurrente;

20. retries de processing funcionen;

21. Dead Letter funcione;

22. reencuentro entregue mensajes pendientes;

23. reconciliación converja;

24. receiver window sea respetado;

25. cuotas eviten resource exhaustion;

26. expiración detenga entregas futuras;

27. cancelación no afirme rollback remoto;

28. mensajes grandes utilicen ObjectId;

29. Core sea independiente del transporte;

30. existan vectores interoperables y fault injection tests.

---

# 116. Criterios de rechazo

La SPEC debe rechazarse si:

* mensaje se considera enviado antes de Outbox commit;

* ACK(RECEIVED) se envía antes de Inbox commit;

* RECEIVED y PROCESSED son el mismo estado;

* MessageId cambia durante retry;

* MessageId cambia después de transport switch;

* MessageId cambia después de nueva Secure Session;

* MessageId cambia después de reencuentro;

* duplicate MESSAGE_DATA crea duplicate InboxMessage;

* same sender + MessageId + different payload no genera conflicto;

* ACK de otro NodeId modifica Outbox state;

* process death pierde mensajes durables;

* processing concurrente ocurre sin lease o mecanismo equivalente;

* se afirma exactly-once para efectos externos;

* peer offline produce FAILED inmediatamente;

* cancelación afirma deshacer efectos ya procesados;

* payload grande se carga completamente en memoria obligatoriamente;

* Core depende directamente de BLE o Wi-Fi Direct.

---

# 117. Entregables

```text
/specs/008-offline-messaging/spec.md

/core/offline-messaging-domain/

/core/offline-messaging-application/

/core/offline-messaging-contracts/

/core/offline-messaging-testing/

/storage/message-storage-contracts/

/storage/fake-message-storage/

/docs/architecture/DURABLE-OFFLINE-MESSAGING.md

/docs/architecture/OUTBOX-INBOX-MODEL.md

/docs/protocol/MESSAGE-DELIVERY-PROTOCOL.md

/docs/protocol/MESSAGE-ACK-SEMANTICS.md

/docs/protocol/MESSAGE-STATE-RECONCILIATION.md

/docs/storage/MESSAGING-CRASH-CONSISTENCY.md

/docs/security/OFFLINE-MESSAGING-THREAT-MODEL.md

/docs/testing/OFFLINE-MESSAGING-TEST-VECTORS.md

/docs/adr/ADR-025-DURABLE-OUTBOX-BEFORE-SEND.md

/docs/adr/ADR-026-DURABLE-INBOX-BEFORE-RECEIVED-ACK.md

/docs/adr/ADR-027-RECEIVED-VS-PROCESSED-SEMANTICS.md

/docs/adr/ADR-028-MESSAGE-DEDUPLICATION-KEY.md

/docs/adr/ADR-029-PROCESSING-LEASES.md

/docs/adr/ADR-030-MESSAGE-EXPIRATION-POLICY.md
```

---

# 118. Decisiones abiertas

La implementación debe producir evidencia para decidir:

1. límite óptimo de payload;

2. OFFER + DATA fast path;

3. batch delivery;

4. batch ACK;

5. bitmap de receipts;

6. reconciliation compression;

7. ACK ranges;

8. per-peer ACK sequence frente a per-stream sequence;

9. senderSequence obligatorio u opcional;

10. strict per-peer ordering;

11. logical streams;

12. causal ordering;

13. vector clocks;

14. Lamport clocks;

15. message dependencies;

16. transactional inbox + application state;

17. SDK idempotency helpers;

18. deduplication retention óptima;

19. receipt retention óptima;

20. payload compression;

21. priority starvation prevention;

22. adaptive receiver window;

23. power-aware scheduling;

24. background delivery Android;

25. notification integration;

26. end-to-end user identity;

27. group messaging;

28. multi-device identity;

29. store-carry-forward;

30. relay authorization;

31. message forwarding;

32. opportunistic routing.

---

# 119. Relación con SPEC anteriores

```text
SPEC-001
HYBRID OFFLINE LINK SPIKE

        │

        ▼

Physical Feasibility


SPEC-002
TRANSPORT-INDEPENDENT TRANSFER CORE

        │

        ▼

Durable Logical Operations


SPEC-003
DECENTRALIZED NODE IDENTITY

        │

        ▼

Persistent Node Identity


SPEC-004
OFFLINE PEER DISCOVERY & ENCOUNTER

        │

        ▼

Authenticated Nearby Peers


SPEC-005
SECURE SESSION PROTOCOL

        │

        ▼

Encrypted Authenticated Sessions


SPEC-006
HYBRID TRANSPORT ORCHESTRATION

        │

        ▼

Stable LogicalLink


SPEC-007
DURABLE OBJECT TRANSFER & RESUMPTION

        │

        ▼

Large Durable Objects


SPEC-008
OFFLINE MESSAGING & DURABLE DELIVERY

        │

        ├── durable Outbox
        ├── durable Inbox
        ├── offline queueing
        ├── MessageId stability
        ├── deduplication
        ├── durable receipt ACK
        ├── application processed ACK
        ├── retries
        ├── crash recovery
        ├── reencounter delivery
        ├── reconciliation
        ├── expiration
        └── best-effort cancellation
```

---

# 120. Resultado final

```text
                        APPLICATION A

                              │

                              ▼

                         CREATE MESSAGE

                              │

                              ▼

                      DURABLE OUTBOX COMMIT

                              │

                    ┌─────────┴─────────┐
                    │                   │
                    ▼                   ▼

               PEER OFFLINE        PEER AVAILABLE

                    │                   │
                    │                   ▼
                    │
                    │              LOGICAL LINK
                    │
                    │                   │
                    │          ┌────────┴────────┐
                    │          │                 │
                    │          ▼                 ▼
                    │
                    │        BLE           WIFI DIRECT
                    │
                    │          │                 │
                    │          └────────┬────────┘
                    │                   │
                    │                   ▼
                    │
                    │              MESSAGE DATA
                    │                   │
                    │                   ▼
                    │
                    │          DURABLE INBOX COMMIT
                    │                   │
                    │                   ▼
                    │
                    │            ACK(RECEIVED)
                    │                   │
                    │                   ▼
                    │
                    │          APPLICATION PROCESSING
                    │                   │
                    │                   ▼
                    │
                    │            DURABLE PROCESSED
                    │                   │
                    │                   ▼
                    │
                    │            ACK(PROCESSED)
                    │                   │
                    └───────────────────┘

                              │

                              ▼

                         TERMINAL STATE
```

**Un mensaje puede crearse sin conexión.**

**Puede permanecer horas o días en Outbox.**

**Puede atravesar distintos transportes.**

**Puede perder la conexión después de ser recibido.**

**Puede sobrevivir a process death.**

**Puede reenviarse sin crear duplicados lógicos.**

**Puede distinguir entre haber llegado al dispositivo y haber sido realmente procesado por la aplicación.**

**Cuando el mismo NodeId autenticado reaparece, el protocolo puede reconciliar el estado y continuar la entrega.**
