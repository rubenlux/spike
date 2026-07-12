package spike.transports.ble.control

import spike.discovery.contracts.ControlChannel
import spike.discovery.contracts.ControlChannelFactory

interface BleControlChannel : ControlChannel

interface BleControlChannelFactory : ControlChannelFactory

data class BleControlLimits(
    val maxFrameBytes: Int = 4096,
    val preferredMtuBytes: Int = 185,
    val connectionTimeoutMillis: Long = 15_000
)

