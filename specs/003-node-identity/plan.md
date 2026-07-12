# Implementation Plan: Decentralized Node Identity

**Branch**: `003-node-identity`  
**Date**: 2026-07-09  
**Spec**: `specs/003-node-identity/spec.md`  
**Status**: Draft planning scaffold

## Summary

Build an offline, self-certifying Identity Core where each node locally creates an Ed25519 identity keypair, derives deterministic `KeyId` and `NodeId`, creates a self-signed identity document, signs/verifies domain-separated payloads, proves possession through single-use challenges, persists identity safely and detects missing or inconsistent private keys without silent replacement.

## Technical Context

**Language/Version**: Kotlin scaffold with multiplatform-friendly boundaries.  
**Primary Dependencies**: Crypto provider contracts first; concrete Ed25519 provider deferred.  
**Storage**: Abstract local identity, remote identity, challenge and event repositories.  
**Testing**: Unit tests, crypto vectors, property tests, integration/fault tests with fake providers.  
**Target Platform**: Initial validation on Android, but Identity Core has no Android/network dependency.  
**Project Type**: Core library modules plus provider/storage contracts.  
**Constraints**: No private key bytes in public API; no transport identity coupling; fail closed.

## Project Structure

```text
specs/003-node-identity/
├── spec.md
├── plan.md
└── tasks.md

core/
├── identity-domain/
├── identity-contracts/
├── identity-application/
└── identity-testing/

crypto/
└── crypto-provider-contracts/

storage/
└── identity-storage-contracts/

docs/
├── architecture/NODE-IDENTITY.md
├── security/IDENTITY-THREAT-MODEL.md
├── testing/IDENTITY-CRYPTO-TEST-VECTORS.md
└── adr/
    ├── ADR-003-NODE-ID-DERIVATION.md
    ├── ADR-004-IDENTITY-SIGNING-ALGORITHM.md
    └── ADR-005-CANONICAL-IDENTITY-ENCODING.md
```

## Increment Plan

1. Identity value objects and error taxonomy.
2. Domain entities and lifecycle states.
3. Canonical public key encoding and `KeyId`/`NodeId` derivation.
4. Crypto provider contracts for Ed25519, SHA-256 and CSPRNG.
5. Storage contracts for local identity, remote identity, challenges and events.
6. Identity document validation and self-signing service.
7. Domain-separated signing and verification.
8. Challenge/proof creation and replay-resistant validation.
9. Startup validation and recovery detection.
10. Test vectors and mandatory scenarios A-I.

## Key Risks

- Android secure key storage may not support non-exportable Ed25519 uniformly across target devices.
- Canonical encoding must be chosen carefully for cross-language compatibility.
- Root identity key may need future operational subkeys; this SPEC deliberately does not solve rotation.
- Self-certifying identity does not provide trust, authorization or Sybil resistance.
