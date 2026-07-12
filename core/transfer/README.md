# Transfer Core

Transport-independent transfer engine for SPEC-001.

SPEC-002 formalizes this preliminary sketch into separate bounded-context modules:

- `core/transfer-domain/`
- `core/transfer-contracts/`
- `core/transfer-application/`
- `core/transfer-testing/`
- `storage/transfer-storage-contracts/`

Responsibilities:

- Generate object manifests with SHA-256 object and chunk hashes.
- Stream files in bounded chunks; never load the full file into memory.
- Verify every chunk before marking it available.
- Calculate missing chunks from durable progress.
- Encode/decode length-prefixed frames for Wi-Fi Direct socket transfer.
- Keep logical transfer state independent from BLE or Wi-Fi Direct sessions.

Non-responsibilities:

- BLE discovery.
- Wi-Fi Direct group formation.
- Android permission UI.
- Production identity or end-to-end encryption.
