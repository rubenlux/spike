# ADR-002: Transfer Independence

**Status**: Proposed  
**Date**: 2026-07-09  
**Spec**: `specs/002-transfer-core/spec.md`

## Context

SPEC-001 explores whether Android devices can use BLE and Wi-Fi Direct for an offline hybrid link. Even if the physical link is validated, the protocol must not couple logical transfer state to any one radio, socket, Android API or connection lifecycle.

## Decision

Build the Transfer Core as a transport-independent bounded context.

The Core owns object identity, manifests, chunk verification, durable progress, missing chunk calculation, state transitions, events, recovery, reconstruction and final integrity verification.

Transport adapters are external clients of explicit contracts. They may provide manifest and chunk streams, mark transport availability and request snapshots or missing chunks. The Core must not inspect transport session references.

## Consequences

- BLE and Wi-Fi Direct can fail, be replaced or be supplemented without invalidating transfer semantics.
- The Core can be tested without Android hardware.
- Persistence and recovery become first-class protocol behavior rather than transport side effects.
- Transport adapters must respect backpressure and idempotency contracts.
- Storage implementations need explicit transaction semantics across metadata and chunk/object storage.

## Rejected Alternatives

- Coupling transfer identity to BLE/Wi-Fi Direct connection IDs.
- Treating a socket session as the transfer lifecycle.
- Inferring verified progress only from files on disk.
- Letting transport adapters mark chunks verified before durable commit.

## Follow-Up Decisions

- Final Core implementation language.
- Persistent manifest encoding.
- Bitmap/missing-set representation.
- Transaction model across metadata and filesystem.
- Cross-platform FFI strategy.
