# ADR-009: Secure Session Crypto Suite

**Status**: Proposed  
**Date**: 2026-07-10  
**Spec**: `specs/005-secure-session/spec.md`

## Context

The offline secure session protocol requires confidentiality, forward secrecy, mutual authentication, replay protection, and transport independence without relying on central PKI. Primitives must be well-studied, performant, and fit within multiplatform constraints (Android, iOS, etc.).

## Decision

Adopt the `OFFLINE_SESSION_SUITE_V1` cryptographic suite with the following primitives:

- **Identity Signature**: Ed25519 (for long-term peer authentication)
- **Key Agreement**: X25519 (for per-session ephemeral Diffie-Hellman)
- **Hash Function**: SHA-256 (for transcript hashing and HKDF)
- **Key Derivation Function (KDF)**: HKDF-SHA-256 (for master secret and directional key derivation)
- **Authenticated Encryption**: ChaCha20-Poly1305 (for record encryption and authentication)
- **Entropy Source**: OS-provided Cryptographically Secure Pseudo-Random Number Generator (CSPRNG)

Algorithms must be explicitly named in protocol negotiation and transcripts to prevent algorithm confusion attacks.

## Consequences

- High performance, particularly on platforms without hardware-accelerated AES (ChaCha20-Poly1305 is extremely fast in software).
- Prevention of downgrade/algorithm confusion via explicit suite negotiation and transcript binding.
- High security guarantees (128-bit security level) with short keys (32-byte public keys/signatures).
- The implementation must rely on a mature cryptographic library, forbidding custom crypto implementations.

## Rejected Alternatives

- **AES-GCM**: Rejected as the default AEAD due to the risk of catastrophic failure under nonce reuse in software implementations and varying hardware-acceleration availability on older Android devices. ChaCha20-Poly1305 offers safer degradation.
- **NIST P-256 / ECDSA**: Rejected in favor of X25519/Ed25519 due to security vulnerabilities in implementation (e.g. biased signature nonces leaking private keys) and slower speed.
