package spike.secure.session.domain

import spike.crypto.EphemeralKeyPair
import spike.crypto.EphemeralPublicKey

enum class SessionState {
    NEW,
    HANDSHAKING,
    AUTHENTICATING,
    DERIVING_KEYS,
    CONFIRMING_KEYS,
    ESTABLISHED,
    REKEYING,
    CLOSING,
    CLOSED,
    FAILED
}

enum class HandshakeState {
    NEW,
    NEGOTIATING,
    EPHEMERAL_KEY_GENERATED,
    HELLO_SENT,
    HELLO_RECEIVED,
    KEY_EXCHANGE,
    TRANSCRIPT_READY,
    LOCAL_AUTH_SENT,
    REMOTE_AUTH_VERIFIED,
    KEYS_DERIVED,
    KEY_CONFIRMING,
    ESTABLISHED,
    CLOSED,
    FAILED
}

data class SecureRecord(
    val recordVersion: Int,
    val sessionId: SecureSessionId,
    val keyEpoch: KeyEpoch,
    val sequenceNumber: Long,
    val recordType: SecureRecordType,
    val ciphertextLength: Int,
    val ciphertext: ByteArray,
    val authenticationTag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureRecord) return false
        return recordVersion == other.recordVersion &&
                sessionId == other.sessionId &&
                keyEpoch == other.keyEpoch &&
                sequenceNumber == other.sequenceNumber &&
                recordType == other.recordType &&
                ciphertextLength == other.ciphertextLength &&
                ciphertext.contentEquals(other.ciphertext) &&
                authenticationTag.contentEquals(other.authenticationTag)
    }

    override fun hashCode(): Int {
        var result = recordVersion
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + keyEpoch.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + recordType.hashCode()
        result = 31 * result + ciphertextLength
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authenticationTag.contentHashCode()
        return result
    }
}

data class SessionKeyMaterial(
    val sendKey: ByteArray,
    val receiveKey: ByteArray,
    val sendNonceBase: ByteArray,
    val receiveNonceBase: ByteArray,
    val sendRekeySecret: ByteArray,
    val receiveRekeySecret: ByteArray,
    val exporterSecret: ByteArray
) {
    fun destroy() {
        sendKey.fill(0)
        receiveKey.fill(0)
        sendNonceBase.fill(0)
        receiveNonceBase.fill(0)
        sendRekeySecret.fill(0)
        receiveRekeySecret.fill(0)
        exporterSecret.fill(0)
    }
}

data class HandshakeContext(
    var handshakeId: HandshakeId,
    var state: HandshakeState = HandshakeState.NEW,
    var localNonce: SessionNonce? = null,
    var remoteNonce: SessionNonce? = null,
    var localEphemeralKeyPair: EphemeralKeyPair? = null,
    var remoteEphemeralPublicKey: EphemeralPublicKey? = null,
    var dhSharedSecret: ByteArray? = null,
    var transcriptHash: ByteArray? = null,
    var localAuthSignature: ByteArray? = null,
    var remoteAuthSignature: ByteArray? = null
) {
    fun destroy() {
        localEphemeralKeyPair?.privateKey?.destroy()
        dhSharedSecret?.fill(0)
    }
}

data class SecureSession(
    var sessionId: SecureSessionId,
    val encounterId: String,
    val localNodeId: String,
    val remoteNodeId: String,
    var role: SessionRole? = null,
    var state: SessionState = SessionState.NEW,
    var currentEpoch: KeyEpoch = KeyEpoch(0),
    var sendSequence: SendSequence = SendSequence(0L),
    var expectedReceiveSequence: ReceiveSequence = ReceiveSequence(0L),
    var keyMaterial: SessionKeyMaterial? = null,
    var transcriptHash: ByteArray? = null,
    var failureReason: String? = null,
    var recordsSent: Long = 0L,
    var recordsReceived: Long = 0L,
    var bytesSent: Long = 0L,
    var bytesReceived: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    var establishedAt: Long? = null,
    var lastActivityAt: Long = System.currentTimeMillis(),
    var closedAt: Long? = null,
    var handshakeContext: HandshakeContext? = null
) {
    fun destroy() {
        keyMaterial?.destroy()
        keyMaterial = null
        handshakeContext?.destroy()
        handshakeContext = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureSession) return false
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int {
        return sessionId.hashCode()
    }
}
