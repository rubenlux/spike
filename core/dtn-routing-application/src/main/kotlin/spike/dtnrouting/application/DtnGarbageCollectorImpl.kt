package spike.dtnrouting.application

import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository

class DtnGarbageCollectorImpl(
    private val repository: DtnRoutingRepository
) {
    suspend fun runGarbageCollection(): Int {
        val now = System.currentTimeMillis()
        var evictedCount = 0

        // 1. Evict expired stored envelopes
        val allStored = repository.findStoredEnvelopes { true }
        for (stored in allStored) {
            val envelope = repository.getEnvelope(stored.envelopeId)
            if (envelope == null || envelope.isExpiredAt(now)) {
                repository.deleteStoredEnvelopeState(stored.envelopeId)
                evictedCount++
            }
        }

        // 2. Evict tombstones ONLY if past explicit expiresAt timestamp
        // Invariant: Tombstones must be preserved until explicit GC expiration
        val tombstones = repository.listTombstones()
        for (tombstone in tombstones) {
            if (tombstone.isExpiredAt(now)) {
                repository.deleteTombstone(tombstone.envelopeId)
                evictedCount++
            }
        }

        return evictedCount
    }
}
