# BLE Advertisement Budget

Discovery advertisement v1 uses 26 bytes of service data:

| Field | Bytes |
| --- | ---: |
| discoveryVersion | 1 |
| ephemeralDiscoveryId | 16 |
| advertisementNonce | 4 |
| capabilityBitmap | 4 |
| flags | 1 |
| Total | 26 |

The protocol service id is carried by the BLE service filtering layer, not inside the 26-byte service-data payload.

This keeps the logical payload compatible with legacy advertising budgets where practical. If a device cannot fit service uuid plus service data inside the legacy 31-byte envelope, the Android adapter may use scan response or extended advertising, but Core payload semantics must not change.

