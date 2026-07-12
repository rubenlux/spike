# Discovery Privacy Model

Privacy goals for v1:

- hide stable `NodeId` from passive BLE advertisement observers;
- hide public keys from advertisements;
- avoid device/user names and transfer metadata;
- rotate `EphemeralDiscoveryId`;
- keep capability bits minimal.

Non-goals for v1:

- perfect unlinkability;
- protection from a global passive adversary;
- identity hiding after control-channel identity exchange;
- full fingerprinting resistance from radio characteristics or OS behavior.

The current implementation enforces the advertisement privacy boundary by constructing the payload from fixed non-identity fields only.

