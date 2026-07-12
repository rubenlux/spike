package spike.transfer

class ChunkVerifier {
    fun verify(descriptor: ChunkDescriptor, payload: ByteArray): Boolean {
        if (payload.size != descriptor.length) return false
        return Sha256.hex(Sha256.hash(payload)) == descriptor.chunkHashHex
    }

    fun validateDescriptor(manifest: ObjectManifest, descriptor: ChunkDescriptor) {
        require(descriptor.objectId == manifest.transferObject.objectId) { "Chunk object ID mismatch" }
        require(descriptor.chunkIndex in manifest.chunks.indices) { "Invalid chunk index" }
        require(descriptor.offset >= 0) { "Invalid chunk offset" }
        require(descriptor.length > 0) { "Invalid chunk length" }

        val expected = manifest.chunks[descriptor.chunkIndex]
        require(expected == descriptor) { "Chunk descriptor does not match manifest" }
    }
}
