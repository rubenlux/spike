package spike.dtnrouting.application

import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository

class DtnRecoveryServiceImpl(
    private val repository: DtnRoutingRepository
) {
    suspend fun recover() {
        val now = System.currentTimeMillis()

        // 1. Recover stored envelopes
        val allStored = repository.findStoredEnvelopes { true }
        for (stored in allStored) {
            val envelope = repository.getEnvelope(stored.envelopeId)
            if (envelope == null) {
                // Orphan stored state without envelope metadata
                repository.deleteStoredEnvelopeState(stored.envelopeId)
                continue
            }

            // Check deadline/TTL expiration
            if (envelope.isExpiredAt(now)) {
                repository.saveStoredEnvelopeState(stored.copy(envelopeState = EnvelopeStateEnum.EXPIRED))
                continue
            }

            // Reconcile tombstone status
            val tombstone = repository.getTombstone(stored.envelopeId)
            if (tombstone != null && stored.envelopeState != EnvelopeStateEnum.DELIVERED) {
                repository.saveStoredEnvelopeState(stored.copy(envelopeState = EnvelopeStateEnum.DELIVERED))
            }
        }
    }
}
