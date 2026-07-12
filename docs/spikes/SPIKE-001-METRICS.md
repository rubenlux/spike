# SPIKE-001 Metrics

**Status**: Template  
**Spec**: `specs/001-hybrid-offline-link/spec.md`  
**Last updated**: 2026-07-09

## Run Metadata

| Field | Value |
| --- | --- |
| Run ID | TBD |
| Date/time | TBD |
| Device A | TBD |
| Device B | TBD |
| Android versions | TBD |
| Object size | TBD |
| Chunk size | TBD |
| Chunk count | TBD |
| Object SHA-256 | TBD |
| Offline preflight passed | TBD |

## Discovery Metrics

| Metric | Value | Notes |
| --- | --- | --- |
| BLE discovery latency p50 | TBD | TBD |
| BLE discovery latency p95 | TBD | TBD |
| BLE discovery failures / attempts | TBD | TBD |
| BLE control setup time p50 | TBD | TBD |
| BLE control setup time p95 | TBD | TBD |

## Wi-Fi Direct Metrics

| Metric | Value | Notes |
| --- | --- | --- |
| Group formation attempts | TBD | TBD |
| Group formation successes | TBD | TBD |
| Connection success rate | TBD | TBD |
| Time to group formation p50 | TBD | TBD |
| Time to group formation p95 | TBD | TBD |
| Time to `LINK_READY` p50 | TBD | TBD |
| Time to `LINK_READY` p95 | TBD | TBD |

## Transfer Metrics

| Metric | Value | Notes |
| --- | --- | --- |
| Total bytes | TBD | TBD |
| Transfer duration excluding interruption | TBD | TBD |
| Effective throughput | TBD | TBD |
| Chunks sent | TBD | TBD |
| Chunks verified | TBD | TBD |
| Chunks retransmitted | TBD | TBD |
| Chunk ACK mode | TBD | Per-chunk, window, bitmap |
| Backpressure events | TBD | TBD |

## Recovery Metrics

| Metric | Value | Notes |
| --- | --- | --- |
| Bytes transferred before interruption | TBD | TBD |
| Verified chunks before interruption | TBD | TBD |
| Time to rediscover peer | TBD | TBD |
| Time to resume transfer | TBD | TBD |
| Missing chunks calculated | TBD | TBD |
| Duplicate chunks after resume | TBD | Target: 0 except corruption recovery |

## Integrity Metrics

| Metric | Value | Notes |
| --- | --- | --- |
| Chunk verification failures | TBD | TBD |
| Corrupt chunks rejected | TBD | TBD |
| Final object hash matches | TBD | REQUIRED |
| Final object reconstructed path | TBD | App-private controlled path |

## Event Log Checklist

- [ ] `BLE_SCAN_STARTED`
- [ ] `BLE_PEER_DISCOVERED`
- [ ] `BLE_CONTROL_CONNECTED`
- [ ] `CAPABILITIES_EXCHANGED`
- [ ] `WIFI_DIRECT_UPGRADE_REQUESTED`
- [ ] `WIFI_DIRECT_GROUP_FORMED`
- [ ] `WIFI_DIRECT_LINK_READY`
- [ ] `TRANSFER_STARTED`
- [ ] `CHUNK_SENT`
- [ ] `CHUNK_RECEIVED`
- [ ] `CHUNK_VERIFIED`
- [ ] `CHUNK_REJECTED`
- [ ] `LINK_LOST`
- [ ] `TRANSFER_INTERRUPTED`
- [ ] `PEER_REDISCOVERED`
- [ ] `TRANSFER_RESUME_STARTED`
- [ ] `MISSING_CHUNKS_CALCULATED`
- [ ] `TRANSFER_RESUMED`
- [ ] `OBJECT_RECONSTRUCTED`
- [ ] `OBJECT_HASH_VERIFIED`
- [ ] `TRANSFER_COMPLETED`
