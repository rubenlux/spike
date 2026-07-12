# SPIKE-001 Results - Hybrid Offline Link

**Status**: Not executed  
**Spec**: `specs/001-hybrid-offline-link/spec.md`  
**Last updated**: 2026-07-09

## Executive Result

`PENDING`

The spike has not yet been physically validated on two Android devices. This document is the evidence log to complete during execution. Do not mark `VALIDATED` or `INVALIDATED` until the mandatory offline two-device scenario has been completed or blocked by observed platform constraints.

## Required Test Bed

| Field | Device A | Device B |
| --- | --- | --- |
| Role | Sender | Receiver |
| Model | TBD | TBD |
| Android version/API | TBD | TBD |
| BLE available | TBD | TBD |
| BLE advertise supported | TBD | TBD |
| BLE scan supported | TBD | TBD |
| Wi-Fi Direct supported | TBD | TBD |
| Storage free before test | TBD | TBD |

## Offline Preflight

- [ ] Airplane mode enabled on both devices.
- [ ] Mobile data disabled.
- [ ] External Wi-Fi disconnected.
- [ ] No router, hotspot, server or Internet path available.
- [ ] BLE manually enabled.
- [ ] Wi-Fi/Wi-Fi Direct manually enabled if required by Android/device.
- [ ] App permissions granted through Android runtime dialogs.

## Scenario Evidence

| Step | Expected Evidence | Observed | Pass/Fail |
| --- | --- | --- | --- |
| BLE advertisement | Receiver logs `BLE_PEER_DISCOVERED` | TBD | TBD |
| BLE control | Both logs `CAPABILITIES_EXCHANGED` | TBD | TBD |
| Upgrade request | Both logs `WIFI_DIRECT_UPGRADE_REQUESTED` | TBD | TBD |
| Group formation | `WIFI_DIRECT_GROUP_FORMED` with role/IP data | TBD | TBD |
| Socket ready | `WIFI_DIRECT_LINK_READY` | TBD | TBD |
| Manifest | Receiver persists manifest before chunks | TBD | TBD |
| Chunk transfer | Chunks received, persisted, verified, ACKed | TBD | TBD |
| Interruption | `LINK_LOST` and `TRANSFER_INTERRUPTED` | TBD | TBD |
| Rediscovery | `PEER_REDISCOVERED` | TBD | TBD |
| Missing chunks | `MISSING_CHUNKS_CALCULATED` | TBD | TBD |
| Resume | Only missing chunks sent | TBD | TBD |
| Completion | `OBJECT_HASH_VERIFIED` and `TRANSFER_COMPLETED` | TBD | TBD |

## Destructive Cases

| Case | Method | Expected Result | Observed | Pass/Fail |
| --- | --- | --- | --- | --- |
| A - Wi-Fi Direct disconnect | Manually break P2P link | Progress persists; resume missing chunks | TBD | TBD |
| B - Out of range | Separate devices | Loss detected; later recovery | TBD | TBD |
| C - Receiver app close | Force close app mid-transfer | Verified chunks survive app restart | TBD | TBD |
| D - Receiver reboot | Reboot Android mid-transfer | Manifest/progress survives reboot | TBD | TBD |
| E - Corrupt chunk | Inject or modify chunk payload | Hash fails; chunk rejected; retransmitted | TBD | TBD |

## Decision Input

Complete these before writing the ADR:

- BLE discovery reliability: TBD.
- BLE-to-Wi-Fi Direct peer correlation reliability: TBD.
- Wi-Fi Direct group formation success rate: TBD.
- Upgrade latency distribution: TBD.
- Effective throughput for >= 1 GiB object: TBD.
- Duplicate chunks after resume: TBD.
- Android permissions/background constraints observed: TBD.
- Recommendation: TBD.
