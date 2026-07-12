# Identity Crypto Test Vectors

**Status**: Placeholder  
**Spec**: `specs/003-node-identity/spec.md`

Deterministic vectors must be generated once the canonical encoding and crypto provider are selected. These vectors must be reusable across Kotlin, Rust, Swift or future implementations.

## Vector Format

```text
vectorId:
identityVersion:
keyAlgorithm:
signatureAlgorithm:
publicKeyBytesHex:
canonicalPublicKeyBytesHex:
keyId:
nodeId:
unsignedIdentityDocumentCanonicalHex:
identityDocumentSignatureHex:
proofCanonicalHex:
proofSignatureHex:
expectedVerification:
```

## Required Vectors

- Public key to `KeyId`.
- Public key to `NodeId`.
- Identity document to canonical bytes.
- Canonical document bytes to signature.
- Signature verification success.
- Modified document verification failure.
- Proof to canonical bytes.
- Proof signature verification success.
- Wrong domain signature verification failure.
- Replay proof rejection at protocol layer.

## Pending Decisions

- Canonical encoding: pending ADR-005.
- Concrete Ed25519 provider: pending ADR-004 implementation notes.
- Test key generation mode: fixed seed only for vectors, never production identity creation.
