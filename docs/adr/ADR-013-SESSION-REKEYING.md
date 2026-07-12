# ADR-013: Session Rekeying

**Status**: Proposed  
**Date**: 2026-07-10  
**Spec**: `specs/005-secure-session/spec.md`

## Context

Over time and traffic volume, symmetric keys wear out. Exposing too much ciphertext under a single key makes it easier for cryptanalysts to study patterns, and nonce sequence counters could eventually overflow. Periodic session rekeying ensures forward secrecy within a session and prevents cryptographic limits from being breached.

## Decision

Implement an in-band Rekeying Protocol that increments the `keyEpoch`, derives new directional traffic keys from the current rekey secrets, and securely destroys old keys.

### Rekeying Triggers

Rekeying MUST be triggered when any of these thresholds are reached:
1. **Records Sent/Received**: $2^{32}$ records under the same key epoch (to protect sequence space).
2. **Bytes Transferred**: $1 \text{ GB}$ of ciphertext under the same key epoch.
3. **Session Duration**: $1 \text{ hour}$ of active session.
4. **Explicit Request**: A peer requests a rekey via a `KEY_UPDATE` control frame.

### Key Derivation for Epoch `N+1`

When transitioning from epoch `N` to `N+1`:

1. Derive the next rekey secret:
   `nextRekeySecret = HKDF-Expand(currentRekeySecret, info = "OFFLINE_PROTOCOL_REKEY_SECRET_V1" || transcriptHash || pad32(nextKeyEpoch), length = 32)`
2. Derive new directional keys and nonce bases:
   - `nextSendKey = HKDF-Expand(nextRekeySecret, info = "send-traffic-key" || transcriptHash, length = 32)`
   - `nextReceiveKey = HKDF-Expand(nextRekeySecret, info = "receive-traffic-key" || transcriptHash, length = 32)`
   - `nextSendNonceBase = HKDF-Expand(nextRekeySecret, info = "send-nonce-base" || transcriptHash, length = 12)`
   - `nextReceiveNonceBase = HKDF-Expand(nextRekeySecret, info = "receive-nonce-base" || transcriptHash, length = 12)`

### Synchronization Protocol

To prevent packet loss and synchronization failure, rekeying uses an explicit state machine:
1. **Initiation**: Node A sends an encrypted `KEY_UPDATE` control frame with `nextEpoch = N+1`.
2. **Derivation**: Node B receives it, derives the next epoch keys, and responds with a `KEY_UPDATE_ACK` frame encrypted under the *new* keys.
3. **Activation**: Upon verifying `KEY_UPDATE_ACK` successfully under the new keys, Node A activates the new epoch, destroys the old keys, and continues traffic.
4. **Failure**: If decryption of a rekeyed packet fails or times out, the session enters `FAILED` and fails closed.

## Consequences

- Continuous cryptographic protection for long-lived sessions.
- Clean key transitions without service interruption.
- Immediate destruction of old keys limits the window of vulnerability if active memory is compromised (forward secrecy).

## Rejected Alternatives

- **No Rekeying**: Requiring a full new handshake to rotate keys. This is slow and disrupts logical transfers.
- **Asymmetric Rekeying**: Running a new X25519 exchange for rekeying. While highly secure, it is computationally expensive on offline/mobile platforms. Symmetric rekeying via HKDF-Expand is extremely fast and provides sufficient security.
