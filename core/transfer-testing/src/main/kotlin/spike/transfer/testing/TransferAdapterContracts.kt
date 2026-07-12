package spike.transfer.testing

import spike.transfer.contracts.TransferCore
import spike.transfer.domain.TransferId

interface TestTransferAdapter {
    suspend fun runTransfer(source: TransferCore, target: TransferCore, transferId: TransferId): AdapterRunResult
}

data class AdapterRunResult(
    val chunksAttempted: Int,
    val chunksDelivered: Int,
    val duplicateChunks: Int,
    val corruptedChunks: Int
)
