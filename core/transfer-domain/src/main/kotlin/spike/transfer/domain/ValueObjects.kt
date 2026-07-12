package spike.transfer.domain

private val SHA256_OBJECT_ID = Regex("^sha256:[0-9a-f]{64}$")
private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

data class ObjectId(val value: String) {
    init {
        require(SHA256_OBJECT_ID.matches(value)) { "ObjectId must be sha256:<64 lowercase hex>" }
    }

    val algorithm: String get() = value.substringBefore(":")
    val hashHex: String get() = value.substringAfter(":")
}

data class TransferId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransferId is required" }
        require(value.length <= 128) { "TransferId is too long" }
    }
}

data class NodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "NodeId is required" }
        require(value.length <= 256) { "NodeId is too long" }
    }
}

data class ChunkIndex(val value: Int) {
    init {
        require(value >= 0) { "ChunkIndex must be non-negative" }
    }
}

data class Sha256Hash(val hex: String) {
    init {
        require(SHA256_HEX.matches(hex)) { "SHA-256 hash must be 64 lowercase hex characters" }
    }

    fun toObjectId(): ObjectId = ObjectId("sha256:$hex")
}

data class ByteOffset(val value: Long) {
    init {
        require(value >= 0) { "ByteOffset must be non-negative" }
    }
}

data class ByteLength(val value: Long) {
    init {
        require(value > 0) { "ByteLength must be positive" }
    }
}

data class ManifestVersion(val value: Int) {
    init {
        require(value > 0) { "ManifestVersion must be positive" }
    }
}

data class ProtocolVersion(val value: Int) {
    init {
        require(value > 0) { "ProtocolVersion must be positive" }
    }
}
