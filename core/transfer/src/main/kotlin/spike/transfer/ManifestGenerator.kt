package spike.transfer

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

class ManifestGenerator(
    private val chunkSizeBytes: Long = DEFAULT_CHUNK_SIZE_BYTES
) {
    init {
        require(chunkSizeBytes in 1..Int.MAX_VALUE.toLong()) { "Chunk size must fit bounded memory reads" }
    }

    fun generate(file: File): ObjectManifest {
        require(file.isFile) { "Transfer object must be a file" }

        val totalSize = file.length()
        val objectHash = file.inputStream().use { Sha256.hex(Sha256.hashStream(it)) }
        val chunkCount = calculateChunkCount(totalSize, chunkSizeBytes)
        val chunks = mutableListOf<ChunkDescriptor>()

        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(chunkSizeBytes.toInt())
            for (index in 0 until chunkCount) {
                val offset = index.toLong() * chunkSizeBytes
                val length = min(chunkSizeBytes, totalSize - offset).toInt()
                raf.seek(offset)
                raf.readFully(buffer, 0, length)
                chunks += ChunkDescriptor(
                    objectId = objectHash,
                    chunkIndex = index,
                    offset = offset,
                    length = length,
                    chunkHashHex = Sha256.hex(Sha256.hash(buffer.copyOf(length)))
                )
            }
        }

        val transferObject = TransferObject(
            objectId = objectHash,
            totalSize = totalSize,
            chunkSize = chunkSizeBytes,
            chunkCount = chunkCount,
            objectHashHex = objectHash
        )

        return ObjectManifest(
            manifestVersion = MANIFEST_VERSION,
            transferObject = transferObject,
            chunks = chunks
        )
    }

    private fun calculateChunkCount(totalSize: Long, chunkSize: Long): Int {
        if (totalSize == 0L) return 0
        val count = ((totalSize - 1L) / chunkSize) + 1L
        require(count <= Int.MAX_VALUE) { "Too many chunks for experimental manifest" }
        return count.toInt()
    }
}
