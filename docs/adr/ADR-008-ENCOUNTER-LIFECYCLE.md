# ADR-008: Encounter Lifecycle

## Status

Accepted for SPEC-004 v1 scaffold.

## Decision

Create encounters only after mutual authentication. Use explicit states:

```text
CREATING, ACTIVE, DEGRADED, CONTACT_LOST, CLOSED, FAILED
```

Recovery assumes physical channels are destroyed and marks active encounters `CONTACT_LOST`.

## Consequences

Encounter history survives process death without inventing a false live channel. Rediscovery after closure creates a new encounter id.

