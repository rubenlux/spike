package spike.secure.session.application

import spike.secure.session.domain.*
import java.io.*

class SecureSessionWireCodec {
    private val HANDSHAKE_MAGIC = 0x53534831
    private val RECORD_MAGIC = 0x53524543

    fun encodeSessionHello(
        version: Int,
        supportedSuites: String,
        encounterId: String,
        handshakeId: String,
        initiatorNodeId: String,
        responderNodeId: String,
        nonce: ByteArray,
        ephemeralPublicKey: ByteArray,
        capabilities: Map<String, String>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(HANDSHAKE_MAGIC)
        dos.writeShort(0) // Type 0: SESSION_HELLO
        dos.writeShort(version)
        dos.writeSizedString(supportedSuites)
        dos.writeSizedString(encounterId)
        dos.writeSizedString(handshakeId)
        dos.writeSizedString(initiatorNodeId)
        dos.writeSizedString(responderNodeId)
        require(nonce.size == 16)
        dos.write(nonce)
        require(ephemeralPublicKey.size == 32)
        dos.write(ephemeralPublicKey)
        dos.writeSizedString(mapToString(capabilities))
        dos.flush()
        return baos.toByteArray()
    }

    fun encodeSessionHelloResponse(
        version: Int,
        selectedSuite: String,
        encounterId: String,
        handshakeId: String,
        initiatorNodeId: String,
        responderNodeId: String,
        initiatorNonce: ByteArray,
        responderNonce: ByteArray,
        initiatorEphemeralPublicKey: ByteArray,
        responderEphemeralPublicKey: ByteArray
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(HANDSHAKE_MAGIC)
        dos.writeShort(1) // Type 1: SESSION_HELLO_RESPONSE
        dos.writeShort(version)
        dos.writeSizedString(selectedSuite)
        dos.writeSizedString(encounterId)
        dos.writeSizedString(handshakeId)
        dos.writeSizedString(initiatorNodeId)
        dos.writeSizedString(responderNodeId)
        require(initiatorNonce.size == 16)
        dos.write(initiatorNonce)
        require(responderNonce.size == 16)
        dos.write(responderNonce)
        require(initiatorEphemeralPublicKey.size == 32)
        dos.write(initiatorEphemeralPublicKey)
        require(responderEphemeralPublicKey.size == 32)
        dos.write(responderEphemeralPublicKey)
        dos.flush()
        return baos.toByteArray()
    }

    fun encodeHandshakeAuth(
        handshakeId: String,
        signerNodeId: String,
        signerRoleOrdinal: Int,
        transcriptHash: ByteArray,
        signature: ByteArray
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(HANDSHAKE_MAGIC)
        dos.writeShort(2) // Type 2: HANDSHAKE_AUTH
        dos.writeSizedString(handshakeId)
        dos.writeSizedString(signerNodeId)
        dos.writeByte(signerRoleOrdinal)
        require(transcriptHash.size == 32)
        dos.write(transcriptHash)
        require(signature.size == 64)
        dos.write(signature)
        dos.flush()
        return baos.toByteArray()
    }

    fun encodeKeyConfirmation(
        handshakeId: String,
        signerRoleOrdinal: Int,
        confirmationTag: ByteArray
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(HANDSHAKE_MAGIC)
        dos.writeShort(3) // Type 3: KEY_CONFIRMATION
        dos.writeSizedString(handshakeId)
        dos.writeByte(signerRoleOrdinal)
        require(confirmationTag.size == 32)
        dos.write(confirmationTag)
        dos.flush()
        return baos.toByteArray()
    }

    fun decodeHandshakeFrame(bytes: ByteArray): DecodedHandshakeFrame {
        val bais = ByteArrayInputStream(bytes)
        val dis = DataInputStream(bais)
        val magic = dis.readInt()
        if (magic != HANDSHAKE_MAGIC) {
            throw SecureSessionError.TranscriptError.MalformedHandshakeTranscript("Invalid handshake magic")
        }
        val type = dis.readUnsignedShort()
        return when (type) {
            0 -> {
                val version = dis.readUnsignedShort()
                val supportedSuites = dis.readSizedString()
                val encounterId = dis.readSizedString()
                val handshakeId = dis.readSizedString()
                val initiatorNodeId = dis.readSizedString()
                val responderNodeId = dis.readSizedString()
                val nonce = ByteArray(16)
                dis.readFully(nonce)
                val ephemPubKey = ByteArray(32)
                dis.readFully(ephemPubKey)
                val capsStr = dis.readSizedString()
                DecodedHandshakeFrame.SessionHello(
                    version, supportedSuites, encounterId, handshakeId,
                    initiatorNodeId, responderNodeId, nonce, ephemPubKey, stringToMap(capsStr)
                )
            }
            1 -> {
                val version = dis.readUnsignedShort()
                val selectedSuite = dis.readSizedString()
                val encounterId = dis.readSizedString()
                val handshakeId = dis.readSizedString()
                val initiatorNodeId = dis.readSizedString()
                val responderNodeId = dis.readSizedString()
                val initNonce = ByteArray(16)
                dis.readFully(initNonce)
                val respNonce = ByteArray(16)
                dis.readFully(respNonce)
                val initEphemKey = ByteArray(32)
                dis.readFully(initEphemKey)
                val respEphemKey = ByteArray(32)
                dis.readFully(respEphemKey)
                DecodedHandshakeFrame.SessionHelloResponse(
                    version, selectedSuite, encounterId, handshakeId,
                    initiatorNodeId, responderNodeId, initNonce, respNonce, initEphemKey, respEphemKey
                )
            }
            2 -> {
                val handshakeId = dis.readSizedString()
                val signerNodeId = dis.readSizedString()
                val roleOrdinal = dis.readUnsignedByte()
                val transcriptHash = ByteArray(32)
                dis.readFully(transcriptHash)
                val signature = ByteArray(64)
                dis.readFully(signature)
                DecodedHandshakeFrame.HandshakeAuth(
                    handshakeId, signerNodeId, roleOrdinal, transcriptHash, signature
                )
            }
            3 -> {
                val handshakeId = dis.readSizedString()
                val roleOrdinal = dis.readUnsignedByte()
                val confirmationTag = ByteArray(32)
                dis.readFully(confirmationTag)
                DecodedHandshakeFrame.KeyConfirmation(
                    handshakeId, roleOrdinal, confirmationTag
                )
            }
            else -> throw SecureSessionError.TranscriptError.MalformedHandshakeTranscript("Unknown handshake type: $type")
        }
    }

    fun encodeSecureRecord(record: SecureRecord): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(RECORD_MAGIC)
        dos.writeByte(record.recordVersion)
        dos.writeInt(record.keyEpoch.value)
        dos.writeLong(record.sequenceNumber)
        dos.writeByte(record.recordType.ordinal)
        dos.writeSizedString(record.sessionId.value)
        dos.writeInt(record.ciphertextLength)
        dos.write(record.ciphertext)
        require(record.authenticationTag.size == 16)
        dos.write(record.authenticationTag)
        dos.flush()
        return baos.toByteArray()
    }

    fun decodeSecureRecord(bytes: ByteArray): SecureRecord {
        val bais = ByteArrayInputStream(bytes)
        val dis = DataInputStream(bais)
        val magic = dis.readInt()
        if (magic != RECORD_MAGIC) {
            throw SecureSessionError.SecureRecordError.MalformedSecureRecord("Invalid record magic")
        }
        val recordVersion = dis.readUnsignedByte()
        val keyEpoch = dis.readInt()
        val sequenceNumber = dis.readLong()
        val recordTypeOrdinal = dis.readUnsignedByte()
        val sessionIdVal = dis.readSizedString()
        val ciphertextLength = dis.readInt()
        if (ciphertextLength < 0 || ciphertextLength > 10 * 1024 * 1024) {
            throw SecureSessionError.SecureRecordError.SecureRecordTooLarge("Ciphertext size exceeds limit")
        }
        val ciphertext = ByteArray(ciphertextLength)
        dis.readFully(ciphertext)
        val tag = ByteArray(16)
        dis.readFully(tag)

        val recordTypes = SecureRecordType.values()
        if (recordTypeOrdinal !in recordTypes.indices) {
            throw SecureSessionError.SecureRecordError.MalformedSecureRecord("Invalid record type ordinal: $recordTypeOrdinal")
        }

        return SecureRecord(
            recordVersion = recordVersion,
            sessionId = SecureSessionId(sessionIdVal),
            keyEpoch = KeyEpoch(keyEpoch),
            sequenceNumber = sequenceNumber,
            recordType = recordTypes[recordTypeOrdinal],
            ciphertextLength = ciphertextLength,
            ciphertext = ciphertext,
            authenticationTag = tag
        )
    }

    private fun DataOutputStream.writeSizedString(str: String) {
        val bytes = str.encodeToByteArray()
        require(bytes.size <= UShort.MAX_VALUE.toInt()) { "String is too large for sizing prefix" }
        writeShort(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedString(): String {
        val size = readUnsignedShort()
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.decodeToString()
    }

    companion object {
        fun mapToString(map: Map<String, String>): String =
            map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

        fun stringToMap(str: String): Map<String, String> {
            if (str.isBlank()) return emptyMap()
            return str.split(",").associate {
                val parts = it.split("=")
                parts[0] to parts.getOrElse(1) { "" }
            }
        }
    }
}

sealed class DecodedHandshakeFrame {
    data class SessionHello(
        val version: Int,
        val supportedSuites: String,
        val encounterId: String,
        val handshakeId: String,
        val initiatorNodeId: String,
        val responderNodeId: String,
        val nonce: ByteArray,
        val ephemeralPublicKey: ByteArray,
        val capabilities: Map<String, String>
    ) : DecodedHandshakeFrame() {
        override fun equals(other: Any?): Boolean =
            other is SessionHello && version == other.version && supportedSuites == other.supportedSuites &&
                    encounterId == other.encounterId && handshakeId == other.handshakeId &&
                    initiatorNodeId == other.initiatorNodeId && responderNodeId == other.responderNodeId &&
                    nonce.contentEquals(other.nonce) && ephemeralPublicKey.contentEquals(other.ephemeralPublicKey) &&
                    capabilities == other.capabilities

        override fun hashCode(): Int = 31 * version + nonce.contentHashCode() + ephemeralPublicKey.contentHashCode()
    }

    data class SessionHelloResponse(
        val version: Int,
        val selectedSuite: String,
        val encounterId: String,
        val handshakeId: String,
        val initiatorNodeId: String,
        val responderNodeId: String,
        val initiatorNonce: ByteArray,
        val responderNonce: ByteArray,
        val initiatorEphemeralPublicKey: ByteArray,
        val responderEphemeralPublicKey: ByteArray
    ) : DecodedHandshakeFrame() {
        override fun equals(other: Any?): Boolean =
            other is SessionHelloResponse && version == other.version && selectedSuite == other.selectedSuite &&
                    encounterId == other.encounterId && handshakeId == other.handshakeId &&
                    initiatorNodeId == other.initiatorNodeId && responderNodeId == other.responderNodeId &&
                    initiatorNonce.contentEquals(other.initiatorNonce) && responderNonce.contentEquals(other.responderNonce) &&
                    initiatorEphemeralPublicKey.contentEquals(other.initiatorEphemeralPublicKey) &&
                    responderEphemeralPublicKey.contentEquals(other.responderEphemeralPublicKey)

        override fun hashCode(): Int = 31 * version + initiatorNonce.contentHashCode() + initiatorEphemeralPublicKey.contentHashCode()
    }

    data class HandshakeAuth(
        val handshakeId: String,
        val signerNodeId: String,
        val signerRoleOrdinal: Int,
        val transcriptHash: ByteArray,
        val signature: ByteArray
    ) : DecodedHandshakeFrame() {
        override fun equals(other: Any?): Boolean =
            other is HandshakeAuth && handshakeId == other.handshakeId && signerNodeId == other.signerNodeId &&
                    signerRoleOrdinal == other.signerRoleOrdinal && transcriptHash.contentEquals(other.transcriptHash) &&
                    signature.contentEquals(other.signature)

        override fun hashCode(): Int = 31 * signerRoleOrdinal + transcriptHash.contentHashCode() + signature.contentHashCode()
    }

    data class KeyConfirmation(
        val handshakeId: String,
        val signerRoleOrdinal: Int,
        val confirmationTag: ByteArray
    ) : DecodedHandshakeFrame() {
        override fun equals(other: Any?): Boolean =
            other is KeyConfirmation && handshakeId == other.handshakeId &&
                    signerRoleOrdinal == other.signerRoleOrdinal && confirmationTag.contentEquals(other.confirmationTag)

        override fun hashCode(): Int = 31 * signerRoleOrdinal + confirmationTag.contentHashCode()
    }
}
