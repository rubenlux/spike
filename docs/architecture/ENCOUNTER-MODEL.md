# Encounter Model

An `Encounter` represents a physical meeting between two mutually authenticated nodes.

An encounter requires:

- local node id;
- remote node id from a validated identity document;
- candidate id;
- authentication session id;
- discovery transport type;
- lifecycle timestamps.

States:

```text
CREATING -> ACTIVE -> DEGRADED -> CONTACT_LOST -> CLOSED
                  \-> CLOSED
                  \-> FAILED
```

Only one active logical encounter should exist per remote node. Duplicate active encounters converge deterministically by keeping the earliest `startedAt`, then lexicographically smallest `encounterId`.

Ephemeral discovery id rotation does not affect an active encounter because the encounter is bound to the authenticated `remoteNodeId`, not the advertisement id.

Process recovery must assume physical channels are gone. Active encounters are marked `CONTACT_LOST`; history is preserved.

