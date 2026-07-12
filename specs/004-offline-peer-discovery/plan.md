# Implementation Plan: Offline Peer Discovery & Encounter Protocol

**Branch**: `004-offline-peer-discovery`  
**Date**: 2026-07-09  
**Spec**: `specs/004-offline-peer-discovery/spec.md`  
**Status**: Draft planning scaffold

## Summary

Build a BLE-independent Discovery Core and Encounter model where nearby nodes advertise only protocol-compatible ephemeral IDs, create unauthenticated candidates, establish a control channel through transport contracts, validate identity documents through Identity Core, perform mutual Proof of Possession, create authenticated peers and persist logical encounters.

## Technical Context

**Language/Version**: Kotlin scaffold with multiplatform-friendly boundaries.  
**Primary Dependencies**: Identity Core contracts for identity validation and proof operations; no BLE dependency in Core.  
**Storage**: Discovery candidate, encounter and event repository contracts to be added in implementation.  
**Testing**: Unit/property/integration tests with fake discovery transport and fake control channel; Android BLE hardware tests later.  
**Target Platform**: Android initial adapter, Core platform-independent.  
**Constraints**: No `NodeId`/public key in advertisements; mutual auth required; one active encounter per remote node.

## Project Structure

```text
specs/004-offline-peer-discovery/
├── spec.md
├── plan.md
└── tasks.md

core/
├── discovery-domain/
├── discovery-contracts/
├── discovery-application/
├── discovery-testing/
├── encounter-domain/
└── encounter-application/

transports/
├── ble-discovery-contracts/
└── ble-control-contracts/

apps/android-spike/ble-discovery-adapter/
```

## Increment Plan

1. Discovery value objects, advertisement model and validation.
2. Ephemeral ID derivation and rotation policy.
3. Candidate state machine and deduplication.
4. Control channel frame contracts and sequence/session validation.
5. Identity exchange integration boundary.
6. Mutual Proof of Possession orchestration boundary.
7. Authenticated peer and encounter model.
8. Encounter state machine, duplicate policy and contact loss.
9. Recovery after process death.
10. Fake transport/control-channel integration tests.
11. Android BLE discovery/control adapter.
12. Mandatory scenarios A-L.

## Key Risks

- BLE payload budget may force tighter advertisement encoding.
- Capability bitmap may fingerprint nodes if it grows too expressive.
- Simultaneous GATT/client-server connection attempts need deterministic convergence.
- Ephemeral rotation can confuse pre-auth candidates if timeout/dedup windows are poorly chosen.
- Stable `NodeId` is revealed after identity exchange, so privacy after control-channel establishment is limited in v1.
