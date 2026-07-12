# Secure Session Threat Model

This document outlines the threat model for the Secure Session Protocol (SPEC-005) and documents the cryptographic mitigations implemented in the design.

---

## 1. Adversary Capabilities

The adversary is assumed to have full control of the physical transport medium (BLE, Wi-Fi Direct, etc.) and can perform active and passive attacks, including:
- **Eavesdropping**: Capturing all transmitted packets.
- **Injection**: Injecting arbitrary, malformed, or replayed packets.
- **Modification**: Altering packet contents in transit.
- **Reordering & Delay**: Delaying, dropping, or reordering packets.
- **Connection Manipulation**: Opening multiple connections or forcing reconnects.
- **Process Termination**: Triggering process death on devices.

---

## 2. Threat Analysis & Mitigations

### 2.1 Eavesdropping & Lack of Confidentiality
- **Threat**: An eavesdropper captures plaintext protocol records (e.g. transfer requests, command frames).
- **Mitigation**: All post-handshake traffic is encrypted using **ChaCha20-Poly1305** under keys derived dynamically via Ephemeral Diffie-Hellman (X25519) and HKDF-SHA-256. Plaintext is never exposed on the wire.

### 2.2 Man-in-the-Middle (MITM) & Ephemeral Key Modification
- **Threat**: An active attacker replaces the ephemeral keys sent in `SESSION_HELLO` and `SESSION_HELLO_RESPONSE` with their own, establishing independent sessions with both peers.
- **Mitigation**: **Mutual Authentication** via Ed25519 identity signatures. The signature covers the `transcriptHash`, which binds the ephemeral public keys of both parties. Any replacement of the ephemeral public keys changes the transcript hash, causing signature verification to fail.

### 2.3 Unknown Key-Share (UKS) Attack
- **Threat**: Node A believes it is sharing a session key with Node B, but Node B believes it is sharing the key with Node C (an attacker). Node C can then trick Node B into accepting files or commands on behalf of Node A.
- **Mitigation**: **Explicit Identity Binding**. The handshake transcript and the signature payload explicitly include both the local and remote `NodeId`s. The signature proves that the signer intended to establish a session with the specific peer identified by their NodeId.

### 2.4 Reflection Attack
- **Threat**: An attacker intercepts a frame sent by Node A (the initiator) and reflects it back to Node A. Node A parses it as if it was sent by the responder.
- **Mitigation**:
  1. **Directional Keys**: Encryption and decryption keys are different for each direction ($A \rightarrow B$ uses a different key than $B \rightarrow A$).
  2. **Role Binding**: The transcript and the signed authentication payload include the explicit role (`INITIATOR` vs `RESPONDER`) of the node.
  3. **Directional Nonces**: Each direction derives a distinct `nonceBase`.

### 2.5 Replay Attacks
- **Threat**: An attacker captures a valid encrypted frame from a past session (or earlier in the same session) and injects it again.
- **Mitigation**:
  - **Inter-Session Replays**: Handshakes generate fresh random 128-bit `sessionNonce` values for both parties, resulting in different session keys for every connection. Old record ciphertexts cannot be decrypted under new keys.
  - **Intra-Session Replays**: Records contain a monotonic `sequenceNumber`. The receiver rejects any frame with a sequence number less than or equal to the expected sequence.

### 2.6 Out-of-Order & Truncation Attacks
- **Threat**: An attacker drops a record (e.g. "cancel transfer") or shifts packet order.
- **Mitigation**: The receiver enforces strict sequential ordering. Any frame that has a sequence number greater than expected triggers a session failure (`OutOfOrderRecord`).

### 2.7 Key Wear-out & Nonce Reuse
- **Threat**: Encryption of huge amounts of data under a single key allows analytical attacks, or sequence counters overflow, leading to nonce reuse.
- **Mitigation**: In-band symmetric rekeying is triggered automatically when record thresholds ($2^{32}$ frames), byte thresholds (1 GB), or session duration (1 hour) are met, rotating keys and resetting sequence counters.

### 2.8 Process Death & Key Leakage
- **Threat**: A device process dies or is killed. If session secrets are persisted to storage, a forensic attacker might extract them.
- **Mitigation**: Ephemeral private keys, DH shared secrets, session master secrets, traffic keys, and rekeying secrets are kept **strictly in volatile memory**. They are never persisted. After process death, a new handshake MUST be run.
