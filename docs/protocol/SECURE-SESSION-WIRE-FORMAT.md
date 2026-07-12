# Secure Session Wire Format

This document specifies the binary encoding of secure session handshake frames and secure records.

---

## 1. Handshake Frames

All handshake frames are encoded using a binary layout. Handshake frames are identified by a 4-byte magic header: `0x53534831` (ASCII: `SSH1`).

### 1.1 Handshake Types

```kotlin
enum class HandshakeFrameType {
    SESSION_HELLO,          // Type 0
    SESSION_HELLO_RESPONSE, // Type 1
    HANDSHAKE_AUTH,         // Type 2
    KEY_CONFIRMATION        // Type 3
}
```

### 1.2 Binary Layouts

#### SESSION_HELLO (Type 0)
- **Magic**: `0x53534831` (4 bytes)
- **Protocol Version**: UInt16 (2 bytes)
- **Supported Suites**: Sized string list (e.g. comma-separated, UInt16 length + UTF-8 bytes)
- **Encounter ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Handshake ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Initiator Node ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Responder Node ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Initiator Nonce**: 16 bytes
- **Initiator Ephemeral Public Key**: 32 bytes
- **Capabilities**: Sized string (UInt16 length + UTF-8 bytes containing serialized map)

#### SESSION_HELLO_RESPONSE (Type 1)
- **Magic**: `0x53534831` (4 bytes)
- **Selected Version**: UInt16 (2 bytes)
- **Selected Suite**: Sized string (UInt16 length + UTF-8 bytes)
- **Encounter ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Handshake ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Initiator Node ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Responder Node ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Initiator Nonce**: 16 bytes
- **Responder Nonce**: 16 bytes
- **Initiator Ephemeral Public Key**: 32 bytes
- **Responder Ephemeral Public Key**: 32 bytes

#### HANDSHAKE_AUTH (Type 2)
- **Magic**: `0x53534831` (4 bytes)
- **Handshake ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Signer Node ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Signer Role**: UInt8 (1 byte, 0 = INITIATOR, 1 = RESPONDER)
- **Transcript Hash**: 32 bytes
- **Identity Signature**: 64 bytes (Ed25519 signature)

#### KEY_CONFIRMATION (Type 3)
- **Magic**: `0x53534831` (4 bytes)
- **Handshake ID**: Sized string (UInt16 length + UTF-8 bytes)
- **Signer Role**: UInt8 (1 byte, 0 = INITIATOR, 1 = RESPONDER)
- **Confirmation Tag**: 32 bytes (HMAC-SHA-256)

---

## 2. Secure Records

Once the session is `ESTABLISHED`, all subsequent frames are wrapped as `SecureRecord` packets. They are prefixed by a distinct magic header: `0x53524543` (ASCII: `SREC`).

### 2.1 Wire Layout

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                      Magic (0x53524543)                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|    Version    |                  Key Epoch                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Sequence Number                         |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   RecordType  |             Session ID Sized String ...       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|             ... (UUIDv7 encoded as 36-byte UTF-8 string)      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Ciphertext Length                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                        Ciphertext Bytes                       |
|                             ...                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
|                    AEAD Authentication Tag                    |
|                           (16 bytes)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 2.2 Fields Detailed

1. **Magic**: `0x53524543` (4 bytes).
2. **Record Version**: UInt8 (1 byte). Currently `1`.
3. **Key Epoch**: UInt32 (4 bytes). Starts at `0`, increments on rekey.
4. **Sequence Number**: UInt64 (8 bytes). Monotonic record counter.
5. **Record Type**: UInt8 (1 byte). Maps to the `RecordType` enum:
   - `CONTROL` = 0
   - `TRANSFER_CONTROL` = 1
   - `TRANSPORT_UPGRADE` = 2
   - `HEARTBEAT` = 3
   - `KEY_UPDATE` = 4
   - `CLOSE` = 5
   - `ERROR` = 6
6. **Session ID**: Sized String (UInt16 length prefix + UTF-8 string UUID).
7. **Ciphertext Length**: UInt32 (4 bytes). Size of `ciphertext` without the authentication tag.
8. **Ciphertext**: Variable byte array.
9. **AEAD Authentication Tag**: 16 bytes. Output of the ChaCha20-Poly1305 AEAD primitive.

---

## 3. Associated Data (AAD) Construction

To ensure record routing fields cannot be modified by a MITM in transit, the following values are concatenated to form the AEAD Additional Authenticated Data (AAD):

```text
AAD = recordVersion (1 byte)
    || keyEpoch (4 bytes, Big-Endian)
    || sequenceNumber (8 bytes, Big-Endian)
    || recordType (1 byte)
    || sessionId (UTF-8 bytes of UUID string)
    || ciphertextLength (4 bytes, Big-Endian)
```

The ciphertext and tag are generated by calling:
```text
(ciphertext, tag) = ChaCha20-Poly1305.Encrypt(
    key = trafficKey,
    nonce = nonce,
    plaintext = plaintext,
    aad = AAD
)
```
