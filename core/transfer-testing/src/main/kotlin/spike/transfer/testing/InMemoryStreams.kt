package spike.transfer.testing

import spike.transfer.contracts.BoundedReadableStream

class ByteArrayReadableStream(
    private val bytes: ByteArray
) : BoundedReadableStream {
    override val length: Long = bytes.size.toLong()
    private var cursor = 0
    private var closed = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "Stream is closed" }
        if (cursor >= bytes.size) return -1
        val count = minOf(length, bytes.size - cursor)
        bytes.copyInto(buffer, destinationOffset = offset, startIndex = cursor, endIndex = cursor + count)
        cursor += count
        return count
    }

    override fun close() {
        closed = true
    }
}

fun ByteArray.asReadableStream(): BoundedReadableStream = ByteArrayReadableStream(this)
