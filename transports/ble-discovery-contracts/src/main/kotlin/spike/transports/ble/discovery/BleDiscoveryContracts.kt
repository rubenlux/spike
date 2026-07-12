package spike.transports.ble.discovery

import spike.discovery.contracts.DiscoveryAdvertiser
import spike.discovery.contracts.DiscoveryScanner

interface BleDiscoveryAdvertiser : DiscoveryAdvertiser

interface BleDiscoveryScanner : DiscoveryScanner

data class BleAdvertisementBudget(
    val legacyPayloadBudgetBytes: Int = 31,
    val serviceDataBudgetBytes: Int = 26,
    val extendedAdvertisingAvailable: Boolean = false
)

