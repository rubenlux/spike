# Implementation Plan: Hybrid Offline Link Spike

**Branch**: `001-hybrid-offline-link`  
**Date**: 2026-07-09  
**Spec**: `specs/001-hybrid-offline-link/spec.md`  
**Status**: Draft planning scaffold

## Summary

Build an Android-only proof of feasibility where two physical phones, fully offline, use BLE for discovery/control and Wi-Fi Direct sockets for resumable chunked file transfer. The spike succeeds only after physical validation with a >= 1 GiB object, deliberate interruption, durable progress, redetection, resume from missing chunks and final SHA-256 verification.

## Technical Context

**Language/Version**: Kotlin targeting Android.  
**Primary Dependencies**: Android framework BLE APIs, Android Wi-Fi P2P APIs, Java/Kotlin standard crypto and IO. No cloud/service dependency.  
**Storage**: App-private storage for chunks plus a durable metadata store. Candidate: Room/SQLite for manifests, transfer state, chunk bitmap and metrics.  
**Testing**: JVM tests for transfer/framing where possible; Android instrumented tests and manual two-device validation for radios.  
**Target Platform**: Android smartphones with BLE peripheral/central support and Wi-Fi Direct.  
**Project Type**: Mobile app + local protocol modules.  
**Performance Goals**: Transfer >= 1 GiB, no full-file memory load, observable throughput and resume metrics.  
**Constraints**: 100% offline; no remote coordination; physical device validation required; Android permission and foreground/background behavior must be measured.  
**Scale/Scope**: Two nodes only; no multi-hop, no chat, no production identity/security.

## Constitution Check

The workspace constitution is still a placeholder. For this spike, the controlling rules are the non-negotiables in `spec.md`: offline absolute, physical-device evidence, separated transport responsibilities, durable transfer state, no restart from zero and verification before trust.

## Project Structure

### Documentation

```text
specs/001-hybrid-offline-link/
├── spec.md
├── plan.md
└── tasks.md

docs/
├── adr/ADR-001-HYBRID-TRANSPORT-VIABILITY.md
└── spikes/
    ├── SPIKE-001-ANDROID-CONSTRAINTS.md
    ├── SPIKE-001-METRICS.md
    └── SPIKE-001-RESULTS.md
```

### Source Code

```text
apps/android-spike/
  Experimental app shell, permissions, minimal UI and orchestration wiring.

core/transfer/
  Transport-independent model, manifest, chunk verification and framing.

transports/ble/
  BLE advertisement, scan, GATT/control channel and capability exchange.

transports/wifi-direct/
  Wi-Fi Direct group formation, endpoint discovery and socket link.

storage/
  Durable transfer metadata, chunk files and metrics export.

observability/
  Event taxonomy, metric names and local log writer contract.
```

**Structure Decision**: Keep transfer logic independent from Android transport adapters. Android-specific BLE/Wi-Fi code remains behind adapters so failed transport assumptions do not contaminate the transfer engine.

## Increment Plan

1. BLE discovery only.
2. BLE capability/control channel.
3. Wi-Fi Direct group and socket.
4. Raw small-file streaming.
5. Chunked transfer with per-chunk SHA-256.
6. Durable receiver metadata and chunk files.
7. Link interruption detection.
8. Resume from missing chunks.
9. >= 1 GiB object validation.
10. Destructive validation cases A-E.

## Key Risks

- Android version and device-vendor differences in BLE advertising/scanning behavior.
- Wi-Fi Direct peer correlation after BLE discovery may be unreliable without an explicit handshake.
- Wi-Fi Direct group formation may require user-visible prompts or fail inconsistently across devices.
- Background restrictions may make opportunistic encounters unreliable.
- Large manifests with per-chunk hashes may need bounded size and streaming parse.
