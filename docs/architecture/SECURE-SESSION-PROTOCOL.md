# Secure Session Protocol Architecture

## 1. Overview

The `SecureSession` protocol establishes an authenticated, encrypted, and replay-protected channel between two offline nodes that have already validated each other's identity and established an encounter (per SPEC-003 and SPEC-004). 

The key design constraint is **Transport Independence**: the protocol operates entirely on opaque byte frames, meaning it can run over BLE, Wi-Fi Direct, TCP, or any other transport.

```
       ┌───────────────────────────────────────────────────┐
       │                 SecureSessionCore                 │
       └─────────────────────────┬─────────────────────────┘
                                 │
                 ┌───────────────┼───────────────┐
                 ▼               ▼               ▼
         ┌───────────────┐┌───────────────┐┌───────────────┐
         │HandshakeEngine││  KeySchedule  ││  RecordLayer  │
         └───────────────┘└───────────────┘└───────────────┘
```

---

## 2. Bounded Context & Core Entities

The core domain resides under `spike.secure.session.domain`:

- **SecureSession**: Represents the logical session. Contains the session ID, state, sequence numbers, and active keys.
- **HandshakeContext**: Tracks the handshake state, transcript, and generated ephemeral keypairs.
- **SessionKeyMaterial**: Holds derived encryption/decryption keys, nonce bases, and secrets for the active epoch.
- **SecureRecord**: Value object representing a serialized frame containing a version, session ID, sequence number, record type, epoch, ciphertext, and authentication tag.

---

## 3. Role Resolution

In decentralized environments, both nodes may initiate a session simultaneously. To prevent duplicate handshakes, the core resolves roles deterministically:
1. Compare the canonical byte representations of the initiator and responder `NodeId`s.
2. The node with the **lower** `NodeId` is designated as the **INITIATOR**.
3. The node with the **higher** `NodeId` is designated as the **RESPONDER**.

If both nodes attempt to initiate a connection, they resolve their roles using this rule. The redundant connection is closed, and the canonical handshake is preserved.

---

## 4. Handshake Protocol Flow

The handshake transitions through the following messages:

```
INITIATOR (Lower NodeId)                           RESPONDER (Higher NodeId)
   │                                                    │
   │ ── SESSION_HELLO ────────────────────────────────> │
   │    (version, suite, nonces, ephem pubkey)          │
   │                                                    │
   │ <─ SESSION_HELLO_RESPONSE ──────────────────────── │
   │    (version, suite, nonces, ephem pubkeys)         │
   │                                                    │
   │ ── HANDSHAKE_AUTH ───────────────────────────────> │
   │    (identity signature over transcript)            │
   │                                                    │
   │ <─ HANDSHAKE_AUTH ──────────────────────────────── │
   │    (identity signature over transcript)            │
   │                                                    │
   │ ── KEY_CONFIRMATION ─────────────────────────────> │
   │    (HMAC validation tag or AEAD encrypted frame)   │
   │                                                    │
   │ <─ KEY_CONFIRMATION ────────────────────────────── │
   │                                                    │
   ▼                                                    ▼
   Established Session                                  Established Session
```

### Handshake State Machine Transitions
- `NEW` $\rightarrow$ `NEGOTIATING` (Initiator sends `SESSION_HELLO`, Responder waits)
- `NEGOTIATING` $\rightarrow$ `EPHEMERAL_KEY_GENERATED` $\rightarrow$ `HELLO_SENT`/`HELLO_RECEIVED`
- `HELLO_RECEIVED` $\rightarrow$ `KEY_EXCHANGE` (Executing X25519 Diffie-Hellman)
- `KEY_EXCHANGE` $\rightarrow$ `TRANSCRIPT_READY`
- `TRANSCRIPT_READY` $\rightarrow$ `LOCAL_AUTH_SENT` $\rightarrow$ `REMOTE_AUTH_VERIFIED` (Verifying Ed25519 signatures)
- `REMOTE_AUTH_VERIFIED` $\rightarrow$ `KEYS_DERIVED` $\rightarrow$ `KEY_CONFIRMING`
- `KEY_CONFIRMING` $\rightarrow$ `ESTABLISHED` (Upon successful verification of confirmation tags)
- `ESTABLISHED` $\rightarrow$ `REKEYING` $\rightarrow$ `ESTABLISHED` (During periodic key updates)
- `ESTABLISHED` $\rightarrow$ `CLOSING` $\rightarrow$ `CLOSED` (During clean closure)
- Any state $\rightarrow$ `FAILED` (On any cryptographic or validation error - Fail Closed)

---

## 5. Security Integration

- **Identity Core Integration**: The secure session service calls the `IdentityCore` to retrieve the local `NodeId` and sign the transcript hash using the long-term Ed25519 private key (without exposing private key bytes).
- **Transport Independence**: The secure session transport contract provides callbacks for sending/receiving raw binary frames.
- **Fail Closed**: Any cryptographic mismatch immediately halts session operations, wipes session secrets, and moves state to `FAILED`.
