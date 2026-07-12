# SPEC-003 - Decentralized Node Identity & Cryptographic Identity

**Estado**: Draft  
**Tipo**: Core Protocol Specification  
**Prioridad**: P0 - Fundacional  
**Dependencias**: SPEC-001, SPEC-002  
**Plataforma inicial**: Android  
**Alcance del Core**: Multiplataforma por diseno  
**Conectividad requerida**: Ninguna  
**Dependencia de Internet**: No  
**Servidor requerido**: No  
**Autoridad certificadora requerida**: No  
**Cuenta de usuario requerida**: No  
**Origen**: Solicitud adjunta del usuario, normalizada como especificacion de repositorio.

## 1. Proposito

Definir e implementar el subsistema de identidad criptografica descentralizada que permite a cada nodo crear su identidad localmente, obtener un identificador estable y verificable, demostrar posesion de clave privada, firmar datos, verificar firmas, intercambiar documentos publicos de identidad, detectar alteraciones, persistir su identidad de forma segura y operar completamente sin Internet, servidores, autoridades certificadoras ni cuentas de usuario.

Esta SPEC define exclusivamente la raiz criptografica de identidad. Peer discovery, secure sessions, autorizacion, routing, trust policies, revocation y migracion pertenecen a futuras SPECs.

## 2. Problema Fundamental

El proyecto no puede asumir usernames, email, phone number, server accounts, OAuth, certificate authorities or central registries.

Por tanto:

```text
Each node must be able to create, own and prove its cryptographic identity
without consulting external infrastructure.
```

La identidad debe ser self-generated, self-certifying, cryptographically verifiable, offline, persistent and transport-independent.

## 3. Hipotesis Arquitectonica

Es posible construir identidad de nodo descentralizada donde:

1. cada nodo genera localmente un par de claves;
2. la clave privada nunca abandona el control del nodo;
3. la clave publica permite derivar un identificador estable;
4. `NodeId` esta criptograficamente vinculado a la clave publica;
5. el nodo demuestra posesion mediante firmas;
6. peers verifican esa demostracion offline;
7. no hay servidor central, CA, cuenta o conexion especifica;
8. BLE, Wi-Fi Direct, IP y otros identificadores de transporte pueden cambiar sin cambiar `NodeId`;
9. la identidad persiste entre reinicios;
10. perdida o compromiso de clave privada se trata explicitamente;
11. formatos persistentes y publicos estan versionados.

## 4. Resultado Esperado

```text
NODE IDENTITY CORE
  Key Generation
  Private Key Protection
  Public Key Representation
  NodeId Derivation
  Identity Document
  Identity Persistence
  Proof of Possession
  Signing
  Signature Verification
  Identity Validation
  Identity Import
  Identity Export Policy
  Identity Lifecycle
  Identity Events
  Identity Recovery Detection
  Cryptographic Agility
```

El modulo debe funcionar sin BLE, Wi-Fi Direct, Internet, Android networking, servidores, cloud o account system.

## 5. Principio Fundamental

```text
NODE IDENTITY != TRANSPORT IDENTITY
```

Un nodo no es BLE MAC, Wi-Fi Direct MAC, IP, hostname, socket endpoint, Android device name or advertising identifier. Esos valores pueden cambiar, rotar, ocultarse, aleatorizarse o depender del sistema operativo.

## 6. Principios No Negociables

- **Generacion local**: identidad creada completamente offline.
- **Sin autoridad central**: no CA, identity server, registration server, blockchain, DNS o cloud.
- **Clave privada no exportable por defecto**: API normal no expone bytes privados.
- **Identidad autocertificable**: relacion verificable entre `NodeId` y public key.
- **Proof of Possession**: presentar public key no demuestra private-key ownership.
- **Persistencia**: identidad sobrevive application restart, process death y device reboot.
- **Fail closed**: errores criptograficos, formatos desconocidos o inconsistencias se rechazan.
- **Domain separation**: firmas para propositos distintos usan contextos distintos.
- **Serializacion canonica**: datos firmados o usados para IDs tienen bytes canonicos.
- **Agilidad criptografica controlada**: algoritmos explicitos y versionados.

## 7. Fuera de Alcance

Peer discovery, BLE advertisements, Wi-Fi Direct, secure channel, session encryption, key agreement, Double Ratchet, mensajes, grupos, multi-device identity, routing, multi-hop, store-carry-forward, reputacion, social graph, web of trust, certificados de terceros, blockchain, consenso, cloud sync, server recovery, cloud backup, migracion completa, revocacion distribuida, anonimato de red y proteccion completa contra Sybil.

## 8. Arquitectura

```text
APPLICATION / SDK
  -> IDENTITY SERVICE
       createIdentity()
       getLocalIdentity()
       getIdentityDocument()
       sign()
       verify()
       createProofOfPossession()
       verifyProofOfPossession()
       validateIdentityDocument()
  -> DOMAIN SERVICES
       Node ID Engine
       Signature Engine
       Identity Validation Engine
  -> CRYPTO PROVIDER
       generateKeyPair()
       sign()
       verify()
       secureRandom()
       hash()
  -> SECURE KEY STORAGE
       Private Key Handle
       Public Key
       Identity Metadata
       Creation State
```

## 9. Bounded Context

Bounded context: `Identity`.

Entities:

- `LocalNodeIdentity`
- `IdentityDocument`
- `RemoteNodeIdentity`
- `IdentityEvent`

Value Objects:

- `NodeId`
- `KeyId`
- `PublicKey`
- `Signature`
- `Challenge`
- `IdentityVersion`
- `KeyAlgorithm`
- `SignatureAlgorithm`
- `HashAlgorithm`
- `DomainTag`

Services:

- `NodeIdDerivationService`
- `IdentityCreationService`
- `IdentityValidationService`
- `SignatureService`
- `ProofOfPossessionService`
- `IdentityRecoveryService`

Repositories:

- `LocalIdentityRepository`
- `RemoteIdentityRepository`
- `IdentityEventRepository`

## 10. Initial Cryptography

Initial algorithms:

```text
Signing Algorithm: Ed25519
Hash Algorithm: SHA-256
Secure Random: Operating System CSPRNG
```

Mature cryptographic libraries must be used. Manual implementation of primitives is forbidden.

The identity signing key must not be reused directly as an encryption key, session key, transport key, file encryption key or password derivation key.

## 11. KeyId and NodeId

`KeyId` identifies a key, not a node:

```text
KeyId = sha256:<64 lowercase hex of canonical public key bytes>
```

`NodeId` is self-certifying:

```text
canonicalPublicKey = CanonicalEncode(keyAlgorithm, publicKeyBytes)
digest = SHA-256(canonicalPublicKey)
NodeId = "node:v1:sha256:" + lowercaseHex(digest)
```

`NodeId` must be deterministic, stable, transport-independent, server-independent, self-certifying, canonically serialized, algorithm-explicit and versioned.

## 12. Identity Document

Public document:

```text
IdentityDocument
  identityVersion
  nodeId
  keyId
  keyAlgorithm
  signatureAlgorithm
  publicKey
  createdAt
  capabilities
  documentSignature
```

V1 required fields:

- `identityVersion = 1`
- `nodeId`
- `keyId`
- `keyAlgorithm = Ed25519`
- `signatureAlgorithm = Ed25519`
- `publicKey`
- `createdAt`
- `documentSignature`

`capabilities` may exist but must not advertise transport capabilities.

The document is self-signed over a canonical unsigned encoding with domain tag `OFFLINE_PROTOCOL_IDENTITY_DOCUMENT_V1`.

## 13. Identity Document Validation

Pipeline:

```text
Receive document
  -> Validate maximum size
  -> Parse strictly
  -> Validate version
  -> Validate algorithms
  -> Validate field structure
  -> Canonicalize public key
  -> Derive and compare KeyId
  -> Derive and compare NodeId
  -> Canonicalize unsigned document
  -> Verify self-signature
  -> VALID or REJECT
```

Expected mismatch errors include `KeyIdMismatch`, `NodeIdPublicKeyMismatch` and `InvalidIdentityDocumentSignature`.

## 14. Canonical Encoding

Identity cannot depend on ordinary JSON for cryptographic operations.

Requirements:

- deterministic encoding;
- deterministic field ordering;
- no ambiguous numbers;
- no duplicate fields;
- no alternate equivalent encodings;
- strict decoder;
- bounded input size.

Evaluation recommendation: Deterministic CBOR. Final choice must be recorded in ADR-005.

## 15. Domain Separation

Signature input:

```text
DOMAIN_TAG || VERSION || CANONICAL_PAYLOAD
```

Minimum tags:

- `OFFLINE_PROTOCOL_IDENTITY_DOCUMENT_V1`
- `OFFLINE_PROTOCOL_IDENTITY_PROOF_V1`
- `OFFLINE_PROTOCOL_APPLICATION_SIGNATURE_V1`

A valid signature in one domain must not validate in another.

## 16. Proof of Possession

Verifier creates an unpredictable challenge. Claimant signs challenge, verifier node ID, claimant node ID and context with domain tag `OFFLINE_PROTOCOL_IDENTITY_PROOF_V1`.

Validation checks:

- supported proof version;
- challenge exists locally;
- challenge unused;
- challenge not expired;
- verifier node ID matches local node;
- claimant node ID matches expected peer;
- context matches operation;
- signature valid;
- challenge consumed atomically after successful validation or terminal attempt.

Challenges require at least 256 bits of entropy, OS CSPRNG, single-use semantics and bounded lifetime.

## 17. Local Identity Lifecycle

`LocalNodeIdentity`:

```text
nodeId
keyId
identityVersion
keyAlgorithm
publicKey
privateKeyHandle
identityDocument
state
createdAt
```

States:

```text
CREATING
ACTIVE
INVALID
KEY_UNAVAILABLE
COMPROMISED
RETIRED
```

Creation pipeline:

```text
NO IDENTITY -> CREATING -> Generate Ed25519 KeyPair -> Persist Private Key
-> Canonicalize Public Key -> Derive KeyId -> Derive NodeId
-> Create IdentityDocument -> Self-Sign -> Validate Document
-> Persist Metadata -> ACTIVE
```

Identity must not become `ACTIVE` until validation and persistence complete.

## 18. Secure Key Storage

Android implementation must evaluate Android Keystore, hardware-backed Keystore and StrongBox where available. Architecture must not assume equal protection across devices.

Protection levels:

```text
SOFTWARE
TEE
STRONGBOX
UNKNOWN
```

Hardware-backed absence must not block protocol execution; it only downgrades protection classification.

Forbidden normal APIs:

- `getPrivateKeyBytes()`
- `exportPrivateKey()`
- `logPrivateKey()`
- serializing private key bytes into identity documents

Crypto adapters work through opaque private key handles.

## 19. Persistence and Startup Validation

Persist:

- `NodeId`
- `KeyId`
- `PublicKey`
- `PrivateKeyHandle` reference
- `IdentityDocument`
- identity state
- creation metadata
- protection level
- identity events

Startup validation:

```text
Load metadata
  -> Resolve private key handle
  -> missing => KEY_UNAVAILABLE
  -> Validate public key
  -> Recalculate KeyId
  -> Recalculate NodeId
  -> Validate identity document
  -> Local sign/verify self-test
  -> ACTIVE or INVALID
```

If metadata exists but private key is unavailable, result is `KEY_UNAVAILABLE`. The system must not silently generate a replacement, reuse the same `NodeId` with a new key or pretend identity is valid.

## 20. Compromise, Retirement and Remote Identities

`ACTIVE -> COMPROMISED`: identity must not sign new data or initiate proofs; events are emitted and evidence retained.

`ACTIVE -> RETIRED`: identity remains historically verifiable but does not sign new data and is not auto-deleted.

`RemoteNodeIdentity`:

```text
nodeId
keyId
publicKey
identityDocument
validationState
firstSeenAt
lastValidatedAt
```

Remote validation states: `UNVALIDATED`, `VALID`, `INVALID`, `UNSUPPORTED`.

Persisting a remote identity means cryptographically valid, not trusted.

```text
VALID IDENTITY != TRUSTED IDENTITY
SELF-CERTIFYING IDENTITY != SYBIL RESISTANCE
```

Trust policy and Sybil resistance are future work.

## 21. Events, Errors and Metrics

Minimum events:

```text
IDENTITY_CREATION_STARTED
IDENTITY_KEY_GENERATED
IDENTITY_KEY_PERSISTED
IDENTITY_DOCUMENT_CREATED
IDENTITY_DOCUMENT_SIGNED
IDENTITY_VALIDATED
IDENTITY_ACTIVATED
IDENTITY_STARTUP_VALIDATED
IDENTITY_KEY_UNAVAILABLE
IDENTITY_INVALID
IDENTITY_MARKED_COMPROMISED
IDENTITY_RETIRED
REMOTE_IDENTITY_RECEIVED
REMOTE_IDENTITY_VALIDATED
REMOTE_IDENTITY_REJECTED
PROOF_CHALLENGE_CREATED
PROOF_CREATED
PROOF_VALIDATED
PROOF_REJECTED
PROOF_CHALLENGE_EXPIRED
PROOF_REPLAY_REJECTED
```

`IdentityEvent` includes `eventId`, `eventType`, `localNodeId`, optional `remoteNodeId`, optional `keyId`, `occurredAt`, `sequenceNumber` and metadata. It must not include private key bytes, raw sensitive key material or complete signed payloads.

Error taxonomy includes creation, validation, signature, proof, key storage, state and recovery errors.

Minimum metrics:

- `identity_creation_total`
- `identity_creation_failures_total`
- `identity_validation_total`
- `identity_validation_failures_total`
- `identity_startup_validation_failures_total`
- `identity_signatures_created_total`
- `identity_signature_verifications_total`
- `identity_signature_verification_failures_total`
- `proof_challenges_created_total`
- `proofs_validated_total`
- `proofs_rejected_total`
- `proof_replays_rejected_total`
- `remote_identities_validated_total`
- `remote_identities_rejected_total`
- `private_key_unavailable_total`

## 22. Public API

```text
createIdentity()
getLocalIdentity()
getLocalNodeId()
getIdentityDocument()
validateIdentityDocument(document)
storeRemoteIdentity(document)
getRemoteIdentity(nodeId)
createChallenge(claimantNodeId, context)
createProof(challenge, verifierNodeId, context)
verifyProof(proof)
sign(domainTag, payload)
verify(identityDocument, domainTag, payload, signature)
markCompromised()
retireIdentity()
validateAtStartup()
recover()
subscribeEvents(listener)
```

## 23. Integration With SPEC-002

Identity Core provides `NodeId`, signature operations, identity documents and proof verification to Transfer Core.

Transfer Core may store `localNodeId` and `remoteNodeId`, but must not generate identities, access private keys, implement signatures or validate proofs directly.

## 24. Testing Strategy

Unit tests:

- NodeId derivation;
- KeyId derivation;
- canonical public key encoding;
- identity document validation;
- state machine;
- domain separation;
- proof validation;
- replay protection;
- error taxonomy.

Cryptographic vectors:

- `PublicKey -> KeyId`
- `PublicKey -> NodeId`
- `IdentityDocument -> Canonical Bytes`
- `Canonical Bytes -> Signature`
- signature verification;
- `Proof -> Canonical Bytes`
- proof signature verification.

Mandatory scenarios:

- A: offline identity creation, persistence, restart, validation, sign and verify.
- B: two independent nodes exchange identity document and proof offline.
- C: transport handles and IP change while `NodeId` remains unchanged.
- D: modified signed document is rejected.
- E: `NodeId A + PublicKey B` fails with `NodeIdPublicKeyMismatch`.
- F: proof replay validates once, then fails with `ReplayDetected`.
- G: wrong proof context fails with `ProofContextMismatch`.
- H: private key missing yields `KEY_UNAVAILABLE` and no silent replacement.
- I: process death during creation detects incomplete creation and creates no second identity silently.

## 25. Functional Requirements

- **FR-001**: Core MUST generate identity completely offline.
- **FR-002**: Core MUST use OS CSPRNG.
- **FR-003**: Core MUST generate Ed25519 keys via mature crypto library.
- **FR-004**: Core MUST protect private key through secure key storage where possible.
- **FR-005**: Core MUST derive `KeyId` deterministically.
- **FR-006**: Core MUST derive `NodeId` deterministically.
- **FR-007**: Core MUST bind `NodeId` and public key cryptographically.
- **FR-008**: Core MUST create versioned `IdentityDocument`.
- **FR-009**: `IdentityDocument` MUST be self-signed.
- **FR-010**: Core MUST validate remote documents strictly.
- **FR-011**: Core MUST support Proof of Possession.
- **FR-012**: Challenges MUST be unpredictable.
- **FR-013**: Challenges MUST expire.
- **FR-014**: Challenges MUST be single-use.
- **FR-015**: Core MUST reject replay.
- **FR-016**: Core MUST implement domain separation.
- **FR-017**: Identity MUST survive process death.
- **FR-018**: Identity MUST survive device reboot.
- **FR-019**: Core MUST detect unavailable private key.
- **FR-020**: Core MUST NOT regenerate identity silently.
- **FR-021**: Core MUST distinguish valid identity from trusted identity.
- **FR-022**: Core MUST emit observable events.
- **FR-023**: Core MUST produce metrics.
- **FR-024**: Core MUST work without real transports.

## 26. Non-Functional Requirements

- **NFR-001 - Offline Sovereignty**: essential identity operations work without Internet.
- **NFR-002 - Transport Independence**: no BLE, Wi-Fi Direct, IP or socket dependency.
- **NFR-003 - Cryptographic Correctness**: no manual primitive implementation.
- **NFR-004 - Determinism**: `KeyId`, `NodeId` and canonical bytes are deterministic.
- **NFR-005 - Fail Closed**: crypto failures cause rejection.
- **NFR-006 - Key Confidentiality**: normal API does not expose private material.
- **NFR-007 - Replay Resistance**: consumed/expired proofs do not validate.
- **NFR-008 - Testability**: domain testable without Android hardware.
- **NFR-009 - Evolvability**: persistent formats and algorithms are explicit/versioned.
- **NFR-010 - Cross-Implementation Compatibility**: compatible implementations produce same identifiers, canonical bytes and crypto results for test vectors.

## 27. Acceptance and Rejection Criteria

Accepted when a node can create and persist identity offline, the same keypair always derives the same `NodeId`, `NodeId` is independent of transport/Android IDs, identity documents are self-signed and verifiable, modified documents are rejected, two nodes prove possession offline, expired/replayed/wrong-context proofs are rejected, domain separation is tested, missing private keys are detected, no silent regeneration occurs, normal API exposes no private keys, deterministic crypto vectors exist, module tests need no radios/network, and events/metrics are observable.

Rejected if `NodeId` depends on MAC/IP/Android identifiers, identity creation or validation requires a server/Internet, private key is plaintext or exposed by normal APIs, identity key is reused for encryption, public key alone is treated as proof, proofs can be replayed, domain separation is missing, non-canonical JSON is signed directly, algorithms are implicit, lost key silently creates another identity, or valid identity is treated as trusted identity.

## 28. Entregables

```text
/specs/003-node-identity/spec.md
/core/identity-domain/
/core/identity-application/
/core/identity-contracts/
/core/identity-testing/
/crypto/crypto-provider-contracts/
/storage/identity-storage-contracts/
/docs/architecture/NODE-IDENTITY.md
/docs/security/IDENTITY-THREAT-MODEL.md
/docs/testing/IDENTITY-CRYPTO-TEST-VECTORS.md
/docs/adr/ADR-003-NODE-ID-DERIVATION.md
/docs/adr/ADR-004-IDENTITY-SIGNING-ALGORITHM.md
/docs/adr/ADR-005-CANONICAL-IDENTITY-ENCODING.md
```

## 29. Relacion con SPEC-001 and SPEC-002

```text
SPEC-001 validates physical offline link.
SPEC-002 defines durable transport-independent transfer.
SPEC-003 defines who a node is, how NodeId is derived, how possession is proven,
and how signatures are verified offline.
```

Final principle:

```text
A node does not exist because a server registers it.
A node exists because it owns a private key and can prove offline that it controls
the identity derived from that key.
```
