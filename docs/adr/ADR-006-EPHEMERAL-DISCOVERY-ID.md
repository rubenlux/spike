# ADR-006: Ephemeral Discovery ID

## Status

Accepted for SPEC-004 v1 scaffold.

## Decision

Use a 128-bit `EphemeralDiscoveryId` derived with HMAC-SHA-256:

```text
HMAC(rotationSecret, domainTag || epoch || localRotationNonce)
```

The rotation interval defaults to 15 minutes and is configurable.

## Consequences

Advertisements avoid stable node identity. Active encounters remain bound to authenticated `NodeId`, so rotation does not close an encounter.

