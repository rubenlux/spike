package spike.peerdirectory.contracts

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*

/**
 * Normative Public Administrative Facade Contract for Local Peer Directory & Identity Binding per SPEC-010 Section 98.
 */
interface LocalPeerDirectoryCore {
    suspend fun observeAuthenticatedPeer(
        peerNodeId: NodeId,
        advertisedMetadata: Map<String, String> = emptyMap()
    ): PeerDirectoryEntry

    suspend fun getPeer(peerNodeId: NodeId): PeerDirectoryEntry?

    suspend fun listPeers(
        aliasFilter: LocalAlias? = null,
        humanIdentityId: HumanIdentityId? = null,
        verificationStatus: VerificationResult? = null
    ): List<PeerDirectoryEntry>

    suspend fun setLocalAlias(peerNodeId: NodeId, alias: LocalAlias?): PeerDirectoryEntry

    suspend fun createHumanIdentity(
        localDisplayName: String,
        localNotes: String? = null
    ): HumanIdentity

    suspend fun updateHumanIdentity(
        humanIdentityId: HumanIdentityId,
        localDisplayName: String? = null,
        localNotes: String? = null
    ): HumanIdentity

    suspend fun archiveHumanIdentity(humanIdentityId: HumanIdentityId): HumanIdentity

    suspend fun bindPeerToHumanIdentity(
        humanIdentityId: HumanIdentityId,
        peerNodeId: NodeId,
        evidenceId: VerificationEvidenceId
    ): HumanNodeBinding

    suspend fun revokeBinding(bindingId: BindingId, reason: String): HumanNodeBinding

    suspend fun getIdentityHistory(humanIdentityId: HumanIdentityId): List<IdentityHistoryEntry>

    suspend fun getReadableFingerprint(peerNodeId: NodeId): ReadableFingerprint

    suspend fun startVerification(peerNodeId: NodeId, method: VerificationMethod): VerificationSession

    suspend fun getVerificationSession(sessionId: VerificationSessionId): VerificationSession?

    suspend fun getQrVerificationPayload(sessionId: VerificationSessionId): QrVerificationPayload

    suspend fun submitQrVerificationPayload(
        sessionId: VerificationSessionId,
        payload: QrVerificationPayload
    ): VerificationEvidence

    suspend fun getSas(sessionId: VerificationSessionId): ShortAuthenticationString

    suspend fun confirmSasMatch(sessionId: VerificationSessionId): VerificationEvidence

    suspend fun rejectSas(sessionId: VerificationSessionId)

    suspend fun confirmManualFingerprint(sessionId: VerificationSessionId): VerificationEvidence

    suspend fun cancelVerification(sessionId: VerificationSessionId)

    suspend fun establishTrust(
        peerNodeId: NodeId,
        evidenceId: VerificationEvidenceId
    ): HumanNodeBinding

    suspend fun establishUnverifiedTrustOverride(
        peerNodeId: NodeId,
        confirmationMessage: String
    ): HumanNodeBinding

    suspend fun startDeviceReplacement(
        humanIdentityId: HumanIdentityId,
        oldPeerNodeId: NodeId,
        newPeerNodeId: NodeId
    ): DeviceReplacementOperation

    suspend fun createTrustMigrationPlan(replacementOperationId: ReplacementOperationId): TrustMigrationPlan

    suspend fun confirmTrustMigration(
        planId: TrustMigrationPlanId,
        selectedGrantIds: List<String>
    ): TrustMigrationPlan

    suspend fun completeDeviceReplacement(replacementOperationId: ReplacementOperationId): DeviceReplacementOperation

    suspend fun recover()

    fun subscribeIdentityEvents(listener: PeerDirectoryEventListener)
}
