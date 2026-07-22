package spike.peerdirectory.domain

import spike.identity.domain.NodeId

data class PeerDirectoryEntry(
    val peerNodeId: NodeId,
    val localAlias: LocalAlias? = null,
    val advertisedDisplayName: String? = null,
    val advertisedDeviceName: String? = null,
    val advertisedApplicationMetadata: Map<String, String> = emptyMap(),
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastAuthenticatedAt: Long? = null,
    val encounterCount: Long = 1L,
    val verificationStatus: VerificationResult? = null,
    val currentBindingId: BindingId? = null,
    val trustStateSnapshot: String = "OBSERVED",
    val identityVersion: Long = 1L,
    val createdAt: Long,
    val updatedAt: Long
)

data class HumanIdentity(
    val humanIdentityId: HumanIdentityId = HumanIdentityId.generate(),
    val localDisplayName: String,
    val localNotes: String? = null,
    val state: HumanIdentityState = HumanIdentityState.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long
)

data class HumanNodeBinding(
    val bindingId: BindingId = BindingId.generate(),
    val humanIdentityId: HumanIdentityId,
    val peerNodeId: NodeId,
    val state: BindingState = BindingState.PENDING,
    val verificationEvidenceIds: List<VerificationEvidenceId> = emptyList(),
    val trustEstablishmentMode: TrustEstablishmentMode,
    val createdAt: Long,
    val confirmedAt: Long? = null,
    val revokedAt: Long? = null,
    val revocationReason: String? = null,
    val replacedByBindingId: BindingId? = null,
    val bindingVersion: Long = 1L
)

data class VerificationSession(
    val verificationSessionId: VerificationSessionId = VerificationSessionId.generate(),
    val localNodeId: NodeId,
    val remoteNodeId: NodeId,
    val method: VerificationMethod,
    val state: VerificationState = VerificationState.CREATED,
    val localNonce: String,
    val remoteNonce: String? = null,
    val sessionBindingHash: String? = null,
    val startedAt: Long,
    val expiresAt: Long,
    val completedAt: Long? = null,
    val failedAt: Long? = null,
    val failureReason: String? = null,
    val encounterId: String? = null,
    val secureSessionId: String? = null
)

data class VerificationEvidence(
    val verificationEvidenceId: VerificationEvidenceId = VerificationEvidenceId.generate(),
    val verificationSessionId: VerificationSessionId,
    val localNodeId: NodeId,
    val remoteNodeId: NodeId,
    val method: VerificationMethod,
    val result: VerificationResult,
    val sessionBindingHash: String,
    val transcriptHash: VerificationTranscriptHash,
    val identityFingerprint: ReadableFingerprint,
    val verifiedAt: Long,
    val evidenceVersion: Long = 1L
)

data class IdentityHistoryEntry(
    val historyEntryId: HistoryEntryId = HistoryEntryId.generate(),
    val humanIdentityId: HumanIdentityId? = null,
    val peerNodeId: NodeId,
    val bindingId: BindingId? = null,
    val eventType: String,
    val previousState: String? = null,
    val newState: String,
    val reason: String? = null,
    val timestamp: Long,
    val relatedEvidenceId: VerificationEvidenceId? = null,
    val relatedReplacementOperationId: ReplacementOperationId? = null
)

data class DeviceReplacementOperation(
    val replacementOperationId: ReplacementOperationId = ReplacementOperationId.generate(),
    val humanIdentityId: HumanIdentityId,
    val oldPeerNodeId: NodeId,
    val newPeerNodeId: NodeId,
    val state: DeviceReplacementState = DeviceReplacementState.CREATED,
    val initiatedAt: Long,
    val confirmedAt: Long? = null,
    val completedAt: Long? = null,
    val verificationEvidenceIds: List<VerificationEvidenceId> = emptyList(),
    val trustMigrationPlanId: TrustMigrationPlanId? = null,
    val reason: String? = null
)

data class TrustMigrationPlan(
    val trustMigrationPlanId: TrustMigrationPlanId = TrustMigrationPlanId.generate(),
    val oldPeerNodeId: NodeId,
    val newPeerNodeId: NodeId,
    val sourceTrustState: String = "TRUSTED",
    val proposedTrustState: String = "TRUSTED",
    val sourceGrantIds: List<String> = emptyList(),
    val migratableGrantIds: List<String> = emptyList(),
    val nonMigratableGrantIds: List<String> = emptyList(),
    val selectedGrantIds: List<String> = emptyList(),
    val oldDevicePolicy: OldDevicePolicy = OldDevicePolicy.REMOVE_TRUST,
    val createdAt: Long,
    val confirmedAt: Long? = null,
    val appliedAt: Long? = null,
    val state: String = "PROPOSED"
)

data class QrVerificationPayload(
    val protocolVersion: String = "1.0",
    val verificationSessionId: VerificationSessionId,
    val presentingNodeId: NodeId,
    val expectedRemoteNodeId: NodeId,
    val sessionBindingHash: String,
    val identityFingerprint: ReadableFingerprint,
    val verificationNonce: String,
    val expiresAt: Long
)
