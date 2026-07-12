package spike.secure.session.domain

import spike.crypto.EphemeralPublicKey

data class SecureSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SecureSessionId cannot be blank" }
    }
}

data class HandshakeId(val value: String) {
    init {
        require(value.isNotBlank()) { "HandshakeId cannot be blank" }
    }
}

data class SessionNonce(val bytes: ByteArray) {
    init {
        require(bytes.size == 16) { "SessionNonce must be exactly 16 bytes" }
    }

    override fun equals(other: Any?): Boolean =
        other is SessionNonce && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}

enum class CipherSuiteId(val value: String) {
    OFFLINE_SESSION_SUITE_V1("OFFLINE_SESSION_SUITE_V1")
}

data class SendSequence(val value: Long = 0L) {
    init {
        require(value >= 0L) { "Sequence number must be non-negative" }
    }

    fun next(): SendSequence {
        if (value == Long.MAX_VALUE) {
            throw SecureSessionError.SecureRecordError.SequenceOverflow("Sequence number overflow")
        }
        return SendSequence(value + 1)
    }
}

data class ReceiveSequence(val value: Long = 0L) {
    init {
        require(value >= 0L) { "Sequence number must be non-negative" }
    }

    fun next(): ReceiveSequence = ReceiveSequence(value + 1)
}

data class KeyEpoch(val value: Int = 0) {
    init {
        require(value >= 0) { "KeyEpoch must be non-negative" }
    }

    fun next(): KeyEpoch = KeyEpoch(value + 1)
}

enum class SessionRole {
    INITIATOR,
    RESPONDER
}

enum class SecureRecordType {
    CONTROL,
    TRANSFER_CONTROL,
    TRANSPORT_UPGRADE,
    HEARTBEAT,
    KEY_UPDATE,
    CLOSE,
    ERROR
}
