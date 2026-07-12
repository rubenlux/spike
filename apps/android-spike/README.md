# Android Spike App

Experimental Android application for SPEC-001.

This directory is intentionally a scaffold, not a finished Android project. It should become the physical validation app that wires:

- `core/transfer/` for manifest, chunking, verification and resume logic.
- `transports/ble/` for discovery and control.
- `transports/wifi-direct/` for bulk socket transfer.
- `storage/` for durable manifests, chunks, transfer state and metrics.
- `observability/` for structured event logs and export.

## Required Surfaces

- Local Node: experimental node ID, BLE state, Wi-Fi Direct state, storage available.
- Peer Discovery: compatible peers discovered through BLE.
- Transfer: select file, select peer, start transfer, force disconnect, resume transfer.
- Transfer State: transfer ID, object ID, state, transport, total bytes, verified bytes, verified chunks, missing chunks, throughput, elapsed time.
- Metrics: local export of events and metric tables.

## Android Permissions to Implement

- BLE advertise/scan/connect runtime permission flow for Android 12+.
- Wi-Fi Direct nearby devices runtime permission flow for Android 13+.
- Backward-compatible location permission behavior for older target devices when required.

See `docs/spikes/SPIKE-001-ANDROID-CONSTRAINTS.md` before wiring the manifest.
