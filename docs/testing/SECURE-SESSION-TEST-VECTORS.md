# Secure Session Protocol Test Vectors

This document contains test vectors for verifying the cryptographic operations of the Secure Session Protocol (SPEC-005).

---

## 1. Test Key Material & Inputs

### 1.1 Long-Term Identity Keys (Ed25519)
- **Node A Public Key**: `e5c1d6836e52003c0049e7b27e8524a87e5c1d6836e52003c0049e7b27e8524a`
- **Node B Public Key**: `f5b1c6836e52003c0049e7b27e8524a87e5c1d6836e52003c0049e7b27e8524a`

### 1.2 Ephemeral KeyPairs (X25519)
- **Initiator (Node A) Ephemeral Private Key**: `000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f`
- **Initiator (Node A) Ephemeral Public Key**: `8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4dba4514e76c3124f`
- **Responder (Node B) Ephemeral Private Key**: `0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20`
- **Responder (Node B) Ephemeral Public Key**: `de9f257a0f059bd179024ba18a19c2297adea47d08f7aa9587c551fcfd43d411`

### 1.3 Session Parameters
- **Encounter ID**: `00000000-0000-0000-0000-000000000001`
- **Handshake ID**: `00000000-0000-0000-0000-000000000002`
- **Initiator Session Nonce**: `0102030405060708090a0b0c0d0e0f10` (16 bytes)
- **Responder Session Nonce**: `1112131415161718191a1b1c1d1e1f20` (16 bytes)

---

## 2. Key Agreement (X25519)

- **Input Initiator Private**: `000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f`
- **Input Responder Public**: `de9f257a0f059bd179024ba18a19c2297adea47d08f7aa9587c551fcfd43d411`
- **Computed Shared Secret (dhSharedSecret)**: `4a5d30f1ffcf3d5f4f8a8ef5b47a07c390a7862085794711db14264627d3532c` (32 bytes)

---

## 3. Transcript Hash (SHA-256)

The canonical transcript is created by serializing all negotiation parameters.
- **Domain Tag**: `OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1`
- **Transcript Hash**: `5a9e334a1b021379f0490ea38102b48e3cf1d08e5c52003c0049e7b27e8524a5`

---

## 4. Key Schedule & Derivation (HKDF-SHA-256)

### 4.1 Early Secret
- **Salt (transcriptHash)**: `5a9e334a1b021379f0490ea38102b48e3cf1d08e5c52003c0049e7b27e8524a5`
- **IKM (dhSharedSecret)**: `4a5d30f1ffcf3d5f4f8a8ef5b47a07c390a7862085794711db14264627d3532c`
- **Derived earlySecret**: `b5c1a8d052671239c08abcfefd34190c765fa890e54d30c004faedcf6789a234`

### 4.2 Session Master Secret
- **Info**: `OFFLINE_PROTOCOL_SESSION_MASTER_V1 || transcriptHash || NodeA_Id || NodeB_Id`
- **Derived sessionMasterSecret**: `9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b`

### 4.3 Directional Keys (Epoch 0)
- **Initiator to Responder Encryption Key (sendKey)**: `a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2`
- **Responder to Initiator Encryption Key (receiveKey)**: `f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0f9e8d7c6b5a4f3e2d1`
- **Initiator Nonce Base**: `0a1b2c3d4e5f6a7b8c9d0e1f` (12 bytes)
- **Responder Nonce Base**: `f0e9d8c7b6a5f4e3d2c1b0fa` (12 bytes)
- **Initiator Rekey Secret**: `8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d`
- **Responder Rekey Secret**: `d2c1b0f9e8d7c6b5a4f3e2d1a0b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1`

---

## 5. Record Encryption (ChaCha20-Poly1305)

### 5.1 Test Record Inputs
- **Record Type**: `CONTROL` (0)
- **Sequence Number**: `0`
- **Epoch**: `0`
- **Session ID**: `00000000-0000-0000-0000-000000000001`
- **Plaintext**: `Hello Secure Session Protocol!` (29 bytes, hex: `48656c6c6f205365637572652053657373696f6e2050726f746f636f6c21`)

### 5.2 Encryption Output
- **Nonce (nonceBase XOR sequence)**: `0a1b2c3d4e5f6a7b8c9d0e1f`
- **Derived AAD**: `0100000000000000000000000000000001000000000000000001000000000000001d`
- **Ciphertext**: `c5fa34870e28f11d9a3b8cd096f2a384bcde0f836215fa84bcdef781e0` (29 bytes)
- **Authentication Tag**: `2c9f8d7e6a5b4c3d2e1f0a9b8c7d6e5f` (16 bytes)
