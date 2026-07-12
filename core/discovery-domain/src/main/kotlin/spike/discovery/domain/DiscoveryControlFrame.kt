package spike.discovery.domain

import java.nio.ByteBuffer

enum class DiscoveryControlFrameType(val code: Int, val critical: Boolean = true) {
    DISCOVERY_HELLO(1),
    DISCOVERY_HELLO_ACK(2),
    IDENTITY_DOCUMENT(3),
    IDENTITY_DOCUMENT_ACK(4),
    AUTH_CHALLENGE(5),
    AUTH_PROOF(6),
    AUTH_RESULT(7),
    ENCOUNTER_READY(8),
    ERROR(9),
    CLOSE(10, critical = false);

    companion object {
        fun fromCode(code: Int): DiscoveryControlFrameType? =
            values().firstOrNull { it.code == code }
    }
}

data class DiscoveryControlFrame(
    val protocolVersion: DiscoveryVersion,
    val frameType: DiscoveryControlFrameType,
    val sessionNonce: ControlSessionNonce,
    val sequenceNumber: ControlSequenceNumber,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is DiscoveryControlFrame &&
            protocolVersion == other.protocolVersion &&
            frameType == other.frameType &&
            sessionNonce == other.sessionNonce &&
            sequenceNumber == other.sequenceNumber &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int =
        31 * (31 * (31 * protocolVersion.hashCode() + frameType.hashCode()) + sessionNonce.hashCode()) +
            sequenceNumber.hashCode() + payload.contentHashCode()
}

class ControlFrameCodec(
    private val maxFrameBytes: Int = DEFAULT_MAX_CONTROL_FRAME_BYTES,
    private val sessionNonceBytes: Int = DEFAULT_CONTROL_SESSION_NONCE_SIZE_BYTES
) {
    private val headerBytes = 1 + 1 + sessionNonceBytes + 8 + 4

    fun encode(frame: DiscoveryControlFrame): ByteArray {
        if (frame.sessionNonce.bytes.size != sessionNonceBytes) {
            throw MalformedControlFrame("Session nonce must be $sessionNonceBytes bytes")
        }
        val size = headerBytes + frame.payload.size
        if (size > maxFrameBytes) throw ControlFrameTooLarge(size, maxFrameBytes)
        return ByteBuffer.allocate(size)
            .put(frame.protocolVersion.value.toByte())
            .put(frame.frameType.code.toByte())
            .put(frame.sessionNonce.bytes)
            .putLong(frame.sequenceNumber.value)
            .putInt(frame.payload.size)
            .put(frame.payload)
            .array()
    }

    fun decode(bytes: ByteArray): DiscoveryControlFrame {
        if (bytes.size > maxFrameBytes) throw ControlFrameTooLarge(bytes.size, maxFrameBytes)
        if (bytes.size < headerBytes) throw MalformedControlFrame("Frame is shorter than the envelope header")

        val buffer = ByteBuffer.wrap(bytes)
        val version = buffer.get().toInt() and 0xff
        if (version != SUPPORTED_DISCOVERY_VERSION) throw UnsupportedDiscoveryVersion(version)
        val frameTypeCode = buffer.get().toInt() and 0xff
        val frameType = DiscoveryControlFrameType.fromCode(frameTypeCode)
            ?: throw MalformedControlFrame("Unknown critical frame type: $frameTypeCode")
        val nonce = ByteArray(sessionNonceBytes)
        buffer.get(nonce)
        val sequenceNumber = buffer.long
        val payloadLength = buffer.int
        if (payloadLength < 0) throw MalformedControlFrame("Negative payload length")
        if (payloadLength != buffer.remaining()) {
            throw MalformedControlFrame("Payload length does not match remaining frame bytes")
        }
        val payload = ByteArray(payloadLength)
        buffer.get(payload)
        return DiscoveryControlFrame(
            protocolVersion = DiscoveryVersion(version),
            frameType = frameType,
            sessionNonce = ControlSessionNonce(nonce),
            sequenceNumber = ControlSequenceNumber(sequenceNumber),
            payload = payload
        )
    }
}

class ControlSequenceValidator(
    private val expectedSessionNonce: ControlSessionNonce
) {
    private var lastAcceptedSequenceNumber: Long = -1

    fun accept(frame: DiscoveryControlFrame) {
        if (frame.sessionNonce != expectedSessionNonce) throw SessionNonceMismatch()
        if (frame.sequenceNumber.value <= lastAcceptedSequenceNumber) {
            throw InvalidSequenceNumber(frame.sequenceNumber.value, lastAcceptedSequenceNumber)
        }
        lastAcceptedSequenceNumber = frame.sequenceNumber.value
    }
}
