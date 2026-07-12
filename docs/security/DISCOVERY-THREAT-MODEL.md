# Discovery Threat Model

Primary threats:

- passive tracking via stable advertised identifiers;
- malformed advertisement parsing;
- incompatible protocol spoofing;
- identity document tampering;
- unilateral authentication;
- replayed proofs or frames;
- stale physical channels after process death;
- duplicate simultaneous control channels.

Controls:

- no stable identity in advertisements;
- strict payload length and version checks;
- required capability bitmap checks;
- identity validation through Identity Core;
- session nonces and monotonic sequence numbers for control frames;
- explicit state machines for candidates and encounters;
- recovery that drops physical-channel assumptions.

Trust remains out of scope. A cryptographically valid identity is authenticated, not trusted.

