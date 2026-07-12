# ADR-011: Session Key Schedule

**Status**: Proposed  
**Date**: 2026-07-10  
**Spec**: `specs/005-secure-session/spec.md`

## Context

After generating a shared secret from X25519, the secure session must derive cryptographically separated, independent traffic keys, nonce bases, and rekeying keys for both communication directions ($A \rightarrow B$ and $B \rightarrow A$).

## Decision

Implement an HKDF-SHA-256 key schedule using `transcriptHash` as salt and context.

```text
                  dhSharedSecret (X25519)
                            │
                            ▼
    transcriptHash ──► HKDF-Extract
                            │
                            ▼
                       earlySecret
                            │
                            ▼
    Info Context  ──► HKDF-Expand
   (Domain Tag +            │
    Transcript Hash +       ▼
    NodeIds)          sessionMasterSecret (32 bytes)
                            │
      ┌─────────────────────┼─────────────────────┐
      ▼                     ▼                     ▼
HKDF-Expand           HKDF-Expand           HKDF-Expand
(Label: I2R_Key)      (Label: R2I_Key)      (Label: I2R_Nonce)
      │                     │                     │
      ▼                     ▼                     ▼
initiatorKey          responderKey          initiatorNonceBase
 (32 bytes)            (32 bytes)            (12 bytes)

      ┌─────────────────────┼─────────────────────┐
      ▼                     ▼                     ▼
HKDF-Expand           HKDF-Expand           HKDF-Expand
(Label: R2I_Nonce)    (Label: I2R_Rekey)    (Label: R2I_Rekey)
      │                     │                     │
      ▼                     ▼                     ▼
responderNonceBase    initiatorRekeySecret  responderRekeySecret
 (12 bytes)            (32 bytes)            (32 bytes)
```

### HKDF Context Labels

- **Early Secret**: `earlySecret = HKDF-Extract(salt = transcriptHash, IKM = dhSharedSecret)`
- **Session Master Secret**:
  `sessionMasterSecret = HKDF-Expand(earlySecret, info = "OFFLINE_PROTOCOL_SESSION_MASTER_V1" || transcriptHash || initiatorNodeId || responderNodeId, length = 32)`
- **Directional Keys & Bases**:
  - `initiatorToResponderKey = HKDF-Expand(sessionMasterSecret, info = "initiator-to-responder-traffic-key" || transcriptHash, length = 32)`
  - `responderToInitiatorKey = HKDF-Expand(sessionMasterSecret, info = "responder-to-initiator-traffic-key" || transcriptHash, length = 32)`
  - `initiatorNonceBase = HKDF-Expand(sessionMasterSecret, info = "initiator-nonce-base" || transcriptHash, length = 12)`
  - `responderNonceBase = HKDF-Expand(sessionMasterSecret, info = "responder-nonce-base" || transcriptHash, length = 12)`
  - `initiatorRekeySecret = HKDF-Expand(sessionMasterSecret, info = "initiator-rekey-secret" || transcriptHash, length = 32)`
  - `responderRekeySecret = HKDF-Expand(sessionMasterSecret, info = "responder-rekey-secret" || transcriptHash, length = 32)`
  - `confirmationKey = HKDF-Expand(sessionMasterSecret, info = "session-confirmation-key" || transcriptHash, length = 32)`

## Consequences

- Complete separation of keys: one direction's key compromise does not compromise the other.
- Cryptographic distinction between traffic encryption, nonce bases, rekeying secrets, and confirmation tags.
- Mutual verification: confirmation tags derived from `confirmationKey` confirm that both parties successfully completed the key schedule before session status transitions to `ESTABLISHED`.

## Rejected Alternatives

- **Single Symmetric Key**: Using the same key for both directions. This enables reflection attacks and increases key wear-out.
- **Direct DH Output**: Using the raw output of X25519 directly as the symmetric key. This violates cryptographic hygiene (DH outputs are not uniformly distributed).
