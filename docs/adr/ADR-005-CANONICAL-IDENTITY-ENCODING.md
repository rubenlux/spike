# ADR-005: Canonical Identity Encoding

**Status**: Proposed - pending implementation validation  
**Date**: 2026-07-09  
**Spec**: `specs/003-node-identity/spec.md`

## Context

Signed identity data and ID derivation require deterministic bytes. Ordinary JSON allows equivalent documents to have different byte representations and must not be signed directly.

## Decision

Evaluate deterministic CBOR for identity document and proof canonicalization.

The Identity Core will depend on a `CanonicalIdentityEncoder` contract until the concrete encoding library and exact profile are selected.

## Requirements

- Deterministic field ordering.
- No duplicate fields.
- No ambiguous numeric encodings.
- Bounded input size.
- Strict decoder.
- Cross-language test vectors.

## Consequences

- Crypto vectors are blocked until canonical encoding is finalized.
- Implementations must reject alternate encodings rather than normalize loosely.
