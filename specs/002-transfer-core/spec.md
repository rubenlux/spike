# SPEC-002 - Transport-Independent Transfer Core

**Estado**: Draft  
**Tipo**: Core Architecture Specification  
**Prioridad**: P0 - Fundacional  
**Dependencia**: SPEC-001 - Spike-001: Hybrid Offline Link  
**Plataforma inicial**: Android  
**Alcance**: Core multiplataforma por diseno, validacion inicial en Android  
**Conectividad requerida**: Ninguna  
**Dependencia de BLE**: No  
**Dependencia de Wi-Fi Direct**: No  
**Dependencia de Internet**: No  
**Servidor requerido**: No  
**Origen**: Solicitud adjunta del usuario, normalizada como especificacion de repositorio.

## 1. Proposito

Definir e implementar el nucleo de transferencia logica independiente de transporte que servira como primera pieza estable del futuro protocolo offline.

El sistema debe permitir representar, persistir, verificar, interrumpir, recuperar y completar transferencias de objetos sin conocer ni depender de BLE, Wi-Fi Direct, sockets, conexiones IP, APIs Android, Internet, servidores o infraestructura externa.

La SPEC-001 valida experimentalmente que BLE y Wi-Fi Direct pueden utilizarse conjuntamente para establecer un enlace hibrido offline. La SPEC-002 extrae de ese experimento la abstraccion estable:

```text
Una transferencia es una entidad logica durable cuya existencia, progreso,
integridad y recuperacion son independientes de cualquier enlace fisico usado
para transportar bytes.
```

## 2. Hipotesis Arquitectonica

Es posible construir un `Transfer Core` determinista, durable y transport-independent que:

1. represente objetos transferibles con identificadores estables;
2. divida objetos en unidades verificables;
3. mantenga manifests versionados;
4. cree transferencias logicas persistentes;
5. determine unidades disponibles y faltantes;
6. acepte datos desde cualquier transporte;
7. verifique integridad antes de aceptar datos;
8. persista progreso confirmado;
9. sobreviva interrupciones, process death y reinicio de dispositivo;
10. continue desde progreso confirmado;
11. reconstruya y verifique el objeto final;
12. produzca eventos observables;
13. funcione sin conocer el transporte que entrega o solicita bytes.

## 3. Resultado Esperado

Debe existir un modulo reutilizable:

```text
TRANSFER CORE
  Object Model
  Manifest Model
  Chunk Model
  Transfer Lifecycle
  Chunking Engine
  Verification Engine
  Progress Tracker
  Missing Chunk Calculator
  Durable Transfer Store
  Object Reconstruction
  Final Integrity Verification
  Transfer Events
  Transfer Recovery
  Transport Contracts
```

El modulo debe poder probarse con `InMemoryTransportAdapter`, `FileTransportAdapter` y `LoopbackTransportAdapter`, sin BLE ni Wi-Fi Direct.

## 4. Principio Fundamental

```text
TRANSFER != CONNECTION
```

Una conexion es temporal. Una transferencia es durable. La destruccion de una conexion fisica no debe destruir objeto, manifest, transferencia, progreso, chunks verificados ni estado de recuperacion.

## 5. Principios No Negociables

- **Independencia de transporte**: el Core no importa ni conoce Android Bluetooth APIs, Android Wi-Fi P2P APIs, BLE, Wi-Fi Direct, TCP, UDP, QUIC, HTTP, WebSocket, sockets o interfaces de red.
- **Persistencia antes de confirmacion**: un chunk es ACK-eligible solo despues de validate, write, flush/commit, verify y register verified.
- **Integridad por unidad**: cada chunk se verifica independientemente.
- **Integridad final**: todos los chunks validos no implican objeto valido hasta verificar hash final.
- **Idempotencia**: procesar el mismo chunk valido dos veces produce el mismo estado logico.
- **Determinismo**: `MissingChunks(Manifest, VerifiedSet)` produce siempre el mismo resultado.
- **Recursos acotados**: no cargar objetos completos en memoria.
- **Recuperabilidad**: el estado durable se reconstruye despues de terminacion abrupta.
- **Estado explicito**: no inferir estado critico solo desde archivos.
- **Errores tipados**: errores de dominio clasificados; no usar errores genericos como unica estrategia.

## 6. Fuera de Alcance

BLE Discovery, GATT, BLE Advertising, Wi-Fi Direct, sockets reales, peer discovery, identidad criptografica, autenticacion, E2EE, routing, multi-hop, store-carry-forward, gossip, replication policies, adaptive transport selection, grupos, mensajeria, UI de produccion, servidores, cloud e Internet fallback.

## 7. Arquitectura

```text
APPLICATION / SDK
  -> TRANSFER SERVICE
       createTransfer()
       prepareObject()
       acceptManifest()
       acceptChunk()
       getMissingChunks()
       interruptTransfer()
       resumeTransfer()
       verifyObject()
       completeTransfer()
  -> DOMAIN SERVICES
       Object Engine
       Transfer Lifecycle
       Verification Engine
  -> DURABLE TRANSFER STORE
       Objects, Manifests, Transfers, Verified Chunks, Events, Recovery Metadata
  -> STORAGE ADAPTER
       MetadataStore, ChunkStore, ObjectStore, TransactionBoundary

EXTERNAL TRANSPORTS
  BLE | Wi-Fi Direct | Loopback | Future Transport
  -> TRANSFER CORE CONTRACTS
```

## 8. Bounded Context

Bounded context: `Transfer`.

Entities:

- `TransferObject`
- `ObjectManifest`
- `ChunkDescriptor`
- `Transfer`
- `VerifiedChunk`
- `TransferEvent`

Value Objects:

- `ObjectId`
- `TransferId`
- `ChunkIndex`
- `ChunkHash`
- `ObjectHash`
- `ByteOffset`
- `ByteLength`
- `ProtocolVersion`
- `ManifestVersion`

Services:

- `ObjectPreparationService`
- `ChunkVerificationService`
- `TransferLifecycleService`
- `MissingChunkCalculator`
- `ObjectReconstructionService`
- `TransferRecoveryService`

Repositories:

- `ObjectRepository`
- `ManifestRepository`
- `TransferRepository`
- `VerifiedChunkRepository`
- `TransferEventRepository`

## 9. ObjectId

`ObjectId` is immutable, validated at construction, fixed-length, canonical, value-equal and deterministic when serialized.

Initial format:

```text
sha256:<64 lowercase hex characters>
```

Bare SHA-256 values without an algorithm prefix are forbidden in persistent interfaces.

## 10. Manifest

`ObjectManifest` includes `manifestVersion`, `objectId`, `totalSize`, `chunkSize`, `chunkCount`, `objectHash`, `chunks[]` and `createdAt`.

Invariants:

- `chunkCount > 0`
- `chunkSize > 0`
- `totalSize > 0`
- `chunks.length == chunkCount`
- chunk indexes are unique and contiguous from `0`
- offsets are valid
- no overlaps and no gaps
- sum of chunk lengths equals total size
- final chunk length is less than or equal to chunk size
- object ID is consistent with object hash

Unknown manifest versions are rejected with `UnsupportedManifestVersion`.

## 11. Chunk Verification Pipeline

```text
ChunkInput
  -> Validate Transfer
  -> Validate Manifest
  -> Validate Chunk Index
  -> Validate Declared Length
  -> Open Temporary Write
  -> Stream Bytes + Incremental SHA-256
  -> Validate Actual Length
  -> Compare Hash
  -> Durable Commit
  -> Register VerifiedChunk
  -> Emit CHUNK_VERIFIED
```

If any phase fails, the chunk is not verified, durable state remains consistent, a typed error is emitted and temporary data is deleted or recovered later.

## 12. Transfer

Fields:

- `transferId`
- `objectId`
- `direction`
- `localNodeId`
- `remoteNodeId`
- `state`
- `totalBytes`
- `verifiedBytes`
- `totalChunks`
- `verifiedChunks`
- `createdAt`
- `updatedAt`
- `interruptedAt`
- `completedAt`
- `failureReason`

`TransferId` must not derive from BLE address, Wi-Fi MAC, IP, socket or connection ID. Initial recommendation: UUIDv7 or equivalent opaque canonical ID.

Directions: `OUTBOUND`, `INBOUND`.

## 13. State Machine

States:

```text
CREATED
PREPARING
READY
ACTIVE
INTERRUPTED
WAITING
RESUMING
VERIFYING
COMPLETED
FAILED
CANCELLED
```

Undeclared transitions are rejected with `InvalidTransferStateTransition`. Each transition is executed as a durable transaction:

```text
BEGIN TRANSACTION
load transfer
validate current state
validate transition
apply mutation
persist transfer
append event
COMMIT
publish observable event
```

No persisted state may exist without its durable event.

## 14. Transfer Events

Minimum events:

```text
TRANSFER_CREATED
OBJECT_PREPARATION_STARTED
OBJECT_PREPARED
MANIFEST_CREATED
MANIFEST_ACCEPTED
TRANSFER_READY
TRANSFER_ACTIVATED
CHUNK_RECEIVE_STARTED
CHUNK_RECEIVED
CHUNK_VERIFIED
CHUNK_REJECTED
TRANSFER_INTERRUPTED
TRANSFER_WAITING
TRANSFER_RESUME_STARTED
TRANSFER_RESUMED
MISSING_CHUNKS_CALCULATED
OBJECT_RECONSTRUCTION_STARTED
OBJECT_RECONSTRUCTED
OBJECT_VERIFICATION_STARTED
OBJECT_VERIFIED
TRANSFER_COMPLETED
TRANSFER_FAILED
TRANSFER_CANCELLED
TRANSFER_RECOVERED
```

`sequenceNumber` is monotonic inside a transfer. No duplicates, gaps from successful commits or persistent reordering are allowed.

## 15. Missing Chunk Set

```text
MissingChunks = ManifestChunkIndexes - VerifiedChunkIndexes
```

The public abstraction must not be coupled to a list of integers. Initial implementation may use BitSet/bitmap internally.

## 16. Object Preparation and Reconstruction

Object preparation streams the source, calculates object SHA-256, calculates chunk descriptors/hashes, creates and validates manifest, persists object metadata and persists manifest.

Object reconstruction validates completeness, writes a temporary object sequentially from verified chunks, calculates SHA-256 incrementally, flushes, compares object hash and atomically finalizes only if valid.

Pattern:

```text
object.partial -> write complete -> durable flush where supported -> verify SHA-256 -> atomic rename -> object.complete
```

## 17. Durable Transfer Store

Must persist:

- Objects
- Manifests
- Transfers
- VerifiedChunks
- TransferEvents
- RecoveryMetadata

Minimum operations: `createTransfer`, `getTransfer`, `updateTransfer`, `saveManifest`, `getManifest`, `registerVerifiedChunk`, `hasVerifiedChunk`, `listVerifiedChunks`, `appendEvent`, `listEvents`, `findRecoverableTransfers`, `markCompleted`, `markFailed`.

## 18. Storage Contracts

Domain must not know SQLite, Room, PostgreSQL, filesystem API, Android Context or path implementation.

Contracts:

- `MetadataStore`
- `ChunkStore`
- `ObjectStore`
- `TransactionManager`

## 19. Recovery

`TransferRecoveryService.recover()` must locate non-terminal transfers, locate temporary files, compare metadata with physical storage, delete safe orphan temporaries, detect missing chunk files, detect unregistered chunk files, apply recovery policy, recalculate progress, correct recoverable states, mark unrecoverable inconsistencies and emit recovery events.

Initial rules:

- temp chunk exists without verified record -> delete temp chunk.
- verified record exists and chunk file missing -> remove verified state, mark missing, emit storage inconsistency.
- chunk file exists without verified record and hash valid -> do not auto-trust; delete or quarantine.
- active transfer after process restart -> interrupted then waiting.
- all chunks verified and not completed -> verify object completeness and continue reconstruction.

## 20. Transport Boundary

Input:

- `acceptManifest(remoteNodeId, manifestStream)`
- `acceptChunk(transferId, chunkIndex, contentStream)`
- `markTransportAvailable(transferId, transportSessionRef)`
- `markTransportUnavailable(transferId, reason)`

Output:

- `TransferSnapshot`
- `MissingChunkSet`
- `ChunkReadHandle`
- `TransferEventStream`
- `TransferCommandResult`

`transportSessionRef` is opaque and must never be inspected by Core.

## 21. Concurrency and Idempotency

Mutations for the same `TransferId` MUST be serialized. Different transfers may process concurrently.

Idempotent operations:

- `acceptManifest(same valid manifest)`
- `acceptChunk(same valid chunk)`
- `interruptTransfer(already interrupted)`
- `markWaiting(already waiting)`
- `resumeTransfer(already active)`
- `completeTransfer(already completed)`
- `recover(repeated execution)`

Responses: `APPLIED`, `ALREADY_APPLIED`, `REJECTED`.

## 22. Error Taxonomy

Minimum hierarchy:

```text
TransferCoreError
  ValidationError
    InvalidObjectId
    InvalidTransferId
    InvalidManifest
    UnsupportedManifestVersion
    InvalidChunkIndex
    InvalidChunkLength
  IntegrityError
    ChunkHashMismatch
    ObjectHashMismatch
    StoredChunkCorruption
  StateError
    TransferNotFound
    InvalidTransferStateTransition
    TransferAlreadyTerminal
    TransferDirectionMismatch
  StorageError
    InsufficientStorage
    ChunkWriteFailure
    MetadataCommitFailure
    ObjectFinalizationFailure
    StorageInconsistency
  ConcurrencyError
    ConcurrentMutationRejected
    OptimisticLockConflict
  RecoveryError
    RecoveryInvariantViolation
    UnrecoverableTransferState
```

## 23. Observability

Minimum metrics:

- `transfers_created_total`
- `transfers_completed_total`
- `transfers_failed_total`
- `transfers_recovered_total`
- `chunks_received_total`
- `chunks_verified_total`
- `chunks_rejected_total`
- `duplicate_chunks_total`
- `bytes_received_total`
- `bytes_verified_total`
- `object_verification_failures_total`
- `storage_inconsistencies_total`
- `recovery_duration_ms`
- `object_preparation_duration_ms`
- `object_reconstruction_duration_ms`

Structured logs must include timestamp, event type, transfer ID, object ID, chunk index, state before/after, operation, duration and error code where applicable. Logs must not include file content.

## 24. Public API

Initial API:

```text
prepareOutboundObject(source)
createOutboundTransfer(objectId, remoteNodeId)
acceptInboundManifest(remoteNodeId, manifest)
activateTransfer(transferId)
acceptChunk(transferId, chunkIndex, stream)
getTransfer(transferId)
getTransferSnapshot(transferId)
getMissingChunks(transferId)
openChunk(objectId, chunkIndex)
interruptTransfer(transferId, reason)
markWaiting(transferId)
resumeTransfer(transferId)
verifyAndComplete(transferId)
cancelTransfer(transferId)
recover()
subscribeEvents(listener)
```

## 25. Security and Limits

Required: incremental SHA-256, safe hash comparison, configurable maximums, strict manifest validation, integer overflow protection, invalid offset protection, abusive chunk count protection, excessive manifest protection, controlled storage, no remote names/paths, safe temporary cleanup.

Configurable limits:

- `MAX_OBJECT_SIZE`
- `MAX_CHUNK_SIZE`
- `MIN_CHUNK_SIZE`
- `MAX_CHUNK_COUNT`
- `MAX_MANIFEST_SIZE`
- `MAX_ACTIVE_TRANSFERS`
- `MAX_CONCURRENT_CHUNK_WRITES`
- `MAX_EVENT_METADATA_SIZE`
- `MAX_RECOVERY_DURATION`

## 26. Testing Strategy

Unit tests: value objects, manifest validation, state machine, missing chunk calculation, hash verification, error taxonomy and idempotency.

Property tests: random object sizes, chunk sizes, missing sets, interruption points, duplicates and out-of-order sequences.

Integration tests: filesystem storage, metadata persistence, transaction boundaries, crash recovery, reconstruction and large files.

Fault injection: write failures, commit failures, process termination, corrupted chunk, missing chunk file, duplicate events, concurrent mutations and disk full.

Mandatory scenarios:

- A: transfer without network via loopback, complete.
- B: interrupt at 35%, destroy adapter, recover, resume missing chunks only.
- C: duplicate every chunk, complete once with duplicate metric.
- D: out-of-order chunks, complete.
- E: corrupt chunk rejected, correct chunk accepted later.
- F: process death restores progress and enters waiting.
- G: final hash mismatch forbids completed.

## 27. Functional Requirements

- **FR-001**: Core MUST prepare objects by streaming.
- **FR-002**: Core MUST produce deterministic manifests.
- **FR-003**: Core MUST validate manifests before persistence.
- **FR-004**: Core MUST create transport-independent transfers.
- **FR-005**: Core MUST accept out-of-order chunks.
- **FR-006**: Core MUST verify every chunk.
- **FR-007**: Core MUST persist valid chunks.
- **FR-008**: Core MUST reject corrupt chunks.
- **FR-009**: Core MUST process duplicates idempotently.
- **FR-010**: Core MUST calculate missing chunks.
- **FR-011**: Core MUST persist progress.
- **FR-012**: Core MUST survive process death.
- **FR-013**: Core MUST execute recovery.
- **FR-014**: Core MUST reconstruct objects by streaming.
- **FR-015**: Core MUST verify final hash.
- **FR-016**: Core MUST forbid `COMPLETED` if final verification fails.
- **FR-017**: Core MUST emit durable events.
- **FR-018**: Core MUST produce metrics.
- **FR-019**: Core MUST serialize mutations for one transfer.
- **FR-020**: Core MUST operate without real transports.

## 28. Non-Functional Requirements

- **NFR-001 - Transport Independence**: zero networking dependencies in Core.
- **NFR-002 - Bounded Memory**: memory must not grow with object size.
- **NFR-003 - Crash Consistency**: abrupt restart must not create false progress.
- **NFR-004 - Determinism**: manifest validation, missing chunks and state transitions are deterministic.
- **NFR-005 - Idempotency**: recoverable duplicate commands do not corrupt state.
- **NFR-006 - Observability**: every state transition emits a durable event.
- **NFR-007 - Testability**: full Core testable without Android hardware.
- **NFR-008 - Evolvability**: persistent IDs, manifests and contracts are versioned.

## 29. Acceptance and Rejection Criteria

Accepted when the Core has no Android or transport dependencies, prepares large objects by streaming, validates manifests, persists transfers, accepts out-of-order chunks, detects corruption, handles duplicates idempotently, calculates missing chunks, survives process death, recovers pending transfers, reconstructs objects by streaming, verifies final SHA-256, forbids invalid completion, emits ordered durable events, passes mandatory scenarios and works with a test adapter only.

Rejected if Core knows BLE or Wi-Fi Direct, `TransferId` depends on connection data, transport loss deletes transfer state, chunks are verified before durable commit, interruption retransmits the full object, objects are loaded fully in memory, process death loses progress, critical state is inferred only from filesystem, integrity errors permit `COMPLETED`, tests require smartphones, or Core couples to Android.

## 30. Entregables

```text
/specs/002-transfer-core/spec.md
/core/transfer-domain/
/core/transfer-application/
/core/transfer-contracts/
/core/transfer-testing/
/storage/transfer-storage-contracts/
/docs/architecture/TRANSFER-CORE.md
/docs/testing/TRANSFER-CORE-FAULT-MODEL.md
/docs/adr/ADR-002-TRANSFER-INDEPENDENCE.md
/testing/adapters/InMemoryTransferAdapter
/testing/adapters/LoopbackTransferAdapter
/testing/adapters/FileTransferAdapter
```

## 31. Relacion con SPEC-001

```text
SPEC-001 validates physical feasibility:
  BLE + Wi-Fi Direct experimental link

SPEC-002 extracts stable architecture:
  durable logical transfer independent from BLE, Wi-Fi Direct, Android,
  connections and hardware.
```

Final principle:

```text
The connection can die.
The process can die.
The device can reboot.
The logical transfer remains recoverable.
```
