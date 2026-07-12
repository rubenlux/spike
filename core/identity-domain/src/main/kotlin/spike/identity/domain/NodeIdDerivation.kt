package spike.identity.domain

import java.security.MessageDigest

object NodeIdDerivation {
    fun canonicalPublicKey(publicKey: PublicKeyBytes): ByteArray {
        val algorithm = publicKey.algorithm.name.encodeToByteArray()
        val length = publicKey.bytes.size.toString().encodeToByteArray()
        return byteArrayOf(1) + algorithm + byteArrayOf(0) + length + byteArrayOf(0) + publicKey.bytes
    }

    fun deriveKeyId(publicKey: PublicKeyBytes): KeyId =
        KeyId("sha256:${sha256Hex(canonicalPublicKey(publicKey))}")

    fun deriveNodeId(publicKey: PublicKeyBytes): NodeId =
        NodeId("node:v1:sha256:${sha256Hex(canonicalPublicKey(publicKey))}")

    fun validateBinding(nodeId: NodeId, keyId: KeyId, publicKey: PublicKeyBytes) {
        if (deriveKeyId(publicKey) != keyId) throw KeyIdMismatch()
        if (deriveNodeId(publicKey) != nodeId) throw NodeIdPublicKeyMismatch()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { each -> "%02x".format(each) }
    }
}
