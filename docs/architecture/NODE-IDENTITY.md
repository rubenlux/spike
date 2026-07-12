# Node Identity Architecture

**Status**: Draft  
**Spec**: `specs/003-node-identity/spec.md`

## Core Boundary

Identity Core owns cryptographic node identity only. It does not discover peers, open transports, authorize users, route messages, define trust policy or provide Sybil resistance.

Transport adapters may carry identity documents and proofs, but they do not define identity.

## Module Layout

```text
core/identity-domain/
  Value objects, identity documents, lifecycle states, errors, derivation.

core/identity-contracts/
  Public IdentityCore API and verification/proof result contracts.

core/identity-application/
  Application service orchestration over crypto and storage providers.

core/identity-testing/
  Fake providers, fixtures and deterministic vectors.

crypto/crypto-provider-contracts/
  Ed25519, SHA-256, CSPRNG, private key handle and canonical encoder contracts.

storage/identity-storage-contracts/
  Local identity, remote identity, challenge and event repositories.
```

## Dependency Rule

```text
identity-domain
  has no Android, transport, network or storage implementation dependency

identity-contracts
  depends on identity-domain abstractions

identity-application
  depends on identity-domain, identity-contracts, crypto contracts and storage contracts

crypto/storage implementations
  depend inward on contracts

transport adapters
  consume IdentityCore API only
```

Forbidden inside Identity Core:

- BLE/Wi-Fi Direct APIs.
- socket, IP, HTTP or network APIs.
- Android device identifiers as identity source.
- direct private key byte export in public APIs.

## Durable Invariants

- `NodeId` is derived only from canonical public key bytes and explicit algorithm/version.
- `KeyId` identifies the key and is distinct from `NodeId`.
- Identity document is self-signed over canonical unsigned bytes with domain separation.
- Proof of Possession consumes a locally issued challenge atomically.
- Valid identity means cryptographically valid, not trusted or authorized.
- Missing private key never causes silent identity replacement.

## Primary Flow

1. Generate Ed25519 identity keypair through crypto provider.
2. Persist private key through secure storage provider and keep only an opaque handle in domain records.
3. Canonicalize public key.
4. Derive `KeyId` and `NodeId`.
5. Create unsigned identity document.
6. Self-sign with identity key using identity-document domain tag.
7. Validate generated document.
8. Persist metadata and activate.
9. On startup, resolve key handle and repeat validation/self-test.

## Open Implementation Decisions

- Deterministic CBOR vs another canonical binary encoding.
- Android Keystore/StrongBox support matrix for Ed25519.
- Future root identity vs operational subkeys.
- Key rotation and continuity between identities.
- Future privacy strategy for stable `NodeId` during discovery.
