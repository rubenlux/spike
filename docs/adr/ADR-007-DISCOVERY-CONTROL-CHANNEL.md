# ADR-007: Discovery Control Channel

## Status

Accepted for SPEC-004 v1 scaffold.

## Decision

Discovery Core depends on a `ControlChannel` contract with explicit frames, session nonce and monotonic sequence numbers. BLE/GATT details live in adapters.

## Consequences

The same discovery/authentication flow can be tested with fake transports and later implemented over Android BLE without importing Android APIs into Core.

