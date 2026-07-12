# Discovery Protocol Test Vectors

These vectors document the current v1 binary formats.

## Advertisement v1

Input:

```text
version = 01
ephemeralDiscoveryId = 000102030405060708090a0b0c0d0e0f
advertisementNonce = aabbccdd
capabilityBitmap = 0000000f
flags = 00
```

Encoded payload:

```text
01000102030405060708090a0b0c0d0e0faabbccdd0000000f00
```

Privacy assertions:

- no `node:v`;
- no `sha256:`;
- no public key bytes;
- no object or transfer metadata.

## Control Frame Envelope

Header:

```text
protocolVersion: 1 byte
frameType: 1 byte
sessionNonce: 16 bytes
sequenceNumber: 8 bytes big-endian
payloadLength: 4 bytes big-endian
payload: payloadLength bytes
```

Decoders must reject unsupported versions, unknown frame types, negative or mismatched payload lengths, oversized frames, nonce mismatch and non-monotonic sequence numbers.

