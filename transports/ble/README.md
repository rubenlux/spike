# BLE Transport Adapter

BLE is the discovery and control transport for SPEC-001.

Responsibilities:

- Advertise protocol service UUID, version, ephemeral discovery ID and capability flags.
- Scan for compatible peers.
- Establish a BLE control channel.
- Exchange capabilities.
- Signal upgrade request to Wi-Fi Direct.

BLE must not transfer large file chunks in the mandatory scenario unless the deterministic transport policy chooses BLE because pending bytes are below the threshold.
