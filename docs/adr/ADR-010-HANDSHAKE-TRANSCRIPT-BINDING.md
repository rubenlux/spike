# ADR-010: Handshake Transcript Binding

**Status**: Proposed  
**Date**: 2026-07-10  
**Spec**: `specs/005-secure-session/spec.md`

## Context

To prevent man-in-the-middle, replay, reflection, and Unknown Key-Share (UKS) attacks, the secure session handshake must cryptographically bind the entire negotiation context. This includes versions, suites, peer identities, roles, encounter contexts, and session nonces.

## Decision

Implement a canonical serialization of all handshake fields into a binary `Handshake Transcript`, which is then hashed and mutually signed.

### Transcript Content

The transcript MUST contain the following fields in order:
1. **Domain Tag**: `OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1`
2. **Protocol Version**: UInt16
3. **Selected Suite ID**: String (UTF-8)
4. **Encounter ID**: String (UTF-8, UUID format)
5. **Handshake ID**: String (UTF-8, UUID format)
6. **Initiator Node ID**: String (UTF-8)
7. **Responder Node ID**: String (UTF-8)
8. **Initiator Session Nonce**: 16 bytes
9. **Responder Session Nonce**: 16 bytes
10. **Initiator Ephemeral Public Key (X25519)**: 32 bytes
11. **Responder Ephemeral Public Key (X25519)**: 32 bytes
12. **Negotiated Capabilities**: Map of String to String, serialized sorted by key.

### Hashing and Signing

1. Calculate: `transcriptHash = SHA-256(canonicalHandshakeTranscript)`
2. Each peer signs the authentication payload with their Ed25519 identity key:
   `authPayload = OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1 || localRole || localNodeId || remoteNodeId || transcriptHash`
   `signature = Ed25519.Sign(identityPrivateKey, authPayload)`
3. The peer verifies the signature using the other peer's validated `IdentityDocument` public key.

## Consequences

- **Reflection Protection**: The inclusion of explicit roles (`INITIATOR` vs `RESPONDER`) and directional identifiers makes it impossible to reflect an initiator's frame back to them.
- **UKS Protection**: Mutual signatures over both `NodeId`s ensure that A and B agree on exactly who they are sharing keys with.
- **Negotiation Integrity**: Modifying any field (e.g. attempting a downgrade attack) changes the transcript and results in a signature verification failure.

## Rejected Alternatives

- **Implicit Binding**: Signing only the ephemeral keys (like in simple DH handshakes). This is vulnerable to identity substitution, reflection, and downgrade attacks.
- **JSON Serialization**: Rejected because JSON is not canonical (whitespace, key ordering, number formatting differences can lead to different transcripts and signature failures).
