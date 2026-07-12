package spike.discovery.domain

import java.nio.ByteBuffer
import java.security.SecureRandom

data class AdvertisementConfig(
    val protocolServiceId: ProtocolServiceId = ProtocolServiceIds.SPIKE_DISCOVERY_V1,
    val supportedVersion: DiscoveryVersion = DiscoveryVersion(SUPPORTED_DISCOVERY_VERSION),
    val requiredCapabilities: CapabilityBitmap = CapabilityBitmap.of(
        DiscoveryCapability.BLE_CONTROL_SUPPORTED,
        DiscoveryCapability.IDENTITY_V1_SUPPORTED,
        DiscoveryCapability.MUTUAL_POP_SUPPORTED,
        DiscoveryCapability.ENCOUNTER_V1_SUPPORTED
    ),
    val maxAdvertisementBytes: Int = DEFAULT_MAX_ADVERTISEMENT_BYTES
)

class AdvertisementService(
    private val config: AdvertisementConfig = AdvertisementConfig(),
    private val secureRandom: SecureRandom = SecureRandom()
) {
    fun createAdvertisement(
        ephemeralDiscoveryId: EphemeralDiscoveryId,
        capabilities: CapabilityBitmap = config.requiredCapabilities,
        flags: Int = 0
    ): DiscoveryAdvertisementV1 {
        val nonce = ByteArray(DEFAULT_ADVERTISEMENT_NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)
        return DiscoveryAdvertisementV1(
            discoveryVersion = config.supportedVersion,
            ephemeralDiscoveryId = ephemeralDiscoveryId,
            advertisementNonce = AdvertisementNonce(nonce),
            capabilityBitmap = capabilities,
            flags = flags
        )
    }

    fun encode(advertisement: DiscoveryAdvertisementV1): RawDiscoveryAdvertisement {
        val payload = ByteBuffer.allocate(DEFAULT_MAX_ADVERTISEMENT_BYTES)
            .put(advertisement.discoveryVersion.value.toByte())
            .put(advertisement.ephemeralDiscoveryId.bytes)
            .put(advertisement.advertisementNonce.bytes)
            .putInt(advertisement.capabilityBitmap.bits)
            .put(advertisement.flags.toByte())
            .array()

        if (payload.size > config.maxAdvertisementBytes) {
            throw AdvertisementTooLarge(payload.size, config.maxAdvertisementBytes)
        }

        return RawDiscoveryAdvertisement(config.protocolServiceId, payload)
    }

    fun validate(raw: RawDiscoveryAdvertisement): DiscoveryAdvertisementV1 {
        if (raw.protocolServiceId != config.protocolServiceId) {
            throw UnexpectedProtocolServiceId(raw.protocolServiceId)
        }
        if (raw.payload.size > config.maxAdvertisementBytes) {
            throw AdvertisementTooLarge(raw.payload.size, config.maxAdvertisementBytes)
        }
        if (raw.payload.size != DEFAULT_MAX_ADVERTISEMENT_BYTES) {
            throw MalformedAdvertisement("Discovery advertisement v1 must be exactly $DEFAULT_MAX_ADVERTISEMENT_BYTES bytes")
        }

        val buffer = ByteBuffer.wrap(raw.payload)
        val version = buffer.get().toInt() and 0xff
        if (version != config.supportedVersion.value) {
            throw UnsupportedDiscoveryVersion(version)
        }

        val ephemeralBytes = ByteArray(DEFAULT_EPHEMERAL_ID_SIZE_BYTES)
        buffer.get(ephemeralBytes)
        val nonceBytes = ByteArray(DEFAULT_ADVERTISEMENT_NONCE_SIZE_BYTES)
        buffer.get(nonceBytes)
        val capabilities = CapabilityBitmap(buffer.int)
        val flags = buffer.get().toInt() and 0xff

        if (!capabilities.includesAll(config.requiredCapabilities)) {
            throw IncompatibleCapabilities()
        }

        return DiscoveryAdvertisementV1(
            discoveryVersion = DiscoveryVersion(version),
            ephemeralDiscoveryId = runCatching { EphemeralDiscoveryId(ephemeralBytes) }
                .getOrElse { throw InvalidEphemeralDiscoveryId(it.message ?: "Invalid ephemeral id") },
            advertisementNonce = AdvertisementNonce(nonceBytes),
            capabilityBitmap = capabilities,
            flags = flags
        )
    }
}

