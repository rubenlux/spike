package spike.transfer

object MissingChunks {
    fun calculate(manifest: ObjectManifest, verifiedChunkIndexes: Set<Int>): List<ChunkDescriptor> =
        manifest.chunks.filter { chunk -> chunk.chunkIndex !in verifiedChunkIndexes }
}
