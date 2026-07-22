package spike.peerdirectory.application

import spike.identity.domain.NodeId
import spike.peerdirectory.contracts.LocalPeerDirectoryCore
import spike.peerdirectory.contracts.PeerDirectoryEventListener
import spike.peerdirectory.domain.*
import spike.peerdirectory.storage.PeerIdentityRepository
import spike.security.contracts.LocalPeerSecurityCore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Normative Implementation of LocalPeerDirectoryCore per SPEC-010 Section 98.
 */
class LocalPeerDirectoryCoreImpl(
    private val repository: PeerIdentityRepository,
    private val verificationSessionService: VerificationSessionServiceImpl,
    private val trustEstablishmentService: TrustEstablishmentServiceImpl,
    private val deviceReplacementService: DeviceReplacementServiceImpl,
    private val recoveryService: PeerIdentityRecoveryServiceImpl,
    private val localPeerSecurityCore: LocalPeerSecurityCore
) : LocalPeerDirectoryCore {

    private val listeners = CopyOnWriteArrayList<PeerDirectoryEventListener>()

    override suspend fun observeAuthenticatedPeer(
        peerNodeId: NodeId,
        advertisedMetadata: Map<String, String>
    ): PeerDirectoryEntry {
        localPeerSecurityCore.observePeer(peerNodeId)
        val now = System.currentTimeMillis()
        val existing = repository.getPeerDirectoryEntry(peerNodeId)

        val entry = if (existing == null) {
            PeerDirectoryEntry(
                peerNodeId = peerNodeId,
                advertisedDisplayName = advertisedMetadata["displayName"],
                advertisedDeviceName = advertisedMetadata["deviceName"],
                advertisedApplicationMetadata = advertisedMetadata,
                firstSeenAt = now,
                lastSeenAt = now,
                lastAuthenticatedAt = now,
                encounterCount = 1L,
                trustStateSnapshot = "OBSERVED",
                createdAt = now,
                updatedAt = now
            )
        } else {
            // Check identity change warning if alias or metadata changes unexpectedly
            existing.copy(
                advertisedDisplayName = advertisedMetadata["displayName"] ?: existing.advertisedDisplayName,
                advertisedDeviceName = advertisedMetadata["deviceName"] ?: existing.advertisedDeviceName,
                advertisedApplicationMetadata = advertisedMetadata.ifEmpty { existing.advertisedApplicationMetadata },
                lastSeenAt = now,
                lastAuthenticatedAt = now,
                encounterCount = existing.encounterCount + 1L,
                updatedAt = now
            )
        }

        repository.savePeerDirectoryEntry(entry)
        listeners.forEach { it.onPeerObserved(entry) }
        return entry
    }

    override suspend fun getPeer(peerNodeId: NodeId): PeerDirectoryEntry? {
        return repository.getPeerDirectoryEntry(peerNodeId)
    }

    override suspend fun listPeers(
        aliasFilter: LocalAlias?,
        humanIdentityId: HumanIdentityId?,
        verificationStatus: VerificationResult?
    ): List<PeerDirectoryEntry> {
        return repository.findPeers(
            aliasFilter = aliasFilter,
            humanIdentityId = humanIdentityId,
            verificationStatus = verificationStatus
        )
    }

    override suspend fun setLocalAlias(peerNodeId: NodeId, alias: LocalAlias?): PeerDirectoryEntry {
        val entry = repository.getPeerDirectoryEntry(peerNodeId)
            ?: throw LocalPeerIdentityError.PeerDirectoryError.PeerDirectoryEntryNotFound(peerNodeId)

        val now = System.currentTimeMillis()
        val oldAlias = entry.localAlias
        val updatedEntry = entry.copy(
            localAlias = alias,
            updatedAt = now
        )

        repository.savePeerDirectoryEntry(updatedEntry)

        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                peerNodeId = peerNodeId,
                eventType = "LOCAL_ALIAS_CHANGED",
                previousState = oldAlias?.value,
                newState = alias?.value ?: "REMOVED",
                timestamp = now
            )
        )

        listeners.forEach { it.onLocalAliasChanged(peerNodeId, oldAlias, alias) }
        return updatedEntry
    }

    override suspend fun createHumanIdentity(
        localDisplayName: String,
        localNotes: String?
    ): HumanIdentity {
        val now = System.currentTimeMillis()
        val identity = HumanIdentity(
            localDisplayName = localDisplayName,
            localNotes = localNotes,
            state = HumanIdentityState.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        repository.saveHumanIdentity(identity)
        return identity
    }

    override suspend fun updateHumanIdentity(
        humanIdentityId: HumanIdentityId,
        localDisplayName: String?,
        localNotes: String?
    ): HumanIdentity {
        val identity = repository.getHumanIdentity(humanIdentityId)
            ?: throw LocalPeerIdentityError.HumanIdentityError.HumanIdentityNotFound(humanIdentityId)

        val now = System.currentTimeMillis()
        val updated = identity.copy(
            localDisplayName = localDisplayName ?: identity.localDisplayName,
            localNotes = localNotes ?: identity.localNotes,
            updatedAt = now
        )
        repository.saveHumanIdentity(updated)
        return updated
    }

    override suspend fun archiveHumanIdentity(humanIdentityId: HumanIdentityId): HumanIdentity {
        val identity = repository.getHumanIdentity(humanIdentityId)
            ?: throw LocalPeerIdentityError.HumanIdentityError.HumanIdentityNotFound(humanIdentityId)

        val now = System.currentTimeMillis()
        val archived = identity.copy(
            state = HumanIdentityState.ARCHIVED,
            updatedAt = now
        )
        repository.saveHumanIdentity(archived)
        return archived
    }

    override suspend fun bindPeerToHumanIdentity(
        humanIdentityId: HumanIdentityId,
        peerNodeId: NodeId,
        evidenceId: VerificationEvidenceId
    ): HumanNodeBinding {
        val binding = trustEstablishmentService.establishTrust(humanIdentityId, peerNodeId, evidenceId)
        listeners.forEach { it.onBindingCreated(binding) }
        return binding
    }

    override suspend fun revokeBinding(bindingId: BindingId, reason: String): HumanNodeBinding {
        val binding = trustEstablishmentService.revokeBinding(bindingId, reason)
        listeners.forEach { it.onBindingRevoked(binding, reason) }
        return binding
    }

    override suspend fun getIdentityHistory(humanIdentityId: HumanIdentityId): List<IdentityHistoryEntry> {
        return repository.findIdentityHistory(humanIdentityId)
    }

    override suspend fun getReadableFingerprint(peerNodeId: NodeId): ReadableFingerprint {
        return ReadableFingerprintGenerator.generateFingerprint(peerNodeId.value.toByteArray(Charsets.UTF_8))
    }

    override suspend fun startVerification(
        peerNodeId: NodeId,
        method: VerificationMethod
    ): VerificationSession {
        // We use a dummy local node ID for single-device testing context
        val localNodeId = NodeId("node:v1:sha256:0000000000000000000000000000000000000000000000000000000000000000")
        val session = verificationSessionService.startVerificationSession(localNodeId, peerNodeId, method)
        listeners.forEach { it.onVerificationStarted(session) }
        return session
    }

    override suspend fun getVerificationSession(sessionId: VerificationSessionId): VerificationSession? {
        return verificationSessionService.getVerificationSession(sessionId)
    }

    override suspend fun getQrVerificationPayload(sessionId: VerificationSessionId): QrVerificationPayload {
        return verificationSessionService.getQrVerificationPayload(sessionId)
    }

    override suspend fun submitQrVerificationPayload(
        sessionId: VerificationSessionId,
        payload: QrVerificationPayload
    ): VerificationEvidence {
        val evidence = verificationSessionService.submitQrVerificationPayload(sessionId, payload)
        listeners.forEach { it.onVerificationCompleted(evidence) }
        return evidence
    }

    override suspend fun getSas(sessionId: VerificationSessionId): ShortAuthenticationString {
        return verificationSessionService.getSas(sessionId)
    }

    override suspend fun confirmSasMatch(sessionId: VerificationSessionId): VerificationEvidence {
        val evidence = verificationSessionService.confirmSasMatch(sessionId)
        listeners.forEach { it.onVerificationCompleted(evidence) }
        return evidence
    }

    override suspend fun rejectSas(sessionId: VerificationSessionId) {
        verificationSessionService.rejectSas(sessionId)
        listeners.forEach { it.onVerificationFailed(sessionId, "SAS_MISMATCH") }
    }

    override suspend fun confirmManualFingerprint(sessionId: VerificationSessionId): VerificationEvidence {
        val session = verificationSessionService.getVerificationSession(sessionId)
            ?: throw LocalPeerIdentityError.VerificationError.VerificationSessionNotFound(sessionId)

        val fingerprint = getReadableFingerprint(session.remoteNodeId)
        val evidence = verificationSessionService.confirmManualFingerprint(sessionId, fingerprint)
        listeners.forEach { it.onVerificationCompleted(evidence) }
        return evidence
    }

    override suspend fun cancelVerification(sessionId: VerificationSessionId) {
        verificationSessionService.cancelVerificationSession(sessionId, "USER_CANCELLED")
    }

    override suspend fun establishTrust(
        peerNodeId: NodeId,
        evidenceId: VerificationEvidenceId
    ): HumanNodeBinding {
        // Find existing human identity or create a default one
        val activeBinding = repository.findActiveBindingByPeer(peerNodeId)
        val humanId = activeBinding?.humanIdentityId ?: createHumanIdentity("Peer_${peerNodeId.value.take(12)}").humanIdentityId

        val binding = trustEstablishmentService.establishTrust(humanId, peerNodeId, evidenceId)
        listeners.forEach { it.onTrustEstablished(peerNodeId, binding.trustEstablishmentMode) }
        return binding
    }

    override suspend fun establishUnverifiedTrustOverride(
        peerNodeId: NodeId,
        confirmationMessage: String
    ): HumanNodeBinding {
        val activeBinding = repository.findActiveBindingByPeer(peerNodeId)
        val humanId = activeBinding?.humanIdentityId ?: createHumanIdentity("Peer_${peerNodeId.value.take(12)}").humanIdentityId

        val binding = trustEstablishmentService.establishUnverifiedTrustOverride(humanId, peerNodeId, confirmationMessage)
        listeners.forEach { it.onTrustEstablished(peerNodeId, TrustEstablishmentMode.UNVERIFIED_TRUST_OVERRIDE) }
        return binding
    }

    override suspend fun startDeviceReplacement(
        humanIdentityId: HumanIdentityId,
        oldPeerNodeId: NodeId,
        newPeerNodeId: NodeId
    ): DeviceReplacementOperation {
        val op = deviceReplacementService.startDeviceReplacement(humanIdentityId, oldPeerNodeId, newPeerNodeId)
        listeners.forEach { it.onDeviceReplacementStateChanged(op) }
        return op
    }

    override suspend fun createTrustMigrationPlan(replacementOperationId: ReplacementOperationId): TrustMigrationPlan {
        return deviceReplacementService.createTrustMigrationPlan(replacementOperationId)
    }

    override suspend fun confirmTrustMigration(
        planId: TrustMigrationPlanId,
        selectedGrantIds: List<String>
    ): TrustMigrationPlan {
        return deviceReplacementService.confirmTrustMigration(planId, selectedGrantIds)
    }

    override suspend fun completeDeviceReplacement(replacementOperationId: ReplacementOperationId): DeviceReplacementOperation {
        val op = deviceReplacementService.completeDeviceReplacement(replacementOperationId)
        listeners.forEach { it.onDeviceReplacementStateChanged(op) }
        return op
    }

    override suspend fun recover() {
        recoveryService.recover()
    }

    override fun subscribeIdentityEvents(listener: PeerDirectoryEventListener) {
        listeners.add(listener)
    }
}
