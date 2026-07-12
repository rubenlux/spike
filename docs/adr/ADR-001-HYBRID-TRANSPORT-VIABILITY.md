# ADR-001: Hybrid Transport Viability

**Status**: Proposed - pending SPIKE-001 evidence  
**Date**: 2026-07-09  
**Spec**: `specs/001-hybrid-offline-link/spec.md`

## Context

The project needs an offline smartphone-to-smartphone link that can discover nearby compatible peers, negotiate transfer, move large objects and survive interruption without restarting from zero. The candidate architecture uses BLE for discovery/control and Wi-Fi Direct for high-volume transfer.

This ADR must not be finalized from theory. It depends on physical evidence from `docs/spikes/SPIKE-001-RESULTS.md` and metrics from `docs/spikes/SPIKE-001-METRICS.md`.

## Decision

`PENDING`

Candidate outcomes:

1. Adopt BLE + Wi-Fi Direct as the initial reference transport.
2. Keep BLE for discovery but replace or defer Wi-Fi Direct bulk transfer.
3. Use BLE-only for the initial protocol scope.
4. Investigate another offline peer-to-peer transport.
5. Stop the hybrid path because Android constraints make it unfit for the target use case.

## Evidence Required

- BLE discovery works under mandatory offline conditions.
- BLE control channel exchanges capabilities reliably.
- Wi-Fi Direct group formation succeeds reproducibly.
- Peer identity can be correlated across BLE and Wi-Fi Direct.
- >= 1 GiB transfer completes over Wi-Fi Direct.
- Verified progress survives link loss, app close and receiver reboot.
- Resume transfers only missing chunks.
- Final reconstructed object SHA-256 matches source.
- Logs and metrics are sufficient to reconstruct the experiment.

## Consequences if Adopted

- Protocol work can assume a transport orchestration layer with BLE control and Wi-Fi Direct bulk transfer.
- Transfer engine remains transport-independent and durable.
- Future work should define authentication, identity, adaptive transport selection and background behavior policy.

## Consequences if Rejected

- Do not build the protocol on unverified BLE + Wi-Fi Direct assumptions.
- Preserve reusable transfer-engine findings: chunking, manifest, persistence, framing, metrics and resume logic.
- Reassess transport strategy using the measured failure mode.

## Final Rationale

TBD after destructive validation.
