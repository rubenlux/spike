package spike.discovery.domain

import java.util.UUID

const val SUPPORTED_DISCOVERY_VERSION = 1
const val DEFAULT_EPHEMERAL_ID_SIZE_BYTES = 16
const val DEFAULT_ADVERTISEMENT_NONCE_SIZE_BYTES = 4
const val DEFAULT_CONTROL_SESSION_NONCE_SIZE_BYTES = 16
const val DEFAULT_MAX_ADVERTISEMENT_BYTES = 26
const val DEFAULT_MAX_CONTROL_FRAME_BYTES = 4096
const val DEFAULT_DISCOVERY_ID_ROTATION_INTERVAL_MILLIS = 15 * 60 * 1000L
const val DISCOVERY_AUTH_CONTEXT_DOMAIN_TAG = "OFFLINE_PROTOCOL_DISCOVERY_AUTH_V1"
const val EPHEMERAL_ID_DOMAIN_TAG = "OFFLINE_PROTOCOL_EPHEMERAL_DISCOVERY_ID_V1"

data class ProtocolServiceId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProtocolServiceId is required" }
        require(value.length <= 64) { "ProtocolServiceId is too long" }
    }
}

object ProtocolServiceIds {
    val SPIKE_DISCOVERY_V1 = ProtocolServiceId("spike.discovery.v1")
}

data class DiscoveryVersion(val value: Int) {
    init {
        require(value > 0) { "DiscoveryVersion must be positive" }
    }
}

data class EphemeralDiscoveryId(val bytes: ByteArray) {
    init {
        require(bytes.size == DEFAULT_EPHEMERAL_ID_SIZE_BYTES) {
            "EphemeralDiscoveryId must be $DEFAULT_EPHEMERAL_ID_SIZE_BYTES bytes"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is EphemeralDiscoveryId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

data class AdvertisementNonce(val bytes: ByteArray) {
    init {
        require(bytes.size in 4..16) { "AdvertisementNonce must be between 4 and 16 bytes" }
    }

    override fun equals(other: Any?): Boolean =
        other is AdvertisementNonce && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

enum class DiscoveryCapability(val bit: Int) {
    BLE_CONTROL_SUPPORTED(1 shl 0),
    IDENTITY_V1_SUPPORTED(1 shl 1),
    MUTUAL_POP_SUPPORTED(1 shl 2),
    ENCOUNTER_V1_SUPPORTED(1 shl 3)
}

data class CapabilityBitmap(val bits: Int) {
    init {
        require(bits >= 0) { "CapabilityBitmap must not use the sign bit" }
    }

    fun supports(capability: DiscoveryCapability): Boolean =
        bits and capability.bit == capability.bit

    fun includesAll(required: CapabilityBitmap): Boolean =
        bits and required.bits == required.bits

    companion object {
        fun of(vararg capabilities: DiscoveryCapability): CapabilityBitmap =
            CapabilityBitmap(capabilities.fold(0) { acc, capability -> acc or capability.bit })
    }
}

data class CandidateId(val value: String) {
    init {
        require(value.isNotBlank()) { "CandidateId is required" }
        require(value.length <= 128) { "CandidateId is too long" }
    }
}

data class ObservationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ObservationId is required" }
        require(value.length <= 128) { "ObservationId is too long" }
    }
}

data class DiscoveryTimestamp(val monotonicMillis: Long) {
    init {
        require(monotonicMillis >= 0) { "DiscoveryTimestamp must be monotonic and non-negative" }
    }
}

data class SignalStrength(val rssiDbm: Int) {
    init {
        require(rssiDbm in -127..20) { "SignalStrength must be a plausible RSSI value" }
    }
}

data class TransportReference(val value: String) {
    init {
        require(value.isNotBlank()) { "TransportReference is required" }
        require(value.length <= 512) { "TransportReference is too long" }
    }
}

data class AuthenticationSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "AuthenticationSessionId is required" }
        require(value.length <= 128) { "AuthenticationSessionId is too long" }
    }
}

data class ControlSessionNonce(val bytes: ByteArray) {
    init {
        require(bytes.size >= DEFAULT_CONTROL_SESSION_NONCE_SIZE_BYTES) {
            "ControlSessionNonce must contain at least 128 bits"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ControlSessionNonce && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

data class ControlSequenceNumber(val value: Long) {
    init {
        require(value >= 0) { "ControlSequenceNumber must be non-negative" }
    }
}

object DiscoveryIdGenerator {
    fun candidateId(): CandidateId = CandidateId(UUID.randomUUID().toString())
    fun observationId(): ObservationId = ObservationId(UUID.randomUUID().toString())
    fun authenticationSessionId(): AuthenticationSessionId = AuthenticationSessionId(UUID.randomUUID().toString())
}

