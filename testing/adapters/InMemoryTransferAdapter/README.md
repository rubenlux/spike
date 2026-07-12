# InMemoryTransferAdapter

Network-free adapter for SPEC-002 tests.

Purpose:

- Deliver manifest and chunk data between two `TransferCore` instances without BLE, Wi-Fi Direct, sockets or files.
- Exercise duplicate, out-of-order and corruption scenarios deterministically.

Implementation hook lives in `core/transfer-testing/`.
