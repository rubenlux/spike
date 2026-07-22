package spike.dtnrouting.application

import spike.dtnrouting.contracts.RoutingStrategy
import spike.dtnrouting.domain.*
import spike.identity.domain.NodeId

/**
 * Deterministic candidate tie-breaker comparator.
 * Order: Priority (descending) -> CreatedAt (ascending) -> EnvelopeId (lexicographical ascending)
 */
object DeterministicCandidateComparator : Comparator<DtnEnvelope> {
    override fun compare(o1: DtnEnvelope, o2: DtnEnvelope): Int {
        val pComp = o2.priority.ordinal.compareTo(o1.priority.ordinal) // Higher priority first
        if (pComp != 0) return pComp
        val cComp = o1.createdAt.compareTo(o2.createdAt) // Older envelopes first
        if (cComp != 0) return cComp
        return o1.envelopeId.value.compareTo(o2.envelopeId.value) // Deterministic tie-breaker
    }
}

class DirectRoutingStrategy : RoutingStrategy {
    override val strategyId: RoutingStrategyId = RoutingStrategyId("DIRECT_ONLY")
    override val strategyType: RoutingStrategyType = RoutingStrategyType.DIRECT_ONLY

    override suspend fun evaluateCandidates(
        localEnvelopes: List<Pair<DtnEnvelope, StoredEnvelope>>,
        remotePeerNodeId: NodeId,
        remoteSummary: RoutingSummary
    ): List<DtnEnvelope> {
        val now = System.currentTimeMillis()
        return localEnvelopes
            .filter { (envelope, _) ->
                envelope.destinationNodeId == remotePeerNodeId &&
                !envelope.isExpiredAt(now) &&
                !remoteSummary.knownEnvelopeIds.contains(envelope.envelopeId) &&
                !remoteSummary.deliveredTombstoneIds.contains(envelope.envelopeId)
            }
            .map { it.first }
            .sortedWith(DeterministicCandidateComparator)
    }

    override suspend fun calculateCopyAllocation(
        envelope: DtnEnvelope,
        currentCopyBudget: CopyBudget
    ): Pair<CopyBudget, CopyBudget> {
        // Direct delivery retains current budget, transfers 1
        return Pair(currentCopyBudget, CopyBudget(1))
    }
}

class ControlledEpidemicRoutingStrategy : RoutingStrategy {
    override val strategyId: RoutingStrategyId = RoutingStrategyId("CONTROLLED_EPIDEMIC")
    override val strategyType: RoutingStrategyType = RoutingStrategyType.CONTROLLED_EPIDEMIC

    override suspend fun evaluateCandidates(
        localEnvelopes: List<Pair<DtnEnvelope, StoredEnvelope>>,
        remotePeerNodeId: NodeId,
        remoteSummary: RoutingSummary
    ): List<DtnEnvelope> {
        val now = System.currentTimeMillis()
        return localEnvelopes
            .filter { (envelope, stored) ->
                !envelope.isExpiredAt(now) &&
                stored.localHopCount.value < envelope.hopLimit.value &&
                stored.remainingCopyBudget.value > 0 &&
                !remoteSummary.knownEnvelopeIds.contains(envelope.envelopeId) &&
                !remoteSummary.deliveredTombstoneIds.contains(envelope.envelopeId)
            }
            .map { it.first }
            .sortedWith(DeterministicCandidateComparator)
    }

    override suspend fun calculateCopyAllocation(
        envelope: DtnEnvelope,
        currentCopyBudget: CopyBudget
    ): Pair<CopyBudget, CopyBudget> {
        return Pair(currentCopyBudget, CopyBudget(1))
    }
}

class SprayAndWaitRoutingStrategy : RoutingStrategy {
    override val strategyId: RoutingStrategyId = RoutingStrategyId("SPRAY_AND_WAIT")
    override val strategyType: RoutingStrategyType = RoutingStrategyType.SPRAY_AND_WAIT

    override suspend fun evaluateCandidates(
        localEnvelopes: List<Pair<DtnEnvelope, StoredEnvelope>>,
        remotePeerNodeId: NodeId,
        remoteSummary: RoutingSummary
    ): List<DtnEnvelope> {
        val now = System.currentTimeMillis()
        return localEnvelopes
            .filter { (envelope, stored) ->
                !envelope.isExpiredAt(now) &&
                !remoteSummary.knownEnvelopeIds.contains(envelope.envelopeId) &&
                !remoteSummary.deliveredTombstoneIds.contains(envelope.envelopeId) &&
                (stored.remainingCopyBudget.value > 1 || envelope.destinationNodeId == remotePeerNodeId)
            }
            .map { it.first }
            .sortedWith(DeterministicCandidateComparator)
    }

    override suspend fun calculateCopyAllocation(
        envelope: DtnEnvelope,
        currentCopyBudget: CopyBudget
    ): Pair<CopyBudget, CopyBudget> {
        // Binary Spray-And-Wait split invariant: (remaining, transferred)
        return currentCopyBudget.split()
    }
}
