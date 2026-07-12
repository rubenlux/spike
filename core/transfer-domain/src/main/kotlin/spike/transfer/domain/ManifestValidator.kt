package spike.transfer.domain

class ManifestValidator {
    fun validate(manifest: ObjectManifest) {
        if (manifest.manifestVersion.value != SUPPORTED_MANIFEST_VERSION) {
            throw UnsupportedManifestVersion(manifest.manifestVersion.value)
        }
        if (manifest.objectId != manifest.objectHash.toObjectId()) {
            throw InvalidManifest("objectId must match objectHash")
        }
        if (manifest.chunkCount <= 0) {
            throw InvalidManifest("chunkCount must be positive")
        }
        if (manifest.chunks.size != manifest.chunkCount) {
            throw InvalidManifest("chunks length must equal chunkCount")
        }

        var expectedOffset = 0L
        var total = 0L
        val seen = mutableSetOf<Int>()

        manifest.chunks.forEachIndexed { expectedIndex, chunk ->
            val index = chunk.chunkIndex.value
            if (index != expectedIndex) throw InvalidManifest("chunk indexes must be contiguous")
            if (!seen.add(index)) throw InvalidManifest("duplicate chunk index: $index")
            if (chunk.offset.value != expectedOffset) throw InvalidManifest("chunk offset gap or overlap")
            if (chunk.length.value > manifest.chunkSize.value) {
                throw InvalidManifest("chunk length exceeds chunkSize")
            }
            expectedOffset += chunk.length.value
            total += chunk.length.value
        }

        if (total != manifest.totalSize.value) {
            throw InvalidManifest("sum(chunk.length) must equal totalSize")
        }
    }
}
