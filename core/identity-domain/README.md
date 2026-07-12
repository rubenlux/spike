# Identity Domain

Pure identity bounded context for SPEC-003.

This module must not import Android, BLE, Wi-Fi Direct, socket, HTTP or network APIs.

Responsibilities:

- Node identity value objects.
- Identity documents and proofs.
- Lifecycle states.
- Typed errors.
- Deterministic `KeyId` and `NodeId` derivation helpers.
