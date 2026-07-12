# Implementation Plan: Transport-Independent Transfer Core

**Branch**: `002-transfer-core`  
**Date**: 2026-07-09  
**Spec**: `specs/002-transfer-core/spec.md`  
**Status**: Draft planning scaffold

## Summary

Build a deterministic, durable Transfer Core that can prepare objects, generate manifests, accept and verify chunks from any transport, persist progress, recover after process death, calculate missing chunks, reconstruct objects and verify final integrity without Android, BLE, Wi-Fi Direct, sockets or Internet.

## Technical Context

**Language/Version**: Kotlin scaffold, with multiplatform-friendly domain boundaries where practical.  
**Primary Dependencies**: Standard language/runtime only at domain and contract layers.  
**Storage**: Abstract contracts first; concrete storage implementation deferred.  
**Testing**: Unit, property-style, integration and fault injection tests with in-memory, loopback and file-like adapters.  
**Target Platform**: Initial validation on Android, but Core has no Android dependency.  
**Project Type**: Core library modules plus test adapters.  
**Performance Goals**: Bounded memory for >= 1 GiB objects; no full-object memory load.  
**Constraints**: No networking dependencies in Core; durable event per state transition; mutations serialized per transfer.  
**Scale/Scope**: Single logical transfer core, no routing/multi-hop/identity/transport selection.

## Project Structure

```text
specs/002-transfer-core/
├── spec.md
├── plan.md
└── tasks.md

core/
├── transfer-domain/
├── transfer-contracts/
├── transfer-application/
└── transfer-testing/

storage/
└── transfer-storage-contracts/

testing/adapters/
├── InMemoryTransferAdapter/
├── LoopbackTransferAdapter/
└── FileTransferAdapter/

docs/
├── architecture/TRANSFER-CORE.md
├── testing/TRANSFER-CORE-FAULT-MODEL.md
└── adr/ADR-002-TRANSFER-INDEPENDENCE.md
```

## Increment Plan

1. Domain value objects, errors and state machine.
2. Manifest model and validation.
3. Missing chunk abstraction and calculator.
4. Public transfer contracts and bounded stream handles.
5. Storage contracts and transaction boundary.
6. Object preparation streaming engine.
7. Chunk verification pipeline with durable commit semantics.
8. Transfer lifecycle service with ordered durable events.
9. Object reconstruction and final hash verification.
10. Recovery service and fault injection tests.
11. In-memory, loopback and file-like adapters.
12. Mandatory acceptance scenarios A-G.

## Complexity Notes

The Core is split into several modules to protect the central architectural rule: transfer logic must not depend on transports or platform storage. This is intentional complexity because it prevents Android/BLE/Wi-Fi Direct assumptions from becoming protocol semantics.
