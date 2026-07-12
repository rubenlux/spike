# Storage

Durable local storage boundary for SPEC-001.

The receiver must persist:

- Object manifest before accepting chunks.
- Verified chunk files in an app-controlled directory.
- Transfer state and timestamps.
- Verified chunk bitmap/list.
- Metrics and event logs.

The implementation should reject peer-provided paths and names. Any final reconstruction path must be derived locally from `objectId` and app-private directories.
