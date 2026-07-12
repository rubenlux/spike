# SPEC-004 - Offline Peer Discovery & Encounter Protocol

**Estado**: Draft  
**Tipo**: Core Protocol Specification  
**Prioridad**: P0 - Fundacional  
**Dependencias**: SPEC-001, SPEC-002, SPEC-003  
**Plataforma inicial**: Android  
**Alcance del Core**: Multiplataforma por diseno  
**Transporte inicial de descubrimiento**: Bluetooth Low Energy (BLE)  
**Conectividad requerida**: Ninguna  
**Internet requerido**: No  
**Servidor requerido**: No  
**Cuenta de usuario requerida**: No  
**Origen**: Solicitud adjunta del usuario, normalizada como especificacion de repositorio.

## 1. Proposito

Definir e implementar el protocolo offline mediante el cual nodos fisicamente proximos pueden anunciar presencia del protocolo, descubrir peers compatibles, evitar publicar directamente `NodeId`, usar identificadores efimeros, establecer canal inicial de control, intercambiar documentos publicos de identidad, ejecutar Proof of Possession mutuo, vincular presencia fisica efimera con identidad criptografica valida, crear un `Encounter`, detectar perdida de contacto, soportar rediscovery y producir eventos/metricas sin Internet ni infraestructura externa.

Transicion definida:

```text
UNKNOWN RADIO PRESENCE
  -> COMPATIBLE PROTOCOL PRESENCE
  -> CONTROL CHANNEL
  -> CRYPTOGRAPHIC IDENTITY VALIDATION
  -> PROOF OF POSSESSION
  -> AUTHENTICATED PEER
  -> ENCOUNTER
```

Esta SPEC no implementa secure session encryption, Wi-Fi Direct upgrade, transferencias, routing, store-carry-forward ni multi-hop.

## 2. Problema Fundamental

Anunciar `NodeId`, public key o device name en BLE advertisements permitiria tracking persistente, correlacion, observacion pasiva, fingerprinting y exposicion innecesaria de identidad.

Por tanto, discovery debe detectar nodos compatibles sin exponer directamente la identidad criptografica estable.

Principio:

```text
DISCOVERY != IDENTIFICATION != AUTHENTICATION != ENCOUNTER
```

## 3. Hipotesis Arquitectonica

Es posible construir discovery offline donde:

1. nodos anuncian presencia por BLE;
2. advertisement contiene informacion minima;
3. `NodeId` estable no se anuncia;
4. cada nodo usa `EphemeralDiscoveryId` rotativo;
5. peers compatibles se detectan por `ProtocolServiceId`;
6. discovery crea `DiscoveryCandidate`, no identidad autenticada;
7. canal BLE control intercambia identity documents;
8. identity documents se validan mediante Identity Core;
9. peers ejecutan Proof of Possession mutuo;
10. solo despues se crea `AuthenticatedPeer`;
11. presencia autenticada crea `Encounter`;
12. perdida de enlace fisico no borra historial;
13. rediscovery crea nuevo encounter con mismo nodo;
14. Discovery Core permanece separado de BLE mediante contratos.

## 4. Resultado Esperado

```text
DISCOVERY & ENCOUNTER CORE
  Ephemeral Discovery Identity
  Discovery Advertisement Model
  Advertisement Validation
  Discovery Candidate Model
  Candidate Lifecycle
  Control Channel Contracts
  Identity Exchange
  Mutual Proof of Possession
  Peer Authentication
  Encounter Model
  Encounter Lifecycle
  Contact Loss Detection
  Rediscovery Handling
  Discovery Events
  Encounter Events
  Discovery Metrics
  Discovery Transport Contracts
```

Implementacion inicial: BLE discovery adapter y BLE control channel adapter.

## 5. Principio Fundamental

```text
DISCOVERED != IDENTIFIED != AUTHENTICATED != TRUSTED
```

- **DISCOVERED**: existe una presencia fisica compatible.
- **IDENTIFIED**: se recibio un `IdentityDocument` valido.
- **AUTHENTICATED**: peer demostro posesion de la private key.
- **TRUSTED**: politica externa decidio confiar.

Esta SPEC llega solo hasta `AUTHENTICATED`.

## 6. Principios No Negociables

- Discovery y Encounter funcionan sin Internet, servidor, cloud, DNS, backend o registro central.
- `NodeId` estable no aparece en BLE advertisements.
- Public key completa no aparece en advertisements.
- Advertisements usan `EphemeralDiscoveryId` efimero y rotativo.
- Discovery nunca implica autenticacion.
- Ambos peers deben demostrar posesion de sus claves.
- Identity validity no implica trust.
- Discovery Core no importa Android BLE APIs.
- Candidate y Encounter tienen state machines explicitas.
- Parsing, identidad, proof, version o estado invalidos fallan cerrado.

## 7. Fuera de Alcance

Wi-Fi Direct, transferencias de chunks, Transfer Core orchestration, secure session encryption, session key agreement, Double Ratchet, mensajeria, routing, multi-hop, store-carry-forward, gossip, reputacion, web of trust, autorizacion, grupos, inventory sync, Internet fallback, servers, background operation definitivo, Sybil resistance completa, unlinkability criptografica perfecta y anonimato frente a adversarios globales.

## 8. Arquitectura

```text
APPLICATION / SDK
  -> DISCOVERY SERVICE
       startAdvertising()
       startScanning()
       authenticateCandidate()
       getActiveCandidates()
       getActiveEncounters()
  -> DOMAIN SERVICES
       Ephemeral ID Engine
       Candidate Lifecycle
       Peer Authentication
       Encounter Lifecycle
  -> DISCOVERY EVENT STORE
       Candidates, Encounters, Auth Results, Events
  -> DISCOVERY TRANSPORT CONTRACTS
       Advertiser, Scanner, ControlChannel, ProximityObservation
  -> BLE ADAPTER - ANDROID
```

## 9. Bounded Context

Contexts: `Discovery` and `Encounter`.

Entities:

- `DiscoveryCandidate`
- `AuthenticatedPeer`
- `Encounter`
- `DiscoveryEvent`
- `EncounterEvent`

Value objects:

- `EphemeralDiscoveryId`
- `ProtocolServiceId`
- `DiscoveryVersion`
- `AdvertisementNonce`
- `CapabilityBitmap`
- `CandidateId`
- `EncounterId`
- `ObservationId`
- `SignalStrength`
- `DiscoveryTimestamp`

Services:

- `EphemeralIdService`
- `AdvertisementService`
- `CandidateLifecycleService`
- `PeerAuthenticationService`
- `EncounterLifecycleService`
- `RediscoveryService`

## 10. Advertisement v1

`ProtocolServiceId` identifies the protocol, not a node. It is public, non-personal and used for filtering.

`EphemeralDiscoveryId` properties:

- short-lived;
- rotating;
- random-looking;
- not equal to `NodeId`, `KeyId` or public key hash;
- not stable across long periods.

Initial derivation:

```text
rotationSecret = CSPRNG-generated local secret
epoch = floor(monotonicTime / ROTATION_INTERVAL)
ephemeralInput = DOMAIN_TAG || epoch || localRotationNonce
EphemeralDiscoveryId = Truncate(HMAC-SHA-256(rotationSecret, ephemeralInput))
```

Initial recommendation: 128 bits, 15-minute rotation interval, configurable and to be measured.

Advertisement model:

```text
DiscoveryAdvertisementV1
  discoveryVersion
  ephemeralDiscoveryId
  advertisementNonce
  capabilityBitmap
  flags
```

Forbidden in advertisement:

- `NodeId`
- `KeyId`
- public key
- identity document
- device/user name
- file/object/transfer IDs

Capabilities must be minimal, e.g. BLE control, identity v1, mutual PoP and encounter v1 support.

## 11. Advertisement Validation

Pipeline:

```text
RAW ADVERTISEMENT
  -> MAX SIZE CHECK
  -> SERVICE ID FILTER
  -> STRICT PARSE
  -> VERSION CHECK
  -> FIELD VALIDATION
  -> CAPABILITY COMPATIBILITY
  -> CREATE / UPDATE DISCOVERY CANDIDATE
```

Invalid advertisement must not create a valid candidate.

## 12. DiscoveryCandidate

```text
DiscoveryCandidate
  candidateId
  ephemeralDiscoveryId
  discoveryVersion
  capabilities
  firstSeenAt
  lastSeenAt
  observationCount
  latestSignalStrength
  state
  transportReference
```

`transportReference` is opaque. Core must not interpret MAC addresses or BLE handles.

Candidate states:

```text
DETECTED
COMPATIBLE
CONNECTING
CONTROL_CONNECTED
IDENTIFYING
IDENTIFIED
AUTHENTICATING
AUTHENTICATED
REJECTED
EXPIRED
FAILED
```

Multiple observations of the same `EphemeralDiscoveryId` inside a valid window update one candidate when safe. Same ephemeral ID forever must not be assumed to mean same `NodeId`.

## 13. Control Channel

After compatible candidate detection, a BLE control channel may be established through a transport contract. Discovery Core does not know GATT.

Minimum frames:

```text
DISCOVERY_HELLO
DISCOVERY_HELLO_ACK
IDENTITY_DOCUMENT
IDENTITY_DOCUMENT_ACK
AUTH_CHALLENGE
AUTH_PROOF
AUTH_RESULT
ENCOUNTER_READY
ERROR
CLOSE
```

Frame envelope:

```text
protocolVersion
frameType
sessionNonce
sequenceNumber
payloadLength
payload
```

Requirements: explicit framing, max limits, strict parser, monotonic sequence per channel, unpredictable `sessionNonce`, unknown critical frame rejection.

`DISCOVERY_HELLO` includes discovery version, supported identity/proof versions, capabilities and session nonce. It must not include `NodeId` before identity exchange.

## 14. Negotiation and Authentication

Peers negotiate discovery protocol version, identity version and proof version. No intersection means rejection.

Identity exchange:

```text
A -> IDENTITY_DOCUMENT_A -> B
A <- IDENTITY_DOCUMENT_B <- B
```

Each node validates size, strict parse, version, algorithms, `KeyId`, `NodeId`, binding and self-signature through Identity Core. Only then candidate may become `IDENTIFIED`.

Mutual Proof of Possession:

```text
A -> CHALLENGE_A -> B
A <- PROOF_B <- B
A <- CHALLENGE_B <- B
A -> PROOF_A -> B
```

Proofs bind verifier `NodeId`, claimant `NodeId`, discovery session nonce and authentication context.

Authentication context domain tag:

```text
OFFLINE_PROTOCOL_DISCOVERY_AUTH_V1
```

Candidate becomes `AUTHENTICATED` only when local identity valid, remote identity valid, local proof accepted by remote and remote proof accepted locally.

## 15. AuthenticatedPeer and Encounter

`AuthenticatedPeer`:

```text
nodeId
identityDocument
candidateId
authenticatedAt
authenticationSessionId
capabilities
```

It does not contain `TRUSTED = true`.

`Encounter`:

```text
encounterId
localNodeId
remoteNodeId
candidateId
startedAt
lastContactAt
endedAt
state
discoveryTransport
authenticationSessionId
observationCount
```

Encounter states:

```text
CREATING
ACTIVE
DEGRADED
CONTACT_LOST
CLOSED
FAILED
```

One active logical encounter per remote node is the initial duplicate policy. Simultaneous physical channels must converge deterministically.

## 16. Contact, Rotation, Rediscovery and Recovery

`ProximityObservation`:

```text
observationId
candidateId
encounterId?
observedAt
signalStrength?
transportReference
metadata
```

Signal strength is observational and not exact distance.

Contact loss uses `CONTACT_LOSS_GRACE_PERIOD`; missing one BLE observation does not close encounter immediately.

Ephemeral ID rotation must not destroy active encounter. After authentication, encounter is linked to `remoteNodeId`.

Rediscovery after closed encounter creates new candidate, performs full authentication and creates a new encounter ID even for same remote node.

Recovery after process death assumes physical channels are destroyed, marks connected candidates expired/recoverable, marks active encounters `CONTACT_LOST`, preserves history and emits events.

## 17. Privacy and Replay Model

Privacy v1 reduces passive tracking through hidden `NodeId`, hidden public key, rotating ephemeral IDs, minimal advertisement and no names/object/transfer metadata.

It does not guarantee perfect anonymity, global adversary resistance, full fingerprinting resistance or identity hiding after control-channel exchange.

Replay resistance mechanisms:

- ephemeral ID rotation;
- advertisement nonce;
- session nonce;
- single-use challenges;
- proof binding;
- sequence numbers;
- state machine validation.

Must resist replay of old advertisements, identity frames, challenges, proofs, auth results and encounter-ready frames.

## 18. Events, Errors and Metrics

Discovery events include advertising/scanning start/stop, ephemeral ID create/rotate, advertisement received/rejected, candidate create/update/compatible/authenticated/rejected/expired, control connection, protocol negotiation, identity validation and auth proof events.

Encounter events include creation, activation, observations, degraded/recovered, contact lost, closed, failed and duplicate encounter merge.

Error taxonomy includes advertisement, candidate, control channel, protocol negotiation, peer authentication, encounter and recovery errors.

Metrics include advertisement totals/rejections, candidates created/expired, control connections/failures, negotiation failures, identity validation failures, authentication attempts/success/failure, proof replays, encounters created/closed/failed, duplicate encounters and timing metrics from discovery to candidate/control/authenticated/encounter.

Logs may include `candidateId`, `encounterId`, remote `NodeId` only after authentication, hashed ephemeral ID, state transitions, transport type, duration and error code. Logs must not include private keys, complete challenges/proofs, sensitive payloads, user names or transfer content.

## 19. Public API

```text
startAdvertising()
stopAdvertising()
startScanning()
stopScanning()
getLocalAdvertisement()
acceptObservation(observation)
getCandidate(candidateId)
getActiveCandidates()
openControlChannel(candidateId)
authenticateCandidate(candidateId)
rejectCandidate(candidateId, reason)
expireCandidate(candidateId)
getEncounter(encounterId)
getActiveEncounters()
markContactObserved(encounterId, observation)
markContactLost(encounterId)
closeEncounter(encounterId, reason)
recover()
subscribeDiscoveryEvents(listener)
subscribeEncounterEvents(listener)
```

## 20. Transport Contracts

```text
DiscoveryAdvertiser
  start(advertisementProvider)
  updateAdvertisement()
  stop()

DiscoveryScanner
  start(observationConsumer)
  stop()

ControlChannelFactory
  open(transportReference)
  close(channelReference)
```

Core must not know BLE.

## 21. Integration Boundaries

With Identity Core: Discovery receives identity docs and uses Identity Core to validate documents, create challenges, create proofs and verify proofs. Discovery does not implement crypto.

With Transfer Core: no operational integration yet. Encounter may later provide `remoteNodeId`, `encounterId` and contact availability. Discovery Core must not inspect manifests/chunks, initiate transfers, decide object priorities or modify transfer state.

## 22. Testing Strategy

Tests:

- ephemeral ID generation/rotation;
- advertisement encoding/validation;
- candidate and encounter state machines;
- deduplication and duplicate encounter policy;
- timeouts/recovery;
- protocol test vectors for advertisements, control frames, hello, negotiation and auth context;
- property tests ensuring `NodeId` and public key never appear in advertisements;
- integration with two DiscoveryCore instances, fake discovery transport, fake control channel and real/fake IdentityCore;
- Android hardware tests with two devices for BLE advertising/scanning, GATT control, identity exchange, mutual PoP, encounter, contact loss and rediscovery.

## 23. Mandatory Acceptance Scenarios

- A: anonymous-compatible discovery creates candidate with unknown `NodeId` and public key.
- B: mutual offline authentication creates encounters on both nodes.
- C: passive advertisement inspection reveals no identity, object or transfer metadata.
- D: ephemeral ID rotates.
- E: rotation during active encounter leaves encounter active.
- F: altered identity document rejects candidate and creates no encounter.
- G: invalid proof rejects candidate and creates no encounter.
- H: old proof/session replay rejected.
- I: contact loss grace expires and encounter closes.
- J: rediscovery creates new encounter for same remote `NodeId`.
- K: process death preserves history but creates no false active physical channel.
- L: simultaneous discovery converges without duplicate active encounter.

## 24. Functional Requirements

- **FR-001**: System MUST advertise presence via discovery transport.
- **FR-002**: System MUST discover compatible peers offline.
- **FR-003**: Advertisements MUST NOT contain `NodeId`.
- **FR-004**: Advertisements MUST NOT contain public key.
- **FR-005**: System MUST use rotating `EphemeralDiscoveryId`.
- **FR-006**: System MUST create `DiscoveryCandidate` before authentication.
- **FR-007**: Candidate MUST NOT be authenticated by observation.
- **FR-008**: System MUST establish `ControlChannel`.
- **FR-009**: System MUST negotiate versions.
- **FR-010**: System MUST exchange identity documents.
- **FR-011**: System MUST validate identities through Identity Core.
- **FR-012**: System MUST execute mutual Proof of Possession.
- **FR-013**: Proofs MUST bind to `sessionNonce`.
- **FR-014**: Proofs MUST bind to both `NodeId`s.
- **FR-015**: System MUST create `AuthenticatedPeer` only after mutual auth.
- **FR-016**: Encounter MUST require authenticated peer.
- **FR-017**: System MUST persist encounters.
- **FR-018**: System MUST detect contact loss.
- **FR-019**: System MUST support rediscovery.
- **FR-020**: New authenticated contact after closure MUST create new encounter.
- **FR-021**: Ephemeral ID rotation MUST NOT destroy active encounter.
- **FR-022**: Duplicate active encounters MUST resolve deterministically.
- **FR-023**: System MUST recover after process death.
- **FR-024**: System MUST emit observable events.
- **FR-025**: System MUST produce metrics.
- **FR-026**: Discovery Core MUST be BLE-independent.

## 25. Acceptance and Rejection Criteria

Accepted when two Android nodes discover offline, advertisements do not expose `NodeId` or public key, ephemeral IDs rotate, incompatible candidates reject, valid candidates establish control channel, versions negotiate, identities validate, mutual PoP succeeds, unilateral auth fails, replays fail, encounters require mutual auth, contact loss and rediscovery work, rotation does not kill encounter, duplicates converge, process death creates no false active physical channel, Discovery Core has no Android BLE dependency and protocol vectors/logs/events/metrics exist.

Rejected if `NodeId` or public key is advertised, BLE MAC is identity, advertisement observation authenticates peer, identity document validation alone is considered authentication, only one peer proves possession, proofs are not session-bound, old proofs replay, encounter exists before mutual auth, rotation destroys active encounter, process death preserves false physical channel, Discovery Core imports Android BLE APIs, or Discovery Core starts transfers/queries chunks.

## 26. Entregables

```text
/specs/004-offline-peer-discovery/spec.md
/core/discovery-domain/
/core/discovery-application/
/core/discovery-contracts/
/core/discovery-testing/
/core/encounter-domain/
/core/encounter-application/
/transports/ble-discovery-contracts/
/transports/ble-control-contracts/
/apps/android-spike/ble-discovery-adapter/
/docs/architecture/OFFLINE-PEER-DISCOVERY.md
/docs/architecture/ENCOUNTER-MODEL.md
/docs/privacy/DISCOVERY-PRIVACY-MODEL.md
/docs/security/DISCOVERY-THREAT-MODEL.md
/docs/protocol/BLE-ADVERTISEMENT-BUDGET.md
/docs/testing/DISCOVERY-PROTOCOL-TEST-VECTORS.md
/docs/adr/ADR-006-EPHEMERAL-DISCOVERY-ID.md
/docs/adr/ADR-007-DISCOVERY-CONTROL-CHANNEL.md
/docs/adr/ADR-008-ENCOUNTER-LIFECYCLE.md
```

## 27. Relacion con SPEC anteriores

SPEC-001 validates physical communication. SPEC-002 defines durable logical transfer. SPEC-003 defines who a node is. SPEC-004 detects nearby compatible nodes, protects stable identity during advertising, establishes initial control channel, validates identities, performs mutual Proof of Possession and creates logical encounters.

Final principle:

```text
Advertisement reveals compatible presence.
Identity proves who controls a private key.
Authentication proves the present peer possesses that key.
Encounter represents that two authenticated nodes met physically.
```
