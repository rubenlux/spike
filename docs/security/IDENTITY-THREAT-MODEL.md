# Identity Threat Model

**Status**: Draft  
**Spec**: `specs/003-node-identity/spec.md`

## Assets

- Identity private signing key.
- Private key handle and secure storage binding.
- Public identity document.
- `NodeId` and `KeyId`.
- Challenge store state.
- Identity event log.

## Non-Goals

- Sybil resistance.
- Trust or authorization policy.
- Anonymous discovery.
- Distributed revocation.
- Cloud backup or recovery.
- Secure session encryption.
- Multi-device continuity.

## Threats and Required Controls

| Threat | Required Control |
| --- | --- |
| Transport identifier substitution | `NodeId` never derives from BLE, Wi-Fi Direct, IP or device names |
| Public key substitution | Validate `NodeId + PublicKey` and `KeyId + PublicKey` bindings |
| Modified identity document | Verify self-signature over canonical unsigned document |
| Signature replay across domains | Domain separation tags |
| Proof replay | Single-use locally issued challenges consumed atomically |
| Expired proof reuse | Challenge TTL and expiry checks |
| Wrong operation context | Context bound into proof signature |
| Missing private key | `KEY_UNAVAILABLE`, no silent replacement |
| Compromised identity | Compromised state blocks signing/proofs |
| Private key disclosure through API | Opaque handle only; no normal private key export |
| Ambiguous signed bytes | Canonical binary encoding and strict decoder |

## Explicit Limitations

Self-certifying identity proves control of a private key. It does not prove human identity, safety, reputation, authorization or uniqueness. A malicious actor can create many valid identities.

## Review Checklist

- [ ] No normal API returns private key bytes.
- [ ] No identity derivation uses Android or transport identifiers.
- [ ] All signed payloads include domain tags.
- [ ] Challenges are random, bounded, persisted and consumed.
- [ ] Startup validation detects key handle loss.
- [ ] Remote identity persistence is not labeled trust.
