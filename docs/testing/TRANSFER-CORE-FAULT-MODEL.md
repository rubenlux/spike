# Transfer Core Fault Model

**Status**: Draft  
**Spec**: `specs/002-transfer-core/spec.md`

## Fault Classes

The Core must tolerate or classify:

- Invalid manifests.
- Unsupported manifest versions.
- Invalid chunk indexes and lengths.
- Chunk hash mismatch.
- Final object hash mismatch.
- Duplicate chunks.
- Out-of-order chunks.
- Write failure.
- Metadata commit failure.
- Missing verified chunk file.
- Stray chunk file without verified metadata.
- Process death during chunk write.
- Process death after chunk commit.
- Process death before event append.
- Concurrent mutation for one transfer.
- Repeated recovery.

## Required Fault Injection Points

```text
before temporary chunk write
during chunk write
after chunk write
before hash verification
after hash verification
before durable commit
after durable commit
before VerifiedChunk registration
after VerifiedChunk registration
before event append
after event append
during object reconstruction
before atomic finalization
```

## Recovery Expectations

| Fault | Expected Recovery |
| --- | --- |
| Temp chunk without verified record | Delete temp chunk |
| Verified record without chunk file | Remove verified state and mark missing |
| Chunk file without verified record | Quarantine or delete; do not trust |
| Active transfer after restart | Move to `INTERRUPTED`, then `WAITING` |
| All chunks verified but not complete | Resume verification/reconstruction |
| Final hash mismatch | Mark failed; forbid completed |

## Mandatory Scenarios

- Network-free loopback transfer.
- Interruption and resume from 35%.
- Duplicate valid chunks.
- Out-of-order valid chunks.
- Corrupt chunk followed by correct chunk.
- Process death and recovery.
- Final hash mismatch.

## Properties

- `verified union missing = all chunks`
- `verified intersect missing = empty`
- duplicate valid chunk does not change progress after first acceptance
- invalid chunk never increases progress
- completed implies final object hash valid
- repeated recovery is idempotent
