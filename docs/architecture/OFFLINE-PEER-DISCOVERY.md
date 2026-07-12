# Offline Peer Discovery

SPEC-004 separates discovery, identification, authentication and encounter creation.

The discovery advertisement reveals only:

- protocol service id;
- discovery version;
- 128-bit ephemeral discovery id;
- advertisement nonce;
- minimal capability bitmap;
- flags.

It never carries `NodeId`, public key bytes, identity documents, device names, object ids or transfer metadata.

Core flow:

1. `EphemeralIdService` derives a rotating id from a protected rotation secret, monotonic epoch and local rotation nonce.
2. `AdvertisementService` encodes the v1 payload and validates incoming payloads fail-closed.
3. `CandidateLifecycleService` creates or updates unauthenticated `DiscoveryCandidate` records.
4. `ControlChannel` contracts carry negotiation, identity and proof frames without exposing BLE APIs to Core.
5. Identity validation is delegated to Identity Core.
6. Mutual proof evidence is required before creating an `AuthenticatedPeer`.
7. Encounter lifecycle is handled separately from discovery candidates.

The Core is intentionally transport-independent. BLE is one adapter, not a domain dependency.

