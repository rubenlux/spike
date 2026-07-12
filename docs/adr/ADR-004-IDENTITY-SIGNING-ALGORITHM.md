# ADR-004: Identity Signing Algorithm

**Status**: Proposed  
**Date**: 2026-07-09  
**Spec**: `specs/003-node-identity/spec.md`

## Context

Identity documents and proofs require deterministic, mature, offline-verifiable signatures.

## Decision

Use Ed25519 as the initial identity signing algorithm.

Identity signing keys must not be reused directly for encryption, session keys or file keys. Secure sessions will define separate ephemeral keys and key agreement.

## Consequences

- Small public keys and signatures.
- Good fit for offline verification and deterministic test vectors.
- Android secure key storage support must be measured per target device/provider.
- Provider abstraction is required so platform-specific key storage can vary.

## Rejected Alternatives

- RSA for initial identity keys.
- ECDSA as default root identity signing key.
- Homegrown signature primitives.
