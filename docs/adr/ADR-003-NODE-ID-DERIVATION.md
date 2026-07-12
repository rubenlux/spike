# ADR-003: NodeId Derivation

**Status**: Proposed  
**Date**: 2026-07-09  
**Spec**: `specs/003-node-identity/spec.md`

## Context

Node identity must be stable, offline, self-certifying and independent from transport identifiers or central registries.

## Decision

Derive `NodeId` from canonical public key bytes:

```text
canonicalPublicKey = CanonicalEncode(keyAlgorithm, publicKeyBytes)
digest = SHA-256(canonicalPublicKey)
NodeId = "node:v1:sha256:" + lowercaseHex(digest)
```

`KeyId` remains separate:

```text
KeyId = "sha256:" + lowercaseHex(SHA-256(canonicalPublicKey))
```

## Consequences

- Any peer can validate `NodeId` offline from the public key.
- Transport addresses can rotate without identity change.
- Future algorithms require explicit version/algorithm migration.
- `NodeId` is stable and therefore may have privacy implications during discovery.

## Rejected Alternatives

- MAC/IP/device-name-derived identifiers.
- Random opaque node IDs without public-key binding.
- Server-issued identifiers.
