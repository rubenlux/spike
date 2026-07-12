# Secret Lifecycle and Destruction Policy

This document defines the lifecycle, storage constraints, and destruction requirements for all cryptographic secrets managed by the Secure Session Core (SPEC-005).

---

## 1. Secret Classification

The protocol distinguishes between long-term identity material and short-term session material:

| Secret Name | Type | Location | Lifetime | Action upon Termination |
|---|---|---|---|---|
| **Identity Private Key** | Long-Term | TEE / Software Store | Permanent | Retain (Managed by Identity Core) |
| **Ephemeral Private Key** | Volatile | RAM only | Handshake only | Zeroize immediately after DH |
| **DH Shared Secret** | Volatile | RAM only | Handshake only | Zeroize immediately after KDF Extract |
| **Session Master Secret** | Volatile | RAM only | Session duration | Zeroize on rekey or session closure |
| **Traffic Encryption Keys** | Volatile | RAM only | Key Epoch | Zeroize on rekey (epoch transition) or closure |
| **Nonce Bases** | Volatile | RAM only | Key Epoch | Zeroize on rekey or closure |
| **Rekey Secrets** | Volatile | RAM only | Session duration | Zeroize on session closure |
| **Confirmation Keys** | Volatile | RAM only | Handshake only | Zeroize immediately after confirmation |

---

## 2. Volatile Memory Constraints

To protect against physical device extraction and memory forensics:
1. **No Persistence**: Ephemeral private keys, DH shared secrets, master secrets, traffic keys, and rekeying secrets MUST NOT be written to disk, databases, shared preferences, or any persistent medium.
2. **No Logs**: Secrets MUST NOT be written to logcats, log files, stdout, or telemetry platforms.
3. **Minimize Exposure**: Secrets should be held in memory only as long as necessary. For instance, the ephemeral X25519 private key is destroyed immediately after computing the DH shared secret.

---

## 3. Best-Effort Zeroization (Memory Wiping)

Symmetric keys and intermediate secrets are represented in code as `ByteArray` (or platform equivalent like byte arrays in Kotlin). When their lifetime ends, the implementation MUST perform zeroization:

- **Zeroization Pattern**: Fill the backing array with zeroes (`0` bytes) before releasing the reference:
  ```kotlin
  fun ByteArray.zeroize() {
      this.fill(0)
  }
  ```

### Runtime Considerations & JVM/Kotlin Garbage Collection (GC)
Since the primary target is Kotlin on the JVM/Android:
1. **GC Limitations**: Runtimes with garbage collection do not guarantee immediate reclamation of memory. Even if a reference is cleared, the raw bytes may remain in heap memory until the garbage collector runs and overwrites the memory page.
2. **Immutable Objects**: Using immutable classes (like Java's `String` or nested wrappers) for keys is forbidden, as the JVM creates copies and keeps old values in memory that cannot be manually cleared. Always represent raw keys as mutable `ByteArray`s.
3. **Best-Effort Guarantee**: While garbage collection prevents hardware-level absolute assurances of zeroization, manually calling `fill(0)` on byte arrays before dereferencing them ensures that the active data structure is wiped, preventing references from pointing to valid keys in the heap.
