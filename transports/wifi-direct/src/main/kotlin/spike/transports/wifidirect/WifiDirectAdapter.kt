package spike.transports.wifidirect

import java.io.InputStream
import java.io.OutputStream
import spike.transfer.Peer

data class WifiDirectLink(
    val peer: Peer,
    val isGroupOwner: Boolean,
    val groupOwnerAddress: String,
    val input: InputStream,
    val output: OutputStream
)

interface WifiDirectAdapter {
    suspend fun formGroup(peer: Peer): WifiDirectLink

    suspend fun disconnect(link: WifiDirectLink)

    suspend fun forceDisconnectForTest()
}
