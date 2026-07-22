package spike.dtnrouting.application

import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId
import java.security.MessageDigest

class DtnEnvelopeServiceImpl(
    private val localNodeId: NodeId
) {
    /**
     * Creates an end-to-end encrypted DTN envelope.
     * Invariant: Ciphertext only is exposed in the envelope payload.
     */
    fun createEnvelope(
        destinationNodeId: NodeId,
        payloadType: String,
        payloadBytes: ByteArray,
        routingStrategyId: RoutingStrategyId,
        priority: EnvelopePriority,
        ttlDuration: EnvelopeTtl,
        hopLimit: HopLimit,
        initialCopyBudget: CopyBudget
    ): DtnEnvelope {
        val now = System.currentTimeMillis()

        // E2E encryption mock simulation for testing/architecture
        val ciphertext = mockEncryptPayload(payloadBytes, destinationNodeId)
        val ciphertextHash = computeDigestHex(ciphertext)

        val envelopeId = EnvelopeId.generate()
        val authProof = computeAuthProof(localNodeId, envelopeId, ciphertextHash)

        return DtnEnvelope(
            protocolVersion = "1.0",
            envelopeId = envelopeId,
            sourceNodeId = localNodeId,
            destinationNodeId = destinationNodeId,
            payloadType = payloadType,
            inlinePayloadCiphertext = ciphertext,
            payloadLength = ciphertext.size.toLong(),
            payloadCiphertextHash = PayloadCiphertextHash(ciphertextHash),
            routingStrategyId = routingStrategyId,
            priority = priority,
            createdAt = now,
            ttlDuration = ttlDuration,
            hopLimit = hopLimit,
            initialCopyBudget = initialCopyBudget,
            sourceAuthenticationProof = authProof
        )
    }

    fun decryptPayload(envelope: DtnEnvelope): ByteArray {
        val ciphertext = envelope.inlinePayloadCiphertext
            ?: throw DtnRoutingError.EnvelopeMalformed(envelope.envelopeId, "Inline payload ciphertext is null")
        return mockDecryptPayload(ciphertext, envelope.sourceNodeId)
    }

    fun validateEnvelope(envelope: DtnEnvelope) {
        val now = System.currentTimeMillis()
        if (envelope.isExpiredAt(now)) {
            throw DtnRoutingError.EnvelopeExpired(envelope.envelopeId, envelope.expiresAt)
        }
        val ciphertext = envelope.inlinePayloadCiphertext
        if (ciphertext != null) {
            val computedHash = computeDigestHex(ciphertext)
            if (computedHash != envelope.payloadCiphertextHash.value) {
                throw DtnRoutingError.EnvelopeHashMismatch(envelope.payloadCiphertextHash.value, computedHash)
            }
        }
    }

    private fun mockEncryptPayload(bytes: ByteArray, destination: NodeId): ByteArray {
        val prefix = "E2E[to:${destination.value}]:".toByteArray(Charsets.UTF_8)
        return prefix + bytes
    }

    private fun mockDecryptPayload(ciphertext: ByteArray, source: NodeId): ByteArray {
        val str = String(ciphertext, Charsets.UTF_8)
        val idx = str.indexOf("]:")
        if (idx != -1) {
            return str.substring(idx + 2).toByteArray(Charsets.UTF_8)
        }
        return ciphertext
    }

    private fun computeAuthProof(src: NodeId, envelopeId: EnvelopeId, hash: String): String {
        return "PROOF_v1_${src.value}_${envelopeId.value}_$hash"
    }

    private fun computeDigestHex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
