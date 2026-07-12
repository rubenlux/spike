# SPIKE-001 Android Constraints

**Status**: Research notes plus placeholders for device findings  
**Spec**: `specs/001-hybrid-offline-link/spec.md`  
**Last updated**: 2026-07-09

## Sources Checked

- Android Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Android BLE overview: https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview
- Android Wi-Fi Direct guide: https://developer.android.com/develop/connectivity/wifi/wifi-direct
- Android nearby Wi-Fi devices permission: https://developer.android.com/develop/connectivity/wifi/wifi-permissions

## BLE Permissions and Privacy

Android 12+ separates nearby Bluetooth permissions such as `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT`. If the app can strongly assert that scan results are never used to derive physical location, Android documents `android:usesPermissionFlags="neverForLocation"` on `BLUETOOTH_SCAN`; it also warns that some BLE beacons may be filtered when this flag is used.

Spike implication:

- The app should request the smallest permission set that still permits BLE advertise, scan and GATT/control behavior on the target devices.
- If using `neverForLocation`, record whether protocol advertisements are filtered on either test device.
- For older Android versions, location permission behavior must be tested explicitly.

## Wi-Fi Direct Permissions

Android 13+ uses `NEARBY_WIFI_DEVICES` for nearby Wi-Fi operations. Android documentation lists `WifiP2pManager` methods such as `connect()`, `createGroup()`, `discoverPeers()`, `discoverServices()`, `requestDeviceInfo()`, `requestGroupInfo()` and `requestPeers()` under APIs requiring this permission when targeting Android 13 or higher.

Spike implication:

- Declare and request `NEARBY_WIFI_DEVICES` for target API 33+.
- Keep backward-compatible location declarations only where needed by target Android versions.
- If asserting `neverForLocation`, verify that Wi-Fi Direct peer discovery and group formation still work on the tested devices.

## Wi-Fi Direct Connection Model

Android's Wi-Fi Direct flow uses peer discovery, connection negotiation, connection-change broadcasts and `requestConnectionInfo()` to obtain group owner IP/connection details after the P2P link is connected.

Spike implication:

- The Wi-Fi Direct adapter must treat Group Owner as a temporary link role only.
- The adapter must emit explicit events for group formation, role assignment, endpoint IP resolution and socket readiness.
- BLE peer identity and Wi-Fi Direct peer identity need an application-level correlation handshake; do not assume the BLE address maps directly to the P2P device identity.

## Background Behavior to Measure

The spike must measure, not assume:

- BLE scan reliability in foreground, background and screen-off states.
- BLE advertising reliability in foreground, background and screen-off states.
- Wi-Fi Direct connection maintenance when the app loses foreground.
- Whether foreground service/notification is required for stable long-running transfer.
- OEM-specific battery optimization effects.

## Device Findings

| Constraint | Device A | Device B | Notes |
| --- | --- | --- | --- |
| BLE advertise supported | TBD | TBD | TBD |
| BLE scan while screen off | TBD | TBD | TBD |
| BLE GATT/control channel stable | TBD | TBD | TBD |
| Wi-Fi Direct discover peers | TBD | TBD | TBD |
| Wi-Fi Direct group prompts | TBD | TBD | TBD |
| Group formation success rate | TBD | TBD | TBD |
| Group owner endpoint available | TBD | TBD | TBD |
| Socket survives app background | TBD | TBD | TBD |
| Transfer survives receiver app close | TBD | TBD | TBD |
| Transfer survives receiver reboot | TBD | TBD | TBD |

## Open Risk Register

| Risk | Evidence Needed | Mitigation Candidate |
| --- | --- | --- |
| BLE discovery is unreliable for opportunistic encounters | Foreground/background latency and miss rate | Larger scan/advertise windows, foreground service, alternate discovery |
| Wi-Fi Direct requires manual prompts | Actual group formation recordings | UX gate, alternate transport, explicit user consent model |
| BLE peer cannot be correlated with Wi-Fi Direct peer | Handshake logs before and after upgrade | Exchange ephemeral nonce over both channels |
| Upgrade latency too high | Distribution over repeated attempts | Higher threshold, BLE-only fallback, delayed bulk transfer |
| Background limits break transfer | Screen-off/background tests | Foreground service or constrain use case |
