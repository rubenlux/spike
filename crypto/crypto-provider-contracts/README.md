# Crypto Provider Contracts

Provider boundary for SPEC-003.

Implementations must use mature cryptographic libraries or platform facilities. Do not implement Ed25519 or CSPRNG primitives manually.

The public API exposes opaque private key handles, never raw private key bytes.
