# Tasks: Hybrid Offline Link Spike

**Input**: `specs/001-hybrid-offline-link/spec.md`, `specs/001-hybrid-offline-link/plan.md`  
**Validation target**: Two physical Android smartphones, airplane mode, BLE and Wi-Fi Direct enabled manually.

## Phase 1: Setup

- [ ] T001 Create an Android project in `apps/android-spike/` with Kotlin and minimum SDK selected for target devices.
- [ ] T002 Add app-private storage directories for manifests, chunks, reconstructed objects and exported logs.
- [ ] T003 Add required Android permissions for BLE and Wi-Fi Direct, including runtime permission flow.
- [ ] T004 Wire a minimal UI for Local Node, Peer Discovery, Transfer, Transfer State and Metrics.

## Phase 2: Transfer Foundation

- [ ] T005 Implement transfer domain models in `core/transfer/`.
- [ ] T006 Implement streaming SHA-256 object hashing and manifest generation.
- [ ] T007 Implement chunk range calculation without loading the full file into memory.
- [ ] T008 Implement chunk verification and reject invalid index, length or hash.
- [ ] T009 Implement length-prefixed transfer framing with maximum frame and manifest limits.
- [ ] T010 Implement missing-chunk calculation from manifest minus verified chunks.

## Phase 3: Durable Storage

- [ ] T011 Persist manifests before accepting chunk payloads.
- [ ] T012 Persist verified chunks in app-controlled paths only.
- [ ] T013 Persist transfer state, chunk verification bitmap/list and timestamps.
- [ ] T014 Recover incomplete transfers after app process death.
- [ ] T015 Recover incomplete transfers after receiver device reboot.

## Phase 4: Observability

- [ ] T016 Implement the event taxonomy in `observability/`.
- [ ] T017 Log every transfer state transition with timestamp, device ID, peer ID, transfer ID, transport and metrics.
- [ ] T018 Export logs and metrics locally as JSONL/CSV from the Metrics surface.
- [ ] T019 Add measurement timers for BLE discovery, BLE control, Wi-Fi Direct group formation, link ready, transfer duration and resume duration.

## Phase 5: BLE Discovery and Control

- [ ] T020 Implement BLE advertisement with protocol service UUID, version, ephemeral discovery ID and capability flags.
- [ ] T021 Implement BLE scanning with protocol compatibility filtering.
- [ ] T022 Implement BLE control channel for capability exchange.
- [ ] T023 Verify two devices discover each other consistently while offline.
- [ ] T024 Measure BLE discovery latency and BLE control setup time.

## Phase 6: Wi-Fi Direct Link

- [ ] T025 Implement Wi-Fi Direct peer discovery or peer correlation from BLE negotiation.
- [ ] T026 Form a Wi-Fi Direct group and record Group Owner role.
- [ ] T027 Resolve endpoint IP and establish a socket.
- [ ] T028 Emit `WIFI_DIRECT_GROUP_FORMED` and `WIFI_DIRECT_LINK_READY`.
- [ ] T029 Detect disconnects and release Wi-Fi Direct resources.

## Phase 7: Transfer and Resume

- [ ] T030 Send HELLO, MANIFEST and MANIFEST_ACK frames over Wi-Fi Direct.
- [ ] T031 Transfer chunks with CHUNK and CHUNK_ACK frames.
- [ ] T032 Persist and verify every received chunk before ACK.
- [ ] T033 Force Wi-Fi Direct disconnect and verify `TRANSFERRING -> INTERRUPTED`.
- [ ] T034 Rediscover peer via BLE and identify incomplete transfer.
- [ ] T035 Exchange missing chunks and resume only missing chunks.
- [ ] T036 Reconstruct final object and verify object SHA-256.

## Phase 8: Large and Destructive Validation

- [ ] T037 Transfer a >= 1 GiB object under offline conditions.
- [ ] T038 Case A: manually disconnect Wi-Fi Direct and resume.
- [ ] T039 Case B: move devices out of range and resume.
- [ ] T040 Case C: close receiver app during transfer and resume.
- [ ] T041 Case D: reboot receiver device and resume.
- [ ] T042 Case E: simulate corrupt chunk and verify rejection/retransmission.
- [ ] T043 Fill `docs/spikes/SPIKE-001-METRICS.md` with measured results.
- [ ] T044 Fill `docs/spikes/SPIKE-001-RESULTS.md` with validated/invalidated conclusion.
- [ ] T045 Finalize `docs/adr/ADR-001-HYBRID-TRANSPORT-VIABILITY.md`.
