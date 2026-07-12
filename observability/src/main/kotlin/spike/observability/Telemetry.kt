package spike.observability

import spike.transfer.TransportKind

enum class EventType {
    BLE_SCAN_STARTED,
    BLE_PEER_DISCOVERED,
    BLE_CONTROL_CONNECTED,
    CAPABILITIES_EXCHANGED,
    WIFI_DIRECT_UPGRADE_REQUESTED,
    WIFI_DIRECT_GROUP_FORMED,
    WIFI_DIRECT_LINK_READY,
    TRANSFER_STARTED,
    CHUNK_SENT,
    CHUNK_RECEIVED,
    CHUNK_VERIFIED,
    CHUNK_REJECTED,
    LINK_LOST,
    TRANSFER_INTERRUPTED,
    PEER_REDISCOVERED,
    TRANSFER_RESUME_STARTED,
    MISSING_CHUNKS_CALCULATED,
    TRANSFER_RESUMED,
    OBJECT_RECONSTRUCTED,
    OBJECT_HASH_VERIFIED,
    TRANSFER_COMPLETED
}

data class TelemetryEvent(
    val timestampEpochMillis: Long,
    val deviceIdExperimental: String,
    val peerIdExperimental: String?,
    val transferId: String?,
    val eventType: EventType,
    val transport: TransportKind,
    val relevantMetrics: Map<String, String>
)

interface TelemetrySink {
    suspend fun emit(event: TelemetryEvent)

    suspend fun exportJsonLines(): String
}
