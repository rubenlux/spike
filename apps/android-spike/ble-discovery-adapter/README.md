# Android BLE Discovery Adapter

This module is the Android-facing implementation slot for SPEC-004.

Current repository state only defines Kotlin core/contracts, so this adapter is intentionally a boundary stub. A concrete Android module should implement:

- `BleDiscoveryAdvertiser` using BLE advertising with the protocol service id and 26-byte v1 service-data payload.
- `BleDiscoveryScanner` using service-id filtering before passing observations into Discovery Core.
- `BleControlChannelFactory` using GATT or an equivalent BLE control channel.

The Discovery Core must remain free of Android BLE imports.

