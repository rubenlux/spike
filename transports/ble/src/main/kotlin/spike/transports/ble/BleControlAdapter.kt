package spike.transports.ble

import spike.transfer.Peer
import spike.transfer.PeerCapabilities

data class BleAdvertisement(
    val serviceUuid: String,
    val protocolVersion: Int,
    val ephemeralDiscoveryId: String,
    val capabilityFlags: Int
)

interface BleControlAdapter {
    suspend fun startAdvertising(advertisement: BleAdvertisement)

    suspend fun stopAdvertising()

    suspend fun startScan(onPeerDiscovered: suspend (Peer) -> Unit)

    suspend fun stopScan()

    suspend fun connectControl(peer: Peer)

    suspend fun exchangeCapabilities(peer: Peer, local: PeerCapabilities): PeerCapabilities

    suspend fun requestWifiDirectUpgrade(peer: Peer, transferId: String)
}
