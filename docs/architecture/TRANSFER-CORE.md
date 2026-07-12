# Transfer Core Architecture

**Status**: Draft  
**Spec**: `specs/002-transfer-core/spec.md`

## Core Boundary

The Transfer Core owns logical transfer state only. It does not discover peers, open sockets, manage BLE, manage Wi-Fi Direct or inspect transport sessions.

Transport-facing code may deliver manifests and chunk streams into the Core. The Core returns snapshots, missing chunk sets, read handles and durable events.

## Module Layout

```text
core/transfer-domain/
  Value objects, entities, state machine, errors, manifest validation.

core/transfer-contracts/
  Public commands, snapshots, chunk stream handles, transport-facing API.

core/transfer-application/
  TransferCore service orchestration and domain service boundaries.

core/transfer-testing/
  Test fixtures and fake adapters for no-network validation.

storage/transfer-storage-contracts/
  Metadata, chunk, object and transaction storage contracts.
```

## Dependency Rule

```text
transfer-domain
  depends on nothing platform-specific

transfer-contracts
  depends on transfer-domain abstractions

transfer-application
  depends on transfer-domain, transfer-contracts and storage contracts

storage implementations
  depend inward on contracts, never the reverse

transport adapters
  depend on contracts/application, never the reverse
```

Forbidden imports inside Core:

- Android Bluetooth APIs
- Android Wi-Fi P2P APIs
- BLE or Wi-Fi Direct APIs
- TCP/UDP/QUIC/HTTP/WebSocket APIs
- sockets and network interfaces
- Android Context

## Durable Invariants

- A chunk is not ACK-eligible until durable commit and verified registration succeed.
- Every state transition has a durable event in the same conceptual transaction.
- Missing chunks are derived from manifest indexes minus verified indexes.
- Final object exposure requires reconstructed object SHA-256 to match manifest object hash.
- Recovery never auto-trusts stray files that lack verified metadata.

## Primary Flow

1. Prepare outbound object by streaming source bytes.
2. Persist `TransferObject` and `ObjectManifest`.
3. Create transfer with opaque local/remote node IDs.
4. Activate transfer when an external transport is available.
5. Accept chunks in any order.
6. Verify and persist chunks idempotently.
7. Calculate missing chunks.
8. Recover after interruption or process death.
9. Reconstruct object from verified chunks.
10. Verify final hash and complete.

## Open Implementation Decisions

- Final language and build system.
- Manifest binary format.
- Bitmap representation for large missing sets.
- Metadata/filesystem transaction strategy.
- UUIDv7 implementation or alternative opaque ID generator.
- Garbage collection policy for quarantined chunk files.
