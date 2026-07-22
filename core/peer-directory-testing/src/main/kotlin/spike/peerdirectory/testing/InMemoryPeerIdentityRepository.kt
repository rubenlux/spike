package spike.peerdirectory.testing

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*
import spike.peerdirectory.storage.PeerIdentityRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryPeerIdentityRepository : PeerIdentityRepository {
    val directoryEntries = ConcurrentHashMap<NodeId, PeerDirectoryEntry>()
    val humanIdentities = ConcurrentHashMap<HumanIdentityId, HumanIdentity>()
    val bindings = ConcurrentHashMap<BindingId, HumanNodeBinding>()
    val verificationSessions = ConcurrentHashMap<VerificationSessionId, VerificationSession>()
    val verificationEvidences = ConcurrentHashMap<VerificationEvidenceId, VerificationEvidence>()
    val consumedNonces = ConcurrentHashMap.newKeySet<String>()
    val identityHistory = CopyOnWriteArrayList<IdentityHistoryEntry>()
    val replacementOperations = ConcurrentHashMap<ReplacementOperationId, DeviceReplacementOperation>()
    val migrationPlans = ConcurrentHashMap<TrustMigrationPlanId, TrustMigrationPlan>()

    override suspend fun savePeerDirectoryEntry(entry: PeerDirectoryEntry) {
        directoryEntries[entry.peerNodeId] = entry
    }

    override suspend fun getPeerDirectoryEntry(peerNodeId: NodeId): PeerDirectoryEntry? {
        return directoryEntries[peerNodeId]
    }

    override suspend fun findPeers(
        aliasFilter: LocalAlias?,
        humanIdentityId: HumanIdentityId?,
        nodeIdPrefix: String?,
        verificationStatus: VerificationResult?
    ): List<PeerDirectoryEntry> {
        return directoryEntries.values.filter { entry ->
            (aliasFilter == null || entry.localAlias == aliasFilter) &&
            (verificationStatus == null || entry.verificationStatus == verificationStatus) &&
            (nodeIdPrefix == null || entry.peerNodeId.value.startsWith(nodeIdPrefix))
        }
    }

    override suspend fun saveHumanIdentity(identity: HumanIdentity) {
        humanIdentities[identity.humanIdentityId] = identity
    }

    override suspend fun getHumanIdentity(humanIdentityId: HumanIdentityId): HumanIdentity? {
        return humanIdentities[humanIdentityId]
    }

    override suspend fun listHumanIdentities(): List<HumanIdentity> {
        return humanIdentities.values.toList()
    }

    override suspend fun saveBinding(binding: HumanNodeBinding) {
        bindings[binding.bindingId] = binding
    }

    override suspend fun getBinding(bindingId: BindingId): HumanNodeBinding? {
        return bindings[bindingId]
    }

    override suspend fun findActiveBindingByPeer(peerNodeId: NodeId): HumanNodeBinding? {
        return bindings.values.find {
            it.peerNodeId == peerNodeId && (it.state == BindingState.VERIFIED || it.state == BindingState.TRUSTED_UNVERIFIED)
        }
    }

    override suspend fun findBindingsByHumanIdentity(humanIdentityId: HumanIdentityId): List<HumanNodeBinding> {
        return bindings.values.filter { it.humanIdentityId == humanIdentityId }
    }

    override suspend fun saveVerificationSession(session: VerificationSession) {
        verificationSessions[session.verificationSessionId] = session
    }

    override suspend fun getVerificationSession(sessionId: VerificationSessionId): VerificationSession? {
        return verificationSessions[sessionId]
    }

    override suspend fun findNonTerminalVerificationSessions(): List<VerificationSession> {
        return verificationSessions.values.filter {
            it.state != VerificationState.VERIFIED && it.state != VerificationState.REJECTED &&
                    it.state != VerificationState.FAILED && it.state != VerificationState.EXPIRED &&
                    it.state != VerificationState.CANCELLED
        }
    }

    override suspend fun saveVerificationEvidence(evidence: VerificationEvidence) {
        verificationEvidences[evidence.verificationEvidenceId] = evidence
    }

    override suspend fun getVerificationEvidence(evidenceId: VerificationEvidenceId): VerificationEvidence? {
        return verificationEvidences[evidenceId]
    }

    override suspend fun markVerificationNonceConsumed(nonce: String) {
        consumedNonces.add(nonce)
    }

    override suspend fun isVerificationNonceConsumed(nonce: String): Boolean {
        return consumedNonces.contains(nonce)
    }

    override suspend fun appendIdentityHistory(entry: IdentityHistoryEntry) {
        identityHistory.add(entry)
    }

    override suspend fun findIdentityHistory(humanIdentityId: HumanIdentityId): List<IdentityHistoryEntry> {
        return identityHistory.filter { it.humanIdentityId == humanIdentityId }
    }

    override suspend fun listIdentityHistoryForPeer(peerNodeId: NodeId): List<IdentityHistoryEntry> {
        return identityHistory.filter { it.peerNodeId == peerNodeId }
    }

    override suspend fun saveReplacementOperation(operation: DeviceReplacementOperation) {
        replacementOperations[operation.replacementOperationId] = operation
    }

    override suspend fun getReplacementOperation(operationId: ReplacementOperationId): DeviceReplacementOperation? {
        return replacementOperations[operationId]
    }

    override suspend fun saveTrustMigrationPlan(plan: TrustMigrationPlan) {
        migrationPlans[plan.trustMigrationPlanId] = plan
    }

    override suspend fun getTrustMigrationPlan(planId: TrustMigrationPlanId): TrustMigrationPlan? {
        return migrationPlans[planId]
    }

    override suspend fun <T> transaction(block: suspend () -> T): T {
        return block()
    }
}
