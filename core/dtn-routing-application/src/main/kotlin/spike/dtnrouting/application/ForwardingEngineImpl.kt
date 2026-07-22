package spike.dtnrouting.application

import spike.dtnrouting.contracts.ForwardingEngine
import spike.dtnrouting.contracts.RoutingStrategy
import spike.dtnrouting.domain.*
import spike.dtnrouting.storage.DtnRoutingRepository
import spike.identity.domain.NodeId
import java.util.concurrent.ConcurrentHashMap

class ForwardingEngineImpl(
    private val localNodeId: NodeId,
    private val repository: DtnRoutingRepository
) : ForwardingEngine {

    private val strategies = ConcurrentHashMap<RoutingStrategyId, RoutingStrategy>()

    init {
        // Register default strategies
        registerStrategyInternal(DirectRoutingStrategy())
        registerStrategyInternal(ControlledEpidemicRoutingStrategy())
        registerStrategyInternal(SprayAndWaitRoutingStrategy())
    }

    override suspend fun registerStrategy(strategy: RoutingStrategy) {
        registerStrategyInternal(strategy)
    }

    private fun registerStrategyInternal(strategy: RoutingStrategy) {
        strategies[strategy.strategyId] = strategy
    }

    override suspend fun buildTransferPlan(
        encounterId: String,
        remotePeerNodeId: NodeId,
        remoteSummary: RoutingSummary
    ): RoutingTransferPlan {
        val now = System.currentTimeMillis()

        // 1. Fetch all localStored envelopes
        val allStored = repository.findStoredEnvelopes { it.envelopeState == EnvelopeStateEnum.STORED || it.envelopeState == EnvelopeStateEnum.FORWARDABLE }
        val envelopePairs = mutableListOf<Pair<DtnEnvelope, StoredEnvelope>>()

        for (stored in allStored) {
            val env = repository.getEnvelope(stored.envelopeId)
            if (env != null && !env.isExpiredAt(now)) {
                // Check tombstone duplicate suppression
                if (repository.getTombstone(env.envelopeId) == null) {
                    envelopePairs.add(Pair(env, stored))
                }
            }
        }

        // 2. Group envelopes by routingStrategyId and evaluate candidates via pluggable strategy
        val selectedCandidates = mutableSetOf<DtnEnvelope>()
        val rejectedCandidates = mutableMapOf<EnvelopeId, String>()

        val grouped = envelopePairs.groupBy { it.first.routingStrategyId }
        for ((strategyId, pairs) in grouped) {
            val strategy = strategies[strategyId] ?: strategies[RoutingStrategyId("DIRECT_ONLY")]!!
            val candidates = strategy.evaluateCandidates(pairs, remotePeerNodeId, remoteSummary)
            selectedCandidates.addAll(candidates)
        }

        // 3. Deterministic candidate ordering using domain comparator
        val sortedSelected = selectedCandidates.sortedWith(DeterministicCandidateComparator)

        // 4. Apply backpressure and quota limits
        val finalSelected = mutableListOf<EnvelopeId>()
        var estimatedBytes = 0L

        val maxAllowedBytes = when (remoteSummary.backpressureLevel) {
            BackpressureLevel.NORMAL -> 100 * 1024 * 1024L // 100 MB
            BackpressureLevel.ELEVATED -> 10 * 1024 * 1024L // 10 MB
            BackpressureLevel.HIGH -> 1 * 1024 * 1024L // 1 MB
            BackpressureLevel.CRITICAL -> 0L // No indirect transfers
        }

        for (envelope in sortedSelected) {
            if (estimatedBytes + envelope.payloadLength <= maxAllowedBytes || envelope.destinationNodeId == remotePeerNodeId) {
                finalSelected.add(envelope.envelopeId)
                estimatedBytes += envelope.payloadLength
            } else {
                rejectedCandidates[envelope.envelopeId] = "BACKPRESSURE_LIMIT_EXCEEDED"
            }
        }

        return RoutingTransferPlan(
            planId = "plan_${encounterId}_${now}",
            localNodeId = localNodeId,
            remoteNodeId = remotePeerNodeId,
            encounterId = encounterId,
            createdAt = now,
            strategyId = RoutingStrategyId("ORCHESTRATED"),
            selectedEnvelopeIds = finalSelected,
            rejectedEnvelopeIds = rejectedCandidates,
            estimatedTransferBytes = estimatedBytes
        )
    }

    override suspend fun executeTransferPlan(plan: RoutingTransferPlan): List<ForwardingRecord> {
        val records = mutableListOf<ForwardingRecord>()
        val now = System.currentTimeMillis()

        for (envelopeId in plan.selectedEnvelopeIds) {
            val envelope = repository.getEnvelope(envelopeId) ?: continue
            val stored = repository.getStoredEnvelopeState(envelopeId) ?: continue

            val strategy = strategies[envelope.routingStrategyId] ?: strategies[RoutingStrategyId("DIRECT_ONLY")]!!

            // Monotonic HopCount invariant: HopCount increases by 1 on forwarding
            val newHopCount = stored.localHopCount.next()

            // Copy Budget split invariant: (remaining, transferred)
            val (remainingBudget, _) = strategy.calculateCopyAllocation(envelope, stored.remainingCopyBudget)

            // Update stored envelope state
            val updatedStored = stored.copy(
                localHopCount = newHopCount,
                remainingCopyBudget = remainingBudget,
                lastForwardedAt = now,
                forwardAttemptCount = stored.forwardAttemptCount + 1
            )
            repository.saveStoredEnvelopeState(updatedStored)

            val record = ForwardingRecord(
                envelopeId = envelopeId,
                fromNodeId = localNodeId,
                toNodeId = plan.remoteNodeId,
                encounterId = plan.encounterId,
                attemptedAt = now,
                completedAt = now,
                resultStatus = "SUCCESS",
                bytesTransferred = envelope.payloadLength,
                custodyRequested = plan.custodyRequestedEnvelopeIds.contains(envelopeId),
                custodyAccepted = false,
                strategyId = envelope.routingStrategyId
            )
            repository.saveForwardingRecord(record)
            records.add(record)
        }

        return records
    }
}
