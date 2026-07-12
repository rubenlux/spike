# ADR-012: Secure Record Nonce Strategy

**Status**: Proposed  
**Date**: 2026-07-10  
**Spec**: `specs/005-secure-session/spec.md`

## Context

ChaCha20-Poly1305 requires a 96-bit unique nonce for every encryption operation. Reusing a nonce under the same key completely destroys the security guarantees of the cipher (revealing plaintext and allowing forgery). The system must guarantee unique nonces and defend against replayed, out-of-order, or dropped messages.

## Decision

Combine a directional random/derived `nonceBase` (12 bytes) with a monotonic sequence counter (8 bytes) to construct unique nonces, and enforce strict sequential sequence validation in v1.

### Nonce Construction

The 96-bit nonce is calculated as:

```text
nonce = nonceBase XOR pad96(sequenceNumber)
```

Where:
- `nonceBase` is the 12-byte (96-bit) value derived in the Key Schedule for the active direction.
- `sequenceNumber` is a 64-bit unsigned integer (8 bytes), padded with 4 leading zero bytes to make it 96 bits.
- `XOR` is the bitwise exclusive-OR of the two 12-byte blocks.

### Sequence Number Management

- Both `sendSequence` and `receiveSequence` start at `0` for epoch `0`.
- The `sendSequence` is incremented by `1` after each record is encrypted.
- The maximum sequence number is $2^{64} - 1$. If the sequence number overflows, the session MUST fail immediately to prevent nonce reuse (Fail Closed).

### Replay and Out-of-Order Policy (v1)

- The receiver maintains the `expectedReceiveSequence` (initially `0`).
- If a received record has `sequenceNumber == expectedReceiveSequence`, it is processed. If AEAD decryption succeeds, the `expectedReceiveSequence` is advanced by `1`.
- If `sequenceNumber < expectedReceiveSequence`, the record is rejected as a **Replay** (`ReplayDetected` error).
- If `sequenceNumber > expectedReceiveSequence`, the record is rejected as **Out of Order** (`OutOfOrderRecord` error) and the session fails.

## Consequences

- Absolute guarantee of nonce uniqueness under a specific traffic key.
- Replay attacks are completely neutralized.
- Out-of-order delivery, omissions, and truncations are immediately detected, and the connection is torn down.
- Datagram transport layers (like raw BLE packets) that can reorder packets will require a replay window or packet buffer, which is deferred to a future SPEC. This v1 works over ordered logical streams.

## Rejected Alternatives

- **Random Nonces**: Using 96-bit random nonces for each frame. This has a collision probability (birthday paradox) that is unacceptable for high-throughput or long-lived sessions.
- **Direct Counter Nonce**: Using the counter directly as the nonce. This leaks the number of sent messages and does not provide entropy alignment with the session secrets.
- **Replay Window (v1)**: Implementing a sliding bitmap replay window (e.g. RFC 6479). While useful for datagrams, it introduces complexity that is unnecessary for our initial ordered transport assumption and is deferred to v2.
