package spike.discovery.domain

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

interface RotationSecretStore {
    suspend fun getOrCreateRotationSecret(sizeBytes: Int = 32): ByteArray
}

interface MonotonicClock {
    fun nowMillis(): Long
}

data class EphemeralIdConfig(
    val rotationIntervalMillis: Long = DEFAULT_DISCOVERY_ID_ROTATION_INTERVAL_MILLIS,
    val outputSizeBytes: Int = DEFAULT_EPHEMERAL_ID_SIZE_BYTES
) {
    init {
        require(rotationIntervalMillis > 0) { "Rotation interval must be positive" }
        require(outputSizeBytes == DEFAULT_EPHEMERAL_ID_SIZE_BYTES) {
            "Discovery advertisement v1 currently requires 128-bit ephemeral IDs"
        }
    }
}

data class EphemeralAdvertisementIdentity(
    val epoch: Long,
    val ephemeralDiscoveryId: EphemeralDiscoveryId,
    val localRotationNonce: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is EphemeralAdvertisementIdentity &&
            epoch == other.epoch &&
            ephemeralDiscoveryId == other.ephemeralDiscoveryId &&
            localRotationNonce.contentEquals(other.localRotationNonce)

    override fun hashCode(): Int =
        31 * (31 * epoch.hashCode() + ephemeralDiscoveryId.hashCode()) + localRotationNonce.contentHashCode()
}

class EphemeralIdService(
    private val secretStore: RotationSecretStore,
    private val clock: MonotonicClock,
    private val config: EphemeralIdConfig = EphemeralIdConfig(),
    private val secureRandom: SecureRandom = SecureRandom()
) {
    private var cachedEpoch: Long? = null
    private var cachedNonce: ByteArray? = null

    suspend fun currentIdentity(): EphemeralAdvertisementIdentity {
        val epoch = epochFor(clock.nowMillis())
        val nonce = if (cachedEpoch == epoch && cachedNonce != null) {
            cachedNonce!!
        } else {
            ByteArray(16).also {
                secureRandom.nextBytes(it)
                cachedEpoch = epoch
                cachedNonce = it
            }
        }

        val secret = secretStore.getOrCreateRotationSecret()
        val input = ByteBuffer.allocate(EPHEMERAL_ID_DOMAIN_TAG.toByteArray().size + 8 + nonce.size)
            .put(EPHEMERAL_ID_DOMAIN_TAG.toByteArray())
            .putLong(epoch)
            .put(nonce)
            .array()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val digest = mac.doFinal(input).copyOf(config.outputSizeBytes)
        return EphemeralAdvertisementIdentity(epoch, EphemeralDiscoveryId(digest), nonce.copyOf())
    }

    fun epochFor(monotonicMillis: Long): Long =
        monotonicMillis / config.rotationIntervalMillis
}

class InMemoryRotationSecretStore(
    private val secureRandom: SecureRandom = SecureRandom()
) : RotationSecretStore {
    private var secret: ByteArray? = null

    override suspend fun getOrCreateRotationSecret(sizeBytes: Int): ByteArray {
        require(sizeBytes >= 32) { "Rotation secret should contain at least 256 bits" }
        if (secret == null) {
            secret = ByteArray(sizeBytes).also { secureRandom.nextBytes(it) }
        }
        return secret!!.copyOf()
    }
}
