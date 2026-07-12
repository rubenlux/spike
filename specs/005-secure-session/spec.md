# SPEC-005 — Secure Session Protocol

**Estado:** Draft
**Tipo:** Core Security Protocol Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-001, SPEC-002, SPEC-003, SPEC-004
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Conectividad requerida:** Ninguna
**Internet requerido:** No
**Servidor requerido:** No
**Autoridad central requerida:** No
**Transporte específico requerido:** No

---

# 1. Propósito

Definir e implementar el protocolo criptográfico mediante el cual dos nodos previamente descubiertos pueden establecer una sesión segura autenticada, confidencial y transport-independent.

La sesión segura debe permitir:

* negociar una versión compatible del protocolo;
* vincular la sesión a las identidades criptográficas de ambos nodos;
* generar claves efímeras por sesión;
* ejecutar un acuerdo de claves autenticado;
* demostrar posesión de las claves de identidad;
* vincular criptográficamente el handshake completo;
* derivar claves independientes para cada dirección;
* cifrar y autenticar frames;
* proteger contra replay;
* detectar modificación, truncamiento y reordenamiento inválido;
* destruir claves efímeras cuando dejan de ser necesarias;
* cerrar sesiones de forma explícita;
* detectar pérdida abrupta de sesión;
* impedir reutilización accidental de nonces;
* soportar recuperación del protocolo sin persistir claves efímeras activas;
* funcionar completamente offline;
* operar sobre BLE, Wi-Fi Direct o futuros transportes.

La SPEC define la transición:

```text id="sp00501"
AUTHENTICATED PEER

        │

        ▼

SECURE SESSION HANDSHAKE

        │

        ▼

AUTHENTICATED EPHEMERAL KEY AGREEMENT

        │

        ▼

TRANSCRIPT VERIFICATION

        │

        ▼

SESSION KEY DERIVATION

        │

        ▼

SECURE SESSION

        │

        ▼

ENCRYPTED AUTHENTICATED FRAMES
```

---

# 2. Problema fundamental

Después de SPEC-004 existe:

```text id="sp00502"
Physical Proximity

+

Compatible Protocol

+

Identity Document Validation

+

Mutual Proof of Possession

+

Authenticated Peer

+

Encounter
```

Pero todavía no existe:

```text id="sp00503"
Confidentiality

Forward Secrecy

Session Keys

Encrypted Control Frames

Authenticated Encryption

Nonce Management

Replay Window

Secure Rekeying

Cryptographic Session Closure
```

Un peer autenticado puede estar conectado sobre un transporte observable, manipulable o controlado por un atacante.

Por tanto:

> La autenticación de identidad debe utilizarse para autenticar un acuerdo de claves efímero, no para reutilizar directamente las claves de identidad como claves de cifrado.

---

# 3. Objetivo de seguridad

Después de completar exitosamente el handshake:

1. ambos nodos conocen la identidad criptográfica del peer;

2. ambos nodos verificaron posesión de la clave privada de identidad correspondiente;

3. ambos nodos contribuyeron material efímero al acuerdo de claves;

4. ambos nodos derivaron el mismo secreto compartido;

5. cada dirección utiliza claves independientes;

6. las claves de identidad de largo plazo no cifran tráfico;

7. comprometer posteriormente una identity private key no revela automáticamente sesiones pasadas;

8. modificar cualquier parte crítica del handshake invalida la sesión;

9. frames modificados son rechazados;

10. frames repetidos son rechazados;

11. nonces no se reutilizan bajo una misma clave;

12. una sesión no puede confundirse con otra;

13. una sesión segura no depende del transporte físico subyacente.

---

# 4. Modelo de amenazas

La SPEC considera adversarios capaces de:

```text id="sp00504"
Observe Traffic

Capture Frames

Replay Frames

Delay Frames

Drop Frames

Reorder Frames

Modify Frames

Inject Frames

Open Multiple Connections

Force Reconnection

Trigger Process Death

Trigger Transport Loss

Present Malformed Public Keys

Present Invalid Signatures

Attempt Session Confusion

Attempt Reflection

Attempt Unknown Key-Share

Attempt Nonce Reuse

Attempt Resource Exhaustion
```

La SPEC no pretende resolver completamente:

```text id="sp00505"
Compromised Operating System

Compromised Device Kernel

Stolen Unlocked Device

Side-Channel Attacks

Global Passive Adversary

Traffic Analysis

Physical Device Extraction

Post-Quantum Adversaries

Sybil Attacks

Malicious Trusted Application Layer

Denial of Service at Radio Layer
```

---

# 5. Hipótesis arquitectónica

Es posible construir un `Secure Session Core` donde:

1. la identidad de largo plazo utiliza Ed25519;

2. cada sesión genera claves efímeras X25519;

3. el acuerdo de claves produce un shared secret;

4. el handshake completo se serializa canónicamente;

5. ambos peers firman el transcript relevante con sus claves de identidad;

6. las firmas vinculan identidades, roles, claves efímeras, nonces y contexto;

7. HKDF-SHA-256 deriva claves independientes;

8. ChaCha20-Poly1305 protege frames;

9. cada dirección posee su propia clave y espacio de secuencia;

10. sequence numbers determinan nonces únicos;

11. frames repetidos son rechazados;

12. el transporte solo mueve bytes opacos;

13. las claves de sesión no se persisten para recuperación ordinaria;

14. después de process death se establece una sesión nueva;

15. una nueva sesión puede continuar operaciones lógicas persistentes de capas superiores.

---

# 6. Resultado esperado

Al finalizar debe existir:

```text id="sp00506"
SECURE SESSION CORE
────────────────────────────────────

Session Negotiation

Handshake State Machine

Role Resolution

Ephemeral Key Generation

Authenticated Key Agreement

Canonical Handshake Transcript

Transcript Hash

Identity Signature Binding

Shared Secret Derivation

Session Key Derivation

Directional Keys

Nonce Derivation

AEAD Encryption

AEAD Decryption

Replay Protection

Sequence Validation

Secure Frame Envelope

Rekeying Policy

Session Closure

Session Failure Handling

Ephemeral Key Destruction

Session Events

Security Metrics

Secure Session Transport Contracts

Protocol Test Vectors
```

---

# 7. Principio arquitectónico fundamental

```text id="sp00507"
IDENTITY KEY ≠ SESSION KEY ≠ TRANSPORT KEY
```

Responsabilidades:

```text id="sp00508"
IDENTITY KEY

Long-Term Identity

Identity Signatures

Handshake Authentication


EPHEMERAL KEY

Per-Session Key Agreement

Forward Secrecy Contribution


SESSION KEYS

Traffic Encryption

Traffic Authentication


TRANSPORT

Moves Opaque Bytes
```

---

# 8. Principios no negociables

## 8.1 Offline Sovereignty

Todo el protocolo MUST funcionar sin Internet, servidores, PKI externa ni infraestructura central.

---

## 8.2 Identity Keys Do Not Encrypt Traffic

Ed25519 identity keys MUST NOT utilizarse directamente para cifrar tráfico ni como input directo de ChaCha20-Poly1305.

---

## 8.3 Ephemeral Key Agreement

Cada sesión MUST generar un nuevo par X25519 efímero.

---

## 8.4 Forward Secrecy

Las claves efímeras MUST destruirse después de completar su propósito.

La pérdida posterior de una identity private key no debe ser suficiente por sí sola para reconstruir sesiones históricas capturadas.

---

## 8.5 Mutual Authentication

Ambos peers MUST autenticar el handshake.

---

## 8.6 Transcript Binding

Versiones, identidades, roles, claves efímeras, nonces y parámetros negociados MUST estar criptográficamente vinculados al transcript.

---

## 8.7 Directional Keys

A→B y B→A MUST utilizar claves diferentes.

---

## 8.8 Unique Nonces

Un nonce AEAD MUST NOT reutilizarse bajo la misma clave.

---

## 8.9 Replay Protection

Frames ya aceptados MUST NOT aceptarse nuevamente.

---

## 8.10 Fail Closed

Errores criptográficos, estados inválidos o inconsistencias MUST destruir o rechazar la sesión.

---

## 8.11 No Silent Downgrade

Una versión o suite criptográfica inferior no debe seleccionarse silenciosamente.

---

## 8.12 Session Independence

La destrucción del transporte físico no debe permitir reusar una sesión criptográfica muerta.

---

# 9. Fuera de alcance

Esta SPEC no implementará:

* Double Ratchet;
* asynchronous messaging encryption;
* prekeys;
* X3DH;
* MLS;
* group encryption;
* message-level end-to-end encryption durable;
* routing;
* multi-hop;
* store-carry-forward;
* trust policies;
* revocation distribuida;
* Internet relay;
* post-quantum key agreement;
* hardware attestation;
* anonymous credentials;
* deniable authentication;
* traffic padding completo;
* metadata hiding completo;
* session migration entre dispositivos.

---

# 10. Criptografía inicial

Suite inicial:

```text id="sp00509"
Identity Signature:

Ed25519


Ephemeral Key Agreement:

X25519


Hash:

SHA-256


Key Derivation:

HKDF-SHA-256


Authenticated Encryption:

ChaCha20-Poly1305


Secure Random:

Operating System CSPRNG
```

Todas las primitivas deben provenir de bibliotecas criptográficas maduras.

Prohibido implementar criptografía manualmente.

---

# 11. Cryptographic Suite Identifier

Formato conceptual:

```text id="sp00510"
SecureSessionSuiteV1
────────────────────────────────

suiteId

identitySignatureAlgorithm

keyAgreementAlgorithm

hashAlgorithm

kdfAlgorithm

aeadAlgorithm
```

Identificador inicial:

```text id="sp00511"
OFFLINE_SESSION_SUITE_V1
```

Los algoritmos no deben inferirse implícitamente.

---

# 12. Arquitectura

```text id="sp00512"
┌──────────────────────────────────────────────┐
│               APPLICATION / SDK              │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│             SECURE SESSION SERVICE           │
│                                              │
│ createSession()                              │
│ acceptSession()                              │
│ processHandshakeFrame()                      │
│ encryptFrame()                               │
│ decryptFrame()                               │
│ requestRekey()                               │
│ closeSession()                               │
└───────────────┬──────────────────────────────┘
                │
       ┌────────┼───────────────┐
       │        │               │
       ▼        ▼               ▼
┌────────────┐ ┌────────────┐ ┌────────────────┐
│ HANDSHAKE  │ │ KEY        │ │ SECURE RECORD  │
│ ENGINE     │ │ SCHEDULE   │ │ RECORD  LAYER  │
│            │ │            │ │                │
│ Negotiate  │ │ DH         │ │ Encrypt        │
│ Transcript │ │ HKDF       │ │ Decrypt        │
│ Authenticate││ Directional│ │ Replay         │
└──────┬─────┘ └──────┬─────┘ └────────┬───────┘
       │              │                │
       └──────────────┼────────────────┘
                      ▼
┌──────────────────────────────────────────────┐
│                 CRYPTO PROVIDER              │
│                                              │
│ generateX25519KeyPair()                      │
│ x25519()                                     │
│ sha256()                                     │
│ hkdf()                                       │
│ signIdentity()                               │
│ verifyIdentitySignature()                    │
│ aeadEncrypt()                                │
│ aeadDecrypt()                                │
│ secureRandom()                               │
│ destroySecret()                              │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│         SECURE SESSION TRANSPORT CONTRACT    │
│                                              │
│ sendOpaqueBytes()                            │
│ receiveOpaqueBytes()                         │
│ closeTransport()                             │
│ subscribeTransportEvents()                   │
└──────────────────────────────────────────────┘
```

---

# 13. Bounded Context

La SPEC define:

```text id="sp00513"
SecureSession
```

Entidades:

```text id="sp00514"
SecureSession

HandshakeContext

SessionKeyMaterial

ReplayWindow

SecureSessionEvent
```

Value Objects:

```text id="sp00515"
SecureSessionId

HandshakeId

SessionNonce

EphemeralPublicKey

TranscriptHash

CipherSuiteId

SendSequence

ReceiveSequence

KeyEpoch

CloseReason
```

Servicios:

```text id="sp00516"
HandshakeService

RoleResolutionService

TranscriptService

AuthenticatedKeyAgreementService

SessionKeyDerivationService

SecureRecordService

ReplayProtectionService

RekeyService

SessionLifecycleService
```

---

# 14. SecureSessionId

Cada sesión debe poseer identificador único.

Formato recomendado:

```text id="sp00517"
UUIDv7
```

No debe derivarse únicamente de:

```text id="sp00518"
NodeIds

Transport Connection

BLE Handle

Wi-Fi Direct Group

IP Address

EncounterId
```

Puede asociarse a un `EncounterId`, pero no ser equivalente.

---

# 15. Session Roles

Roles criptográficos:

```text id="sp00519"
INITIATOR

RESPONDER
```

Los roles deben ser inequívocos.

Deben vincularse al transcript.

---

# 16. Simultaneous Session Initiation

Dos peers pueden intentar iniciar simultáneamente.

Debe existir una política determinista de convergencia.

Regla inicial:

```text id="sp00520"
Compare Canonical NodeId Bytes

Lower NodeId = INITIATOR

Higher NodeId = RESPONDER
```

Si ambos abren conexiones simultáneamente:

* se determina rol canónico;
* se conserva un handshake;
* se cancela el redundante;
* se emite evento.

La comparación es exclusivamente técnica.

No representa autoridad ni prioridad.

---

# 17. Handshake State Machine

Estados:

```text id="sp00521"
NEW

NEGOTIATING

EPHEMERAL_KEY_GENERATED

HELLO_SENT

HELLO_RECEIVED

KEY_EXCHANGE

TRANSCRIPT_READY

LOCAL_AUTH_SENT

REMOTE_AUTH_VERIFIED

KEYS_DERIVED

KEY_CONFIRMING

ESTABLISHED

REKEYING

CLOSING

CLOSED

FAILED
```

Transiciones no declaradas deben rechazarse.

---

# 18. Handshake Overview

```text id="sp00522"
INITIATOR                              RESPONDER

    │                                      │
    │──── SESSION_HELLO ──────────────────►│
    │                                      │
    │◄─── SESSION_HELLO_RESPONSE ──────────│
    │                                      │
    │──── HANDSHAKE_AUTH ─────────────────►│
    │                                      │
    │◄─── HANDSHAKE_AUTH ──────────────────│
    │                                      │
    │──── KEY_CONFIRMATION ────────────────►│
    │                                      │
    │◄─── KEY_CONFIRMATION ────────────────│
    │                                      │

             SECURE SESSION ESTABLISHED
```

---

# 19. SESSION_HELLO

Contenido conceptual:

```text id="sp00523"
SessionHello
────────────────────────────────

sessionProtocolVersion

supportedSuites

initiatorNodeId

responderNodeId

encounterId

handshakeId

initiatorSessionNonce

initiatorEphemeralPublicKey

initiatorRole

capabilities
```

---

# 20. SESSION_HELLO_RESPONSE

```text id="sp00524"
SessionHelloResponse
────────────────────────────────

selectedProtocolVersion

selectedSuite

initiatorNodeId

responderNodeId

encounterId

handshakeId

initiatorSessionNonce

responderSessionNonce

initiatorEphemeralPublicKey

responderEphemeralPublicKey

initiatorRole

responderRole
```

---

# 21. Negotiation

La negociación debe seleccionar:

```text id="sp00525"
Secure Session Protocol Version

Cryptographic Suite
```

Reglas:

1. seleccionar únicamente intersección compatible;

2. aplicar política determinista;

3. preferir versión/suite más fuerte permitida por política local;

4. vincular resultado al transcript;

5. rechazar downgrade incompatible.

Error:

```text id="sp00526"
NoCompatibleSecureSessionSuite
```

---

# 22. Ephemeral Key Generation

Cada peer genera:

```text id="sp00527"
X25519 Ephemeral KeyPair
```

Requisitos:

* nuevo por handshake;
* generado mediante CSPRNG;
* no reutilizado entre sesiones;
* no persistido como identidad;
* private component no expuesto;
* destruido cuando corresponda.

---

# 23. X25519 Public Key Validation

La implementación debe:

* validar longitud;
* rechazar encoding inválido;
* ejecutar la operación mediante librería madura;
* rechazar shared secret all-zero.

Error:

```text id="sp00528"
InvalidEphemeralPublicKey

InvalidSharedSecret
```

---

# 24. Shared Secret

Cálculo:

```text id="sp00529"
dhSharedSecret =
X25519(
    localEphemeralPrivateKey,
    remoteEphemeralPublicKey
)
```

El `dhSharedSecret`:

* nunca se transmite;
* nunca se loguea;
* no se utiliza directamente como traffic key;
* se entrega al Key Schedule;
* se destruye después de derivar claves.

---

# 25. Canonical Handshake Transcript

Debe incluir como mínimo:

```text id="sp00530"
Domain Tag

Protocol Version

Selected Suite

Initiator NodeId

Responder NodeId

EncounterId

HandshakeId

Initiator Session Nonce

Responder Session Nonce

Initiator Ephemeral Public Key

Responder Ephemeral Public Key

Initiator Role

Responder Role

Negotiated Capabilities
```

Serialización:

```text id="sp00531"
Canonical Binary Encoding
```

preferentemente compatible con la estrategia adoptada en SPEC-003.

---

# 26. Transcript Hash

```text id="sp00532"
transcriptHash =
SHA-256(
    DOMAIN_TAG
    ||
    canonicalHandshakeTranscript
)
```

Domain Tag:

```text id="sp00533"
OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1
```

---

# 27. Handshake Authentication

Cada peer firma:

```text id="sp00534"
authPayload =
DOMAIN_TAG
||
role
||
localNodeId
||
remoteNodeId
||
transcriptHash
```

Domain Tag:

```text id="sp00535"
OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1
```

Firma:

```text id="sp00536"
identitySignature =
Ed25519.Sign(
    identityPrivateKey,
    authPayload
)
```

---

# 28. HANDSHAKE_AUTH

```text id="sp00537"
HandshakeAuth
────────────────────────────────

handshakeId

signerNodeId

signerRole

transcriptHash

identitySignature
```

El receptor debe verificar:

* handshakeId;
* signerNodeId;
* signerRole;
* expected transcriptHash;
* IdentityDocument;
* signature;
* state machine.

---

# 29. Unknown Key-Share Protection

El transcript y las firmas MUST incluir ambos NodeIds.

Debe ser imposible que:

```text id="sp00538"
A believes key shared with B

while

B believes key shared with C
```

sin provocar fallo criptográfico.

---

# 30. Reflection Protection

Roles y direcciones MUST incluirse en:

* transcript;
* authentication payload;
* key derivation context.

Un mensaje generado por un initiator no debe validarse como mensaje equivalente de responder.

---

# 31. Key Schedule

Después de validar autenticación mutua:

```text id="sp00539"
earlySecret =
HKDF-Extract(
    salt = transcriptHash,
    IKM = dhSharedSecret
)
```

Después:

```text id="sp00540"
sessionMasterSecret =
HKDF-Expand(
    earlySecret,
    info =
        DOMAIN_TAG
        ||
        transcriptHash
        ||
        initiatorNodeId
        ||
        responderNodeId,
    length = REQUIRED_LENGTH
)
```

Domain Tag:

```text id="sp00541"
OFFLINE_PROTOCOL_SESSION_MASTER_V1
```

---

# 32. Directional Key Derivation

Derivar independientemente:

```text id="sp00542"
initiatorToResponderKey

responderToInitiatorKey

initiatorNonceBase

responderNonceBase

initiatorRekeySecret

responderRekeySecret

exporterSecret
```

Cada valor debe usar:

* label diferente;
* context explícito;
* HKDF-Expand independiente.

---

# 33. Key Separation

Prohibido reutilizar el mismo key material para:

```text id="sp00543"
Send Encryption

Receive Encryption

Nonce Base

Rekey

Exported Key Material
```

---

# 34. Local Session Key Mapping

Si local role:

```text id="sp00544"
INITIATOR

sendKey = initiatorToResponderKey

receiveKey = responderToInitiatorKey
```

Si:

```text id="sp00545"
RESPONDER

sendKey = responderToInitiatorKey

receiveKey = initiatorToResponderKey
```

---

# 35. Key Confirmation

Después de derivar claves, ambos peers deben demostrar que poseen las mismas claves.

Conceptualmente:

```text id="sp00546"
confirmationTag =
HMAC-SHA-256(
    confirmationKey,
    DOMAIN_TAG
    ||
    transcriptHash
    ||
    senderRole
)
```

Alternativamente puede utilizarse un frame AEAD protegido con claves derivadas.

La decisión final debe documentarse mediante ADR.

Ninguna sesión entra en `ESTABLISHED` sin confirmación mutua.

---

# 36. Secure Record Layer

Después de `ESTABLISHED`, toda información sensible del protocolo debe viajar mediante:

```text id="sp00547"
SecureRecord
```

Modelo:

```text id="sp00548"
SecureRecord
────────────────────────────────

recordVersion

sessionId

keyEpoch

sequenceNumber

recordType

ciphertextLength

ciphertext

authenticationTag
```

---

# 37. Associated Data

AEAD Additional Authenticated Data:

```text id="sp00549"
AAD =
CanonicalEncode(
    recordVersion,
    sessionId,
    keyEpoch,
    sequenceNumber,
    recordType,
    ciphertextLength
)
```

Los campos de routing/framing visibles deben quedar autenticados.

---

# 38. Nonce Construction

ChaCha20-Poly1305 requiere nonce de 96 bits.

Construcción conceptual:

```text id="sp00550"
nonce =
nonceBase XOR encode96(sequenceNumber)
```

Requisitos:

* nonceBase diferente por dirección;
* sequenceNumber monotónico;
* no reutilización;
* overflow terminal.

---

# 39. Send Sequence

Cada dirección mantiene:

```text id="sp00551"
sendSequence
```

Inicial:

```text id="sp00552"
0
```

Después de cada record cifrado exitosamente:

```text id="sp00553"
sendSequence++
```

No debe reutilizarse un sequence number después de cifrar un record bajo la misma clave.

---

# 40. Receive Sequence

La política v1 será inicialmente estricta:

```text id="sp00554"
EXPECTED NEXT SEQUENCE ONLY
```

Si:

```text id="sp00555"
receivedSequence < expectedSequence

→ REPLAY


receivedSequence > expectedSequence

→ OUT_OF_ORDER
```

Esto simplifica el primer protocolo sobre canales confiables y ordenados.

Una replay window para datagramas queda para futuras versiones.

---

# 41. Encrypt Pipeline

```text id="sp00556"
Plaintext Frame

      │

      ▼

Validate Session ESTABLISHED

      │

      ▼

Validate Record Size

      │

      ▼

Read Current sendSequence

      │

      ▼

Construct Nonce

      │

      ▼

Construct AAD

      │

      ▼

ChaCha20-Poly1305 Encrypt

      │

      ▼

Increment sendSequence

      │

      ▼

Return SecureRecord
```

---

# 42. Decrypt Pipeline

```text id="sp00557"
SecureRecord

      │

      ▼

Validate Maximum Size

      │

      ▼

Strict Parse

      │

      ▼

Validate SessionId

      │

      ▼

Validate KeyEpoch

      │

      ▼

Validate Sequence

      │

      ▼

Construct Nonce

      │

      ▼

Construct AAD

      │

      ▼

AEAD Decrypt

      │

      ├──── INVALID TAG ──► REJECT / FAIL POLICY

      ▼

Advance Receive Sequence

      │

      ▼

Deliver Plaintext
```

El receive sequence MUST NOT avanzar antes de autenticación AEAD exitosa.

---

# 43. Secure Record Types

Tipos iniciales:

```text id="sp00558"
CONTROL

TRANSFER_CONTROL

TRANSPORT_UPGRADE

HEARTBEAT

KEY_UPDATE

CLOSE

ERROR
```

La SPEC no define todavía payloads de transferencia.

---

# 44. Session State Machine

```text id="sp00559"
NEW

HANDSHAKING

AUTHENTICATING

DERIVING_KEYS

CONFIRMING_KEYS

ESTABLISHED

REKEYING

CLOSING

CLOSED

FAILED
```

Transiciones:

```text id="sp00560"
NEW
 │
 ▼
HANDSHAKING
 │
 ├──── protocol failure ─────► FAILED
 ▼
AUTHENTICATING
 │
 ├──── auth failure ─────────► FAILED
 ▼
DERIVING_KEYS
 │
 ├──── crypto failure ───────► FAILED
 ▼
CONFIRMING_KEYS
 │
 ├──── confirmation failure ─► FAILED
 ▼
ESTABLISHED
 │
 ├──── rekey threshold ──────► REKEYING
 │                              │
 │                              └────► ESTABLISHED
 │
 ├──── close request ─────────► CLOSING
 │                              │
 │                              ▼
 │                            CLOSED
 │
 └──── terminal failure ──────► FAILED
```

---

# 45. Session Persistence

Persistir únicamente metadata no secreta necesaria:

```text id="sp00561"
sessionId

encounterId

localNodeId

remoteNodeId

role

protocolVersion

suiteId

state

createdAt

establishedAt

closedAt

failureReason

recordsSent

recordsReceived

keyEpoch
```

No persistir para recuperación ordinaria:

```text id="sp00562"
Ephemeral Private Keys

DH Shared Secret

Session Master Secret

Traffic Keys

Nonce Bases

Rekey Secrets

Confirmation Keys
```

---

# 46. Process Death

Si el proceso muere:

```text id="sp00563"
ACTIVE SECURE SESSION

        │

        ▼

PROCESS DEATH

        │

        ▼

SESSION KEYS LOST

        │

        ▼

OLD SESSION NOT RESUMED

        │

        ▼

NEW DISCOVERY / ENCOUNTER OR REAUTHENTICATION

        │

        ▼

NEW SECURE SESSION HANDSHAKE
```

Las operaciones lógicas persistentes de capas superiores pueden continuar mediante una nueva sesión.

---

# 47. Transport Loss

Si el transporte desaparece:

```text id="sp00564"
ESTABLISHED

     │

     ▼

TRANSPORT LOST

     │

     ▼

SESSION FAILED OR CLOSED
```

La versión inicial no soporta migración de una misma sesión criptográfica entre transportes.

Una nueva conexión requiere una nueva sesión segura.

---

# 48. Session Resumption

Fuera de alcance para v1.

No se implementarán:

```text id="sp00565"
Session Tickets

PSK Resumption

0-RTT

Long-Lived Session Secrets
```

---

# 49. Rekeying

La sesión debe soportar renovación de traffic keys.

Triggers:

```text id="sp00566"
MAX_RECORDS_PER_KEY

MAX_BYTES_PER_KEY

MAX_SESSION_DURATION

EXPLICIT_REKEY_REQUEST
```

---

# 50. Key Epoch

Cada conjunto de traffic keys posee:

```text id="sp00567"
keyEpoch
```

Inicial:

```text id="sp00568"
0
```

Después de rekey:

```text id="sp00569"
keyEpoch++
```

---

# 51. Rekey Derivation

Conceptualmente:

```text id="sp00570"
nextRekeySecret =
HKDF-Expand(
    currentRekeySecret,
    DOMAIN_TAG
    ||
    transcriptHash
    ||
    nextKeyEpoch
)
```

Desde el nuevo secreto se derivan nuevas:

```text id="sp00571"
sendKey

receiveKey

nonceBase

rekeySecret
```

Debe existir sincronización explícita del cambio de epoch.

---

# 52. Rekey State Machine

```text id="sp00572"
ESTABLISHED

      │

      ▼

REKEY_REQUEST

      │

      ▼

REKEY_ACK

      │

      ▼

DERIVE NEXT KEYS

      │

      ▼

KEY_UPDATE_CONFIRM

      │

      ▼

ACTIVATE NEXT EPOCH

      │

      ▼

DESTROY OLD KEYS

      │

      ▼

ESTABLISHED
```

---

# 53. Rekey Failure

Si no puede determinarse inequívocamente qué epoch está activo:

```text id="sp00573"
FAIL CLOSED

SESSION FAILED
```

No se permite fallback silencioso a claves antiguas.

---

# 54. Session Closure

Cierre limpio:

```text id="sp00574"
NODE A                             NODE B

   │                                 │
   │──── ENCRYPTED CLOSE ───────────►│
   │                                 │
   │◄─── ENCRYPTED CLOSE_ACK ────────│
   │                                 │

DESTROY TRAFFIC KEYS

DESTROY REKEY SECRETS

STATE → CLOSED
```

---

# 55. Abrupt Closure

Ante:

```text id="sp00575"
Transport Loss

Process Death

Fatal Cryptographic Error

Sequence Violation

Invalid Key Epoch

Repeated Authentication Failures
```

el sistema debe:

1. dejar de aceptar nuevos records;

2. destruir material secreto en memoria cuando sea posible;

3. marcar sesión FAILED;

4. emitir evento;

5. cerrar transporte.

---

# 56. Secret Destruction

La implementación debe realizar best-effort zeroization de:

```text id="sp00576"
Ephemeral Private Keys

DH Shared Secret

Intermediate HKDF Material

Session Master Secret

Traffic Keys

Nonce Bases

Rekey Secrets

Confirmation Keys
```

Debe documentarse que runtimes con garbage collection pueden limitar garantías de zeroization.

---

# 57. Frame Size Limits

Configurable:

```text id="sp00577"
MAX_HANDSHAKE_FRAME_SIZE

MAX_SECURE_RECORD_SIZE

MAX_PLAINTEXT_SIZE

MAX_CIPHERTEXT_SIZE
```

Los límites deben validarse antes de asignaciones de memoria significativas.

---

# 58. Handshake Timeout

Debe existir:

```text id="sp00578"
HANDSHAKE_TIMEOUT
```

Al expirar:

```text id="sp00579"
non-terminal handshake

→ FAILED

→ destroy ephemeral secrets

→ close transport
```

---

# 59. Resource Exhaustion Protection

Límites mínimos:

```text id="sp00580"
MAX_CONCURRENT_HANDSHAKES

MAX_HANDSHAKES_PER_REMOTE_NODE

MAX_FAILED_HANDSHAKES_PER_WINDOW

HANDSHAKE_BACKOFF

MAX_SECURE_SESSIONS

MAX_PENDING_FRAMES
```

La política de rate limiting no debe utilizar NodeId antes de autenticar identidad cuando no sea seguro hacerlo.

---

# 60. Secure Session Events

Eventos mínimos:

```text id="sp00581"
SESSION_CREATION_STARTED

SESSION_ROLE_RESOLVED

SESSION_NEGOTIATION_STARTED

SESSION_SUITE_SELECTED

EPHEMERAL_KEY_GENERATED

SESSION_HELLO_SENT

SESSION_HELLO_RECEIVED

SESSION_HELLO_RESPONSE_SENT

SESSION_HELLO_RESPONSE_RECEIVED

HANDSHAKE_TRANSCRIPT_CREATED

HANDSHAKE_TRANSCRIPT_HASHED

LOCAL_HANDSHAKE_AUTH_CREATED

REMOTE_HANDSHAKE_AUTH_RECEIVED

REMOTE_HANDSHAKE_AUTH_VERIFIED

REMOTE_HANDSHAKE_AUTH_REJECTED

DH_SHARED_SECRET_CREATED

SESSION_KEYS_DERIVED

KEY_CONFIRMATION_SENT

KEY_CONFIRMATION_RECEIVED

KEY_CONFIRMATION_VERIFIED

SESSION_ESTABLISHED

SECURE_RECORD_ENCRYPTED

SECURE_RECORD_DECRYPTED

SECURE_RECORD_REJECTED

REPLAY_REJECTED

SEQUENCE_VIOLATION

REKEY_STARTED

REKEY_COMPLETED

REKEY_FAILED

SESSION_CLOSE_STARTED

SESSION_CLOSED

SESSION_FAILED

SESSION_SECRET_DESTRUCTION_STARTED

SESSION_SECRET_DESTRUCTION_COMPLETED
```

---

# 61. Error Taxonomy

```text id="sp00582"
SecureSessionError

├── NegotiationError
│   ├── UnsupportedSessionVersion
│   ├── NoCompatibleSecureSessionSuite
│   ├── InvalidSuiteSelection
│   └── DowngradeDetected
│
├── RoleError
│   ├── InvalidSessionRole
│   ├── RoleConflict
│   └── DuplicateHandshakeConflict
│
├── EphemeralKeyError
│   ├── EphemeralKeyGenerationFailure
│   ├── InvalidEphemeralPublicKey
│   ├── EphemeralKeyReuseDetected
│   └── InvalidSharedSecret
│
├── TranscriptError
│   ├── MalformedHandshakeTranscript
│   ├── TranscriptMismatch
│   ├── TranscriptHashMismatch
│   └── HandshakeBindingFailure
│
├── HandshakeAuthenticationError
│   ├── InvalidHandshakeIdentity
│   ├── InvalidHandshakeSignature
│   ├── HandshakeNodeMismatch
│   ├── HandshakeRoleMismatch
│   ├── UnknownKeyShareDetected
│   └── ReflectionDetected
│
├── KeyDerivationError
│   ├── HkdfFailure
│   ├── KeyScheduleFailure
│   ├── KeyConfirmationFailure
│   └── InvalidDerivedKeyMaterial
│
├── SecureRecordError
│   ├── SecureRecordTooLarge
│   ├── MalformedSecureRecord
│   ├── SessionIdMismatch
│   ├── InvalidKeyEpoch
│   ├── ReplayDetected
│   ├── OutOfOrderRecord
│   ├── SequenceOverflow
│   ├── AeadAuthenticationFailure
│   └── DecryptionFailure
│
├── RekeyError
│   ├── RekeyThresholdExceeded
│   ├── RekeyProtocolFailure
│   ├── RekeyEpochMismatch
│   ├── RekeyConfirmationFailure
│   └── RekeySynchronizationLost
│
├── SessionStateError
│   ├── SecureSessionNotFound
│   ├── SecureSessionNotEstablished
│   ├── SecureSessionAlreadyTerminal
│   └── InvalidSecureSessionStateTransition
│
└── SecretLifecycleError
    ├── SecretDestructionFailure
    └── SecretLifecycleInvariantViolation
```

---

# 62. Observabilidad

Métricas mínimas:

```text id="sp00583"
secure_session_handshakes_started_total

secure_session_handshakes_completed_total

secure_session_handshakes_failed_total

secure_session_negotiation_failures_total

secure_session_authentication_failures_total

secure_session_invalid_ephemeral_keys_total

secure_session_shared_secret_failures_total

secure_session_key_confirmation_failures_total

secure_sessions_established_total

secure_sessions_closed_total

secure_sessions_failed_total

secure_records_encrypted_total

secure_records_decrypted_total

secure_records_rejected_total

secure_record_replays_rejected_total

secure_record_sequence_violations_total

secure_record_aead_failures_total

secure_session_rekeys_started_total

secure_session_rekeys_completed_total

secure_session_rekeys_failed_total

secure_session_handshake_duration_ms

secure_session_duration_ms
```

---

# 63. Logging

Logs estructurados pueden incluir:

```text id="sp00584"
timestamp

sessionId

handshakeId

encounterId

localNodeId

remoteNodeId

role

protocolVersion

suiteId

stateBefore

stateAfter

keyEpoch

sequenceNumber

eventType

durationMs

errorCode
```

No deben incluir:

```text id="sp00585"
Identity Private Keys

Ephemeral Private Keys

DH Shared Secret

Session Master Secret

Traffic Keys

Nonce Bases

Rekey Secrets

Plaintext Payloads

Complete Authentication Proofs
```

---

# 64. API pública inicial

```text id="sp00586"
SecureSessionCore
────────────────────────────────────

createSession(encounterId, remoteNodeId)

acceptSession(encounterId, remoteNodeId)

processHandshakeFrame(sessionId, frame)

getSession(sessionId)

getSessionSnapshot(sessionId)

encrypt(sessionId, recordType, plaintextStream)

decrypt(sessionId, secureRecordStream)

requestRekey(sessionId)

closeSession(sessionId, reason)

handleTransportLoss(sessionId, reason)

recover()

subscribeEvents(listener)
```

---

# 65. SecureSessionSnapshot

```text id="sp00587"
SecureSessionSnapshot
────────────────────────────────

sessionId

encounterId

localNodeId

remoteNodeId

role

protocolVersion

suiteId

state

keyEpoch

recordsSent

recordsReceived

bytesSent

bytesReceived

createdAt

establishedAt

lastActivityAt

closedAt

failureReason
```

No expone material criptográfico secreto.

---

# 66. Transport Contract

```text id="sp00588"
SecureSessionTransport
────────────────────────────────

sendOpaqueFrame(frameStream)

receiveOpaqueFrame()

close()

getState()

subscribeEvents()
```

El Secure Session Core no debe conocer:

```text id="sp00589"
BLE

GATT

Wi-Fi Direct

TCP

Socket

IP

Android Context
```

---

# 67. Integración con Identity Core

```text id="sp00590"
SECURE SESSION CORE

        │

        ├── getLocalNodeId()

        ├── getIdentityDocument()

        ├── sign(
        │      SECURE_SESSION_AUTH_DOMAIN,
        │      transcriptPayload
        │   )

        └── verify(
               remoteIdentity,
               SECURE_SESSION_AUTH_DOMAIN,
               transcriptPayload,
               signature
            )

        │

        ▼

IDENTITY CORE
```

Secure Session Core no accede a bytes de identity private key.

---

# 68. Integración con Discovery & Encounter

```text id="sp00591"
SPEC-004

AUTHENTICATED PEER

+

ACTIVE ENCOUNTER

        │

        ▼

SPEC-005

SECURE SESSION HANDSHAKE

        │

        ▼

ESTABLISHED SECURE SESSION
```

Precondiciones iniciales:

```text id="sp00592"
Encounter ACTIVE

Remote Identity VALID

Remote Peer AUTHENTICATED
```

---

# 69. Integración con Transfer Core

La integración operacional completa queda para futuras SPECs.

Transfer Core podrá recibir posteriormente:

```text id="sp00593"
SecureChannelHandle
```

capaz de transportar control frames.

Transfer Core MUST NOT:

* acceder a traffic keys;
* implementar AEAD;
* construir nonces;
* administrar sequence numbers criptográficos.

---

# 70. Protocol Test Vectors

Deben existir vectores deterministas para:

```text id="sp00594"
Canonical SessionHello Encoding

Canonical SessionHelloResponse Encoding

Handshake Transcript Encoding

Transcript Hash

Handshake Authentication Payload

Ed25519 Handshake Signature Verification

X25519 Shared Secret

HKDF Extract

Session Master Secret

Directional Traffic Keys

Nonce Bases

Rekey Secrets

Key Confirmation

Secure Record AAD

Nonce Construction

ChaCha20-Poly1305 Encryption

ChaCha20-Poly1305 Decryption

Rekey Derivation
```

Los vectores deben ser reutilizables por implementaciones Kotlin, Rust, Swift u otros lenguajes.

---

# 71. Testing Strategy

## Unit Tests

```text id="sp00595"
Role Resolution

Version Negotiation

Suite Negotiation

Handshake State Machine

Session State Machine

Transcript Construction

Transcript Hashing

Directional Key Mapping

Nonce Construction

Sequence Validation

Replay Rejection

Key Epoch Validation

Rekey State Machine

Error Taxonomy
```

---

## Cryptographic Tests

```text id="sp00596"
Ed25519 Known-Answer Tests

X25519 RFC Test Vectors

HKDF RFC Test Vectors

ChaCha20-Poly1305 RFC Test Vectors

Modified Ciphertext Rejection

Modified AAD Rejection

Modified Tag Rejection

All-Zero Shared Secret Rejection
```

---

## Property-Based Tests

Propiedades:

```text id="sp00597"
same valid handshake produces compatible directional keys

initiator send key equals responder receive key

responder send key equals initiator receive key

send key never equals receive key under normal derivation

different session nonce changes derived keys

different ephemeral key changes derived keys

different NodeId changes transcript hash

different role changes authentication payload

modified transcript invalidates signature

modified ciphertext never authenticates

modified AAD never authenticates

replayed sequence never delivers plaintext twice

receive sequence advances only after successful AEAD authentication

sequence overflow never reuses nonce

new key epoch changes traffic keys

old key epoch rejected after successful rekey

process restart never restores ESTABLISHED without new handshake
```

---

## Integration Tests

```text id="sp00598"
Two IdentityCore Instances

Two DiscoveryCore Instances

Authenticated Encounter

Two SecureSessionCore Instances

Fake Ordered Transport

Full Handshake

Mutual Authentication

Key Confirmation

Bidirectional Encrypted Frames

Rekey

Clean Closure
```

---

## Fault Injection Tests

```text id="sp00599"
drop SESSION_HELLO

duplicate SESSION_HELLO

modify ephemeral public key

swap NodeIds

swap roles

modify selected suite

modify transcript field

invalid handshake signature

all-zero shared secret

process death after DH

process death after key derivation

drop key confirmation

duplicate secure record

reorder secure records

modify ciphertext

modify AAD

modify authentication tag

force sequence overflow

transport loss during ESTABLISHED

transport loss during REKEYING

rekey epoch mismatch

process death during closure
```

---

# 72. Escenarios obligatorios de aceptación

## Scenario A — Offline Secure Session

```text id="sp005100"
NODE A

+

NODE B

+

No Internet

+

Authenticated Encounter

        │

        ▼

Secure Session Handshake

        │

        ▼

Mutual Handshake Authentication

        │

        ▼

Key Confirmation

        │

        ▼

ESTABLISHED
```

Resultado:

```text id="sp005101"
Both nodes derive compatible directional keys.
```

---

## Scenario B — Bidirectional Encryption

```text id="sp005102"
A encrypts Record 0

B decrypts Record 0

B encrypts Record 0

A decrypts Record 0
```

Resultado:

```text id="sp005103"
PLAINTEXT RECOVERED

AUTHENTICATION VALID
```

---

## Scenario C — Ciphertext Modification

Modificar un byte.

Resultado:

```text id="sp005104"
AeadAuthenticationFailure

Plaintext not delivered
```

---

## Scenario D — AAD Modification

Modificar:

```text id="sp005105"
sessionId

or

keyEpoch

or

sequenceNumber

or

recordType
```

Resultado:

```text id="sp005106"
REJECTED
```

---

## Scenario E — Replay

Enviar dos veces el mismo SecureRecord.

Resultado:

```text id="sp005107"
FIRST:

ACCEPTED


SECOND:

ReplayDetected
```

---

## Scenario F — Out-of-Order

Enviar:

```text id="sp005108"
Sequence 0

Sequence 2
```

sin `Sequence 1`.

Resultado v1:

```text id="sp005109"
OutOfOrderRecord
```

---

## Scenario G — Identity Substitution

Modificar un NodeId en transcript.

Resultado:

```text id="sp005110"
TranscriptMismatch

or

InvalidHandshakeSignature

SESSION NOT ESTABLISHED
```

---

## Scenario H — Ephemeral Key Modification

MITM modifica public key efímera.

Resultado:

```text id="sp005111"
Handshake Authentication Failure

SESSION NOT ESTABLISHED
```

---

## Scenario I — Different Session Nonce

Reutilizar handshake frames de sesión anterior.

Resultado:

```text id="sp005112"
TranscriptMismatch

SESSION NOT ESTABLISHED
```

---

## Scenario J — Simultaneous Initiation

```text id="sp005113"
A initiates

B initiates

same Encounter
```

Resultado:

```text id="sp005114"
Deterministic Role Resolution

One Canonical Session

Redundant Handshake Closed
```

---

## Scenario K — Process Death

```text id="sp005115"
Session ESTABLISHED

Kill Process

Restart

recover()
```

Resultado:

```text id="sp005116"
Old session not restored as ESTABLISHED.

Old secret material unavailable.

New handshake required.
```

---

## Scenario L — Transport Loss

```text id="sp005117"
ESTABLISHED

Transport Lost
```

Resultado:

```text id="sp005118"
SESSION FAILED OR CLOSED

Secrets Destroyed

New Session Required
```

---

## Scenario M — Rekey

```text id="sp005119"
ESTABLISHED

Reach Rekey Threshold

Rekey Handshake

Key Epoch 0 → 1

Continue Transfer
```

Resultado:

```text id="sp005120"
New traffic keys active.

Old epoch rejected.

Communication continues.
```

---

## Scenario N — Rekey Synchronization Failure

Peers divergen sobre epoch activo.

Resultado:

```text id="sp005121"
FAIL CLOSED

SESSION FAILED
```

---

## Scenario O — Clean Closure

```text id="sp005122"
ESTABLISHED

Encrypted CLOSE

Encrypted CLOSE_ACK

Destroy Secrets
```

Resultado:

```text id="sp005123"
CLOSED
```

---

# 73. Requisitos funcionales

### FR-001

El sistema MUST establecer sesiones seguras completamente offline.

### FR-002

Cada sesión MUST utilizar nuevas claves efímeras X25519.

### FR-003

Las claves de identidad MUST NOT cifrar tráfico.

### FR-004

El sistema MUST negociar versión y suite explícitamente.

### FR-005

El sistema MUST impedir downgrade silencioso.

### FR-006

El handshake MUST vincular ambos NodeIds.

### FR-007

El handshake MUST vincular roles.

### FR-008

El handshake MUST vincular claves efímeras.

### FR-009

El handshake MUST vincular nonces de sesión.

### FR-010

El handshake MUST vincular EncounterId.

### FR-011

Ambos peers MUST firmar el transcript.

### FR-012

Ambos peers MUST verificar autenticación remota.

### FR-013

Shared secrets all-zero MUST rechazarse.

### FR-014

HKDF MUST derivar claves independientes.

### FR-015

Cada dirección MUST utilizar traffic key diferente.

### FR-016

Cada dirección MUST utilizar nonce base diferente.

### FR-017

Cada dirección MUST mantener sequence numbers independientes.

### FR-018

Nonces MUST ser únicos bajo una misma clave.

### FR-019

Frames MUST utilizar AEAD.

### FR-020

AAD MUST autenticar metadata crítica del record.

### FR-021

Replay MUST rechazarse.

### FR-022

Out-of-order records MUST rechazarse en v1.

### FR-023

Receive sequence MUST avanzar solo después de autenticación exitosa.

### FR-024

La sesión MUST ejecutar key confirmation mutua.

### FR-025

La sesión MUST soportar rekeying.

### FR-026

Rekey ambiguity MUST provocar fail closed.

### FR-027

Claves antiguas MUST destruirse después de rekey exitoso.

### FR-028

Session secrets MUST NOT persistirse para recuperación ordinaria.

### FR-029

Process death MUST requerir nueva sesión.

### FR-030

Transport loss MUST invalidar la sesión v1.

### FR-031

El sistema MUST soportar cierre limpio.

### FR-032

El sistema MUST destruir secretos best-effort.

### FR-033

El sistema MUST emitir eventos.

### FR-034

El sistema MUST producir métricas.

### FR-035

Secure Session Core MUST ser independiente del transporte.

---

# 74. Requisitos no funcionales

### NFR-001 — Offline Sovereignty

El protocolo completo debe operar sin Internet.

### NFR-002 — Forward Secrecy

Las sesiones deben utilizar material efímero nuevo.

### NFR-003 — Cryptographic Separation

Identity, key agreement, traffic encryption y rekeying deben utilizar material criptográfico separado.

### NFR-004 — Transport Independence

El Core no debe depender de BLE, Wi-Fi Direct, IP ni sockets.

### NFR-005 — Fail Closed

Errores criptográficos deben impedir entrega de plaintext o establecimiento de sesión.

### NFR-006 — Determinism

Negotiation, role resolution, transcript construction y key mapping deben ser deterministas.

### NFR-007 — Replay Resistance

Un SecureRecord aceptado no debe entregarse dos veces.

### NFR-008 — Nonce Safety

La arquitectura debe impedir reutilización de nonce bajo una misma clave.

### NFR-009 — Bounded Resources

Frames, handshakes, sesiones y buffers deben poseer límites configurables.

### NFR-010 — Secret Minimization

Material secreto debe mantenerse únicamente durante el tiempo necesario.

### NFR-011 — Cross-Implementation Compatibility

Implementaciones compatibles deben producir los mismos transcripts, hashes, claves derivadas, nonces y ciphertexts para los mismos vectores de prueba.

### NFR-012 — Testability

El Core debe poder probarse sin radios ni hardware Android.

---

# 75. Criterios de éxito

La SPEC será `ACCEPTED` cuando:

1. dos nodos autenticados establezcan una sesión segura offline;

2. cada sesión utilice claves efímeras nuevas;

3. las identity keys no cifren tráfico;

4. versiones y suites se negocien explícitamente;

5. transcript incluya identidades, roles, nonces y ephemeral keys;

6. ambos peers autentiquen el transcript;

7. MITM que modifique ephemeral keys sea detectado;

8. unknown key-share sea mitigado por binding explícito;

9. reflection sea mitigado por roles y domain separation;

10. directional keys sean diferentes;

11. key confirmation sea mutua;

12. frames utilicen ChaCha20-Poly1305;

13. ciphertext alterado sea rechazado;

14. AAD alterado sea rechazado;

15. replay sea rechazado;

16. sequence inválida sea rechazada;

17. receive sequence avance solo después de AEAD válido;

18. nonces no se reutilicen;

19. rekey cambie traffic keys;

20. epoch ambiguity provoque fail closed;

21. process death no restaure secretos;

22. transport loss invalide sesión v1;

23. cierre limpio destruya secretos;

24. existan test vectors interoperables;

25. Secure Session Core no dependa del transporte.

---

# 76. Criterios de rechazo

La SPEC debe rechazarse si:

* Ed25519 identity key cifra tráfico;

* se reutilizan ephemeral keypairs entre sesiones;

* el shared secret se usa directamente como traffic key;

* ambos sentidos usan la misma traffic key;

* ambos sentidos comparten nonce space;

* transcript no incluye ambos NodeIds;

* transcript no incluye roles;

* transcript no incluye ephemeral keys;

* una sola firma autentica toda la sesión bilateral;

* no existe key confirmation;

* sequence number puede repetirse bajo la misma clave;

* receive sequence avanza antes de verificar AEAD;

* replay entrega plaintext dos veces;

* rekey permite ambigüedad de epoch;

* process death restaura una sesión como ESTABLISHED sin secretos válidos;

* session secrets se persisten en texto plano;

* Secure Session Core depende de BLE o Wi-Fi Direct.

---

# 77. Entregables

```text id="sp005124"
/specs/005-secure-session/spec.md

/core/secure-session-domain/

/core/secure-session-application/

/core/secure-session-contracts/

/core/secure-session-testing/

/crypto/session-crypto-contracts/

/docs/architecture/SECURE-SESSION-PROTOCOL.md

/docs/security/SECURE-SESSION-THREAT-MODEL.md

/docs/security/SECRET-LIFECYCLE.md

/docs/protocol/SECURE-SESSION-WIRE-FORMAT.md

/docs/testing/SECURE-SESSION-TEST-VECTORS.md

/docs/adr/ADR-009-SECURE-SESSION-CRYPTO-SUITE.md

/docs/adr/ADR-010-HANDSHAKE-TRANSCRIPT-BINDING.md

/docs/adr/ADR-011-SESSION-KEY-SCHEDULE.md

/docs/adr/ADR-012-SECURE-RECORD-NONCE-STRATEGY.md

/docs/adr/ADR-013-SESSION-REKEYING.md
```

---

# 78. Decisiones abiertas

La implementación debe producir evidencia para decidir:

1. mantener la suite Ed25519 + X25519 o adoptar una construcción protocolar auditada de mayor nivel;

2. evaluar Noise Protocol Framework como base formal del handshake;

3. patrón Noise más adecuado para el modelo de identidad del proyecto;

4. HMAC explícito frente a AEAD para key confirmation;

5. formato binario definitivo del SecureRecord;

6. límites máximos de records por key epoch;

7. límites máximos de bytes por key epoch;

8. duración máxima de una sesión;

9. estrategia futura para unordered transports;

10. replay windows;

11. session resumption;

12. transport migration;

13. channel binding al transporte físico cuando aporte valor;

14. protección adicional contra DoS pre-authentication;

15. separación futura entre root identity keys y operational signing keys;

16. integración con hardware-backed key operations;

17. migración post-cuántica;

18. auditoría criptográfica externa antes de considerar producción.

---

# 79. Relación con SPEC anteriores

```text id="sp005125"
SPEC-001
HYBRID OFFLINE LINK

        │

        ▼

Validates Physical Communication


SPEC-002
TRANSFER-INDEPENDENT CORE

        │

        ▼

Defines Durable Logical Transfer


SPEC-003
DECENTRALIZED NODE IDENTITY

        │

        ▼

Defines Who a Node Is


SPEC-004
OFFLINE PEER DISCOVERY & ENCOUNTER

        │

        ▼

Finds and Authenticates Nearby Nodes


SPEC-005
SECURE SESSION PROTOCOL

        │

        ├── generates ephemeral session keys
        ├── authenticates key agreement
        ├── binds handshake transcript
        ├── derives directional keys
        ├── encrypts protocol records
        ├── rejects replay
        ├── manages key epochs
        └── destroys ephemeral secrets
```

---

# 80. Resultado final

La SPEC-005 debe producir la frontera criptográfica segura entre nodos autenticados.

```text id="sp005126"
             AUTHENTICATED ENCOUNTER

                        │

                        ▼

              ROLE RESOLUTION

                        │

                        ▼

           EPHEMERAL X25519 KEYPAIRS

                        │

                        ▼

              KEY AGREEMENT

                        │

                        ▼

         CANONICAL HANDSHAKE TRANSCRIPT

                        │

                        ▼

          MUTUAL IDENTITY SIGNATURES

                        │

                        ▼

             TRANSCRIPT VERIFIED

                        │

                        ▼

                HKDF KEY SCHEDULE

                        │

             ┌──────────┴──────────┐

             ▼                     ▼

       A → B TRAFFIC KEY     B → A TRAFFIC KEY

             │                     │

             └──────────┬──────────┘

                        ▼

               MUTUAL KEY CONFIRMATION

                        │

                        ▼

                SECURE SESSION

                        │

                        ▼

       CHACHA20-POLY1305 SECURE RECORDS

                        │

             ┌──────────┼──────────┐

             ▼          ▼          ▼

           REPLAY     REKEY      CLOSURE
         PROTECTION
```

**La identidad autentica al nodo.**

**Las claves efímeras protegen la sesión.**

**El transcript impide sustituir el contexto criptográfico.**

**Las claves direccionales separan ambos sentidos del tráfico.**

**AEAD protege cada record.**

**La sesión puede desaparecer; la identidad y las operaciones lógicas durables permanecen.**
