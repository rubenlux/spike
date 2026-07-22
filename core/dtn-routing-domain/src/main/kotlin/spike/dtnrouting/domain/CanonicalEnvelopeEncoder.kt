package spike.dtnrouting.domain

import spike.identity.domain.NodeId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object CanonicalEnvelopeEncoder {

    const val DOMAIN_TAG = "OFFLINE_DTN_E2E_ENVELOPE_V1"

    /**
     * Serializes canonical envelope header fields for authentication and hashing.
     */
    fun encodeCanonicalHeader(envelope: DtnEnvelope): ByteArray {
        val sb = StringBuilder()
        sb.append(DOMAIN_TAG).append("\n")
        sb.append("version=").append(envelope.protocolVersion).append("\n")
        sb.append("id=").append(envelope.envelopeId.value).append("\n")
        sb.append("src=").append(envelope.sourceNodeId.value).append("\n")
        sb.append("dst=").append(envelope.destinationNodeId.value).append("\n")
        sb.append("type=").append(envelope.payloadType).append("\n")
        sb.append("hash=").append(envelope.payloadCiphertextHash.value).append("\n")
        sb.append("strategy=").append(envelope.routingStrategyId.value).append("\n")
        sb.append("priority=").append(envelope.priority.name).append("\n")
        sb.append("created=").append(envelope.createdAt).append("\n")
        sb.append("ttl=").append(envelope.ttlDuration.durationMillis).append("\n")
        sb.append("hop_limit=").append(envelope.hopLimit.value).append("\n")
        sb.append("initial_copy_budget=").append(envelope.initialCopyBudget.value).append("\n")
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Derives a SHA-256 digest of the canonical envelope header and payload ciphertext hash.
     */
    fun computeHeaderDigest(envelope: DtnEnvelope): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(encodeCanonicalHeader(envelope))
        val hashBytes = digest.digest()
        val sb = java.lang.StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
