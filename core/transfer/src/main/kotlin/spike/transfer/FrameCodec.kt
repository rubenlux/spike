package spike.transfer

import java.io.DataInputStream
import java.io.DataOutputStream

private const val FRAME_MAGIC = 0x53504b31
private const val MAX_FRAME_PAYLOAD_BYTES = 8 * 1024 * 1024

enum class FrameType {
    HELLO,
    MANIFEST,
    MANIFEST_ACK,
    MISSING_CHUNKS,
    CHUNK,
    CHUNK_ACK,
    TRANSFER_STATUS,
    COMPLETE,
    COMPLETE_ACK,
    ERROR
}

data class TransferFrame(
    val protocolVersion: Int,
    val frameType: FrameType,
    val transferId: String,
    val sequenceNumber: Long,
    val payload: ByteArray,
    val integrityCheckHex: String = Sha256.hex(Sha256.hash(payload))
) {
    init {
        require(payload.size <= MAX_FRAME_PAYLOAD_BYTES) { "Frame payload too large" }
        require(transferId.isNotBlank()) { "Transfer ID is required" }
    }
}

class FrameCodec {
    fun write(output: DataOutputStream, frame: TransferFrame) {
        val transferIdBytes = frame.transferId.encodeToByteArray()
        val integrityBytes = frame.integrityCheckHex.encodeToByteArray()
        require(transferIdBytes.size <= UShort.MAX_VALUE.toInt()) { "Transfer ID too large" }
        require(integrityBytes.size <= UShort.MAX_VALUE.toInt()) { "Integrity field too large" }

        output.writeInt(FRAME_MAGIC)
        output.writeInt(frame.protocolVersion)
        output.writeInt(frame.frameType.ordinal)
        output.writeLong(frame.sequenceNumber)
        output.writeShort(transferIdBytes.size)
        output.write(transferIdBytes)
        output.writeShort(integrityBytes.size)
        output.write(integrityBytes)
        output.writeInt(frame.payload.size)
        output.write(frame.payload)
        output.flush()
    }

    fun read(input: DataInputStream): TransferFrame {
        val magic = input.readInt()
        require(magic == FRAME_MAGIC) { "Invalid frame magic" }

        val protocolVersion = input.readInt()
        val frameTypeOrdinal = input.readInt()
        val sequenceNumber = input.readLong()
        val transferId = input.readSizedString()
        val integrity = input.readSizedString()
        val payloadLength = input.readInt()
        require(payloadLength in 0..MAX_FRAME_PAYLOAD_BYTES) { "Invalid frame payload length" }

        val payload = ByteArray(payloadLength)
        input.readFully(payload)
        require(Sha256.hex(Sha256.hash(payload)) == integrity) { "Frame integrity check failed" }

        val frameTypes = FrameType.values()
        require(frameTypeOrdinal in frameTypes.indices) { "Invalid frame type" }

        return TransferFrame(
            protocolVersion = protocolVersion,
            frameType = frameTypes[frameTypeOrdinal],
            transferId = transferId,
            sequenceNumber = sequenceNumber,
            payload = payload,
            integrityCheckHex = integrity
        )
    }

    private fun DataInputStream.readSizedString(): String {
        val size = readUnsignedShort()
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.decodeToString()
    }
}
