# LoopbackTransferAdapter

Loopback adapter for SPEC-002 mandatory acceptance scenario A and B.

It should read chunks from an outbound `TransferCore.openChunk()` handle and immediately feed them into the inbound `TransferCore.acceptChunk()` method.

It must support deliberate interruption and resume from `getMissingChunks()`.
