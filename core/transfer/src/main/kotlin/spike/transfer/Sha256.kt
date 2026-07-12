package spike.transfer

import java.io.InputStream
import java.security.MessageDigest

object Sha256 {
    private const val BUFFER_SIZE = 128 * 1024

    fun hash(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hashStream(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }

        return digest.digest()
    }

    fun hex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { each -> "%02x".format(each) }
}
