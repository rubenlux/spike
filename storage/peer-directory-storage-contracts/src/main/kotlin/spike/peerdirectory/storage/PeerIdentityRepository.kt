package spike.peerdirectory.storage

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*

/**
 * Pure Persistence Contract for Peer Directory and Identity Binding Storage per SPEC-010 Section 99.
 */
interface PeerIdentityRepository {
    suspend fun savePeerDirectoryEntry(entry: PeerDirectoryEntry)
    suspend fun getPeerDirectoryEntry(peerNodeId: NodeId): PeerDirectoryEntry?
    suspend fun findPeers(
        aliasFilter: LocalAlias? = null,
        humanIdentityId: HumanIdentityId? = null,
        nodeIdPrefix: String? = null,
        verificationStatus: VerificationResult? = null
    ): List<PeerDirectoryEntry>

    suspend fun saveHumanIdentity(identity: HumanIdentity)
    suspend fun getHumanIdentity(humanIdentityId: HumanIdentityId): HumanIdentity?
    suspend fun listHumanIdentities(): List<HumanIdentity>

    suspend fun saveBinding(binding: HumanNodeBinding)
    suspend fun getBinding(bindingId: BindingId): HumanNodeBinding?
    suspend fun findActiveBindingByPeer(peerNodeId: NodeId): HumanNodeBinding?
    suspend fun findBindingsByHumanIdentity(humanIdentityId: HumanIdentityId): List<HumanNodeBinding>

    suspend fun saveVerificationSession(session: VerificationSession)
    suspend fun getVerificationSession(sessionId: VerificationSessionId): VerificationSession?
    suspend fun findNonTerminalVerificationSessions(): List<VerificationSession>

    suspend fun saveVerificationEvidence(evidence: VerificationEvidence)
    suspend fun getVerificationEvidence(evidenceId: VerificationEvidenceId): VerificationEvidence?

    suspend fun markVerificationNonceConsumed(nonce: String)
    suspend fun isVerificationNonceConsumed(nonce: String): Boolean

    suspend fun appendIdentityHistory(entry: IdentityHistoryEntry)
    suspend fun findIdentityHistory(humanIdentityId: HumanIdentityId): List<IdentityHistoryEntry>
    suspend fun listIdentityHistoryForPeer(peerNodeId: NodeId): List<IdentityHistoryEntry>

    suspend fun saveReplacementOperation(operation: DeviceReplacementOperation)
    suspend fun getReplacementOperation(operationId: ReplacementOperationId): DeviceReplacementOperation?

    suspend fun saveTrustMigrationPlan(plan: TrustMigrationPlan)
    suspend fun getTrustMigrationPlan(planId: TrustMigrationPlanId): TrustMigrationPlan?

    suspend fun <T> transaction(block: suspend () -> T): T
}
