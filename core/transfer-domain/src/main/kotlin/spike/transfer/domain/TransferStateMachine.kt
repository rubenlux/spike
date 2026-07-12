package spike.transfer.domain

class TransferStateMachine {
    private val allowed = mapOf(
        TransferState.CREATED to setOf(TransferState.PREPARING, TransferState.CANCELLED),
        TransferState.PREPARING to setOf(TransferState.READY, TransferState.FAILED, TransferState.CANCELLED),
        TransferState.READY to setOf(TransferState.ACTIVE, TransferState.CANCELLED),
        TransferState.ACTIVE to setOf(
            TransferState.INTERRUPTED,
            TransferState.VERIFYING,
            TransferState.FAILED,
            TransferState.CANCELLED
        ),
        TransferState.INTERRUPTED to setOf(TransferState.WAITING, TransferState.FAILED, TransferState.CANCELLED),
        TransferState.WAITING to setOf(TransferState.RESUMING, TransferState.FAILED, TransferState.CANCELLED),
        TransferState.RESUMING to setOf(TransferState.ACTIVE, TransferState.FAILED, TransferState.CANCELLED),
        TransferState.VERIFYING to setOf(TransferState.COMPLETED, TransferState.FAILED),
        TransferState.COMPLETED to emptySet(),
        TransferState.FAILED to emptySet(),
        TransferState.CANCELLED to emptySet()
    )

    fun validateTransition(from: TransferState, to: TransferState) {
        if (from == to) return
        if (to !in allowed.getValue(from)) {
            throw InvalidTransferStateTransition(from, to)
        }
    }

    fun isTerminal(state: TransferState): Boolean =
        state == TransferState.COMPLETED || state == TransferState.FAILED || state == TransferState.CANCELLED
}
