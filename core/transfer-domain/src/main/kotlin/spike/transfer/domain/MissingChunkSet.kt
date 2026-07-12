package spike.transfer.domain

data class MissingChunkRange(
    val first: ChunkIndex,
    val lastInclusive: ChunkIndex
) {
    init {
        require(first.value <= lastInclusive.value) { "Invalid missing chunk range" }
    }
}

data class MissingChunkSet(
    val objectId: ObjectId,
    val totalChunks: Int,
    val missingCount: Int,
    val ranges: List<MissingChunkRange>,
    val generatedAtEpochMillis: Long
) {
    fun isComplete(): Boolean = missingCount == 0
}

class MissingChunkCalculator {
    fun calculate(
        manifest: ObjectManifest,
        verifiedChunkIndexes: Set<ChunkIndex>,
        generatedAtEpochMillis: Long
    ): MissingChunkSet {
        val verified = verifiedChunkIndexes.map { it.value }.toSet()
        val ranges = mutableListOf<MissingChunkRange>()
        var start: Int? = null
        var previous: Int? = null
        var missingCount = 0

        for (index in 0 until manifest.chunkCount) {
            if (index in verified) {
                if (start != null && previous != null) {
                    ranges += MissingChunkRange(ChunkIndex(start), ChunkIndex(previous))
                }
                start = null
                previous = null
            } else {
                missingCount += 1
                if (start == null) start = index
                previous = index
            }
        }

        if (start != null && previous != null) {
            ranges += MissingChunkRange(ChunkIndex(start), ChunkIndex(previous))
        }

        return MissingChunkSet(
            objectId = manifest.objectId,
            totalChunks = manifest.chunkCount,
            missingCount = missingCount,
            ranges = ranges,
            generatedAtEpochMillis = generatedAtEpochMillis
        )
    }
}
