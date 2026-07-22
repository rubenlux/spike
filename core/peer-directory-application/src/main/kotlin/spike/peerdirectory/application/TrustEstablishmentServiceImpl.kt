package spike.peerdirectory.application

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*
import spike.peerdirectory.storage.PeerIdentityRepository
import spike.security.contracts.LocalPeerSecurityCore

/**
 * Trust Establishment Service implementing evidence-based trust and explicit overrides.
 * Strictly delegates security trust state transitions to SPEC-009's LocalPeerSecurityCore.
 */
class TrustEstablishmentServiceImpl(
    private val repository: PeerIdentityRepository,
    private val localPeerSecurityCore: LocalPeerSecurityCore
) {
    suspend fun establishTrust(
        humanIdentityId: HumanIdentityId,
        peerNodeId: NodeId,
        evidenceId: VerificationEvidenceId
    ): HumanNodeBinding {
        val evidence = repository.getVerificationEvidence(evidenceId)
            ?: throw LocalPeerIdentityError.TrustEstablishmentError.TrustEvidenceInvalid(evidenceId)

        if (evidence.remoteNodeId != peerNodeId) {
            throw LocalPeerIdentityError.TrustEstablishmentError.TrustEvidenceInvalid(evidenceId)
        }
        if (evidence.result != VerificationResult.VERIFIED) {
            throw LocalPeerIdentityError.TrustEstablishmentError.TrustEvidenceInvalid(evidenceId)
        }

        val existingBinding = repository.findActiveBindingByPeer(peerNodeId)
        if (existingBinding != null && existingBinding.humanIdentityId != humanIdentityId) {
            throw LocalPeerIdentityError.BindingError.PeerAlreadyActivelyBound(peerNodeId, existingBinding.bindingId)
        }

        val mode = when (evidence.method) {
            VerificationMethod.QR -> TrustEstablishmentMode.VERIFIED_QR
            VerificationMethod.SAS -> TrustEstablishmentMode.VERIFIED_SAS
            VerificationMethod.MANUAL_FINGERPRINT -> TrustEstablishmentMode.VERIFIED_MANUAL_FINGERPRINT
            else -> TrustEstablishmentMode.VERIFIED_MANUAL_FINGERPRINT
        }

        val now = System.currentTimeMillis()
        val binding = HumanNodeBinding(
            humanIdentityId = humanIdentityId,
            peerNodeId = peerNodeId,
            state = BindingState.VERIFIED,
            verificationEvidenceIds = listOf(evidenceId),
            trustEstablishmentMode = mode,
            createdAt = now,
            confirmedAt = now
        )

        repository.saveBinding(binding)

        // Delegate SPEC-009 security trust transition strictly via public API
        localPeerSecurityCore.trustPeer(peerNodeId)

        // Update directory entry status
        repository.getPeerDirectoryEntry(peerNodeId)?.let { entry ->
            repository.savePeerDirectoryEntry(
                entry.copy(
                    verificationStatus = VerificationResult.VERIFIED,
                    currentBindingId = binding.bindingId,
                    trustStateSnapshot = "TRUSTED",
                    updatedAt = now
                )
            )
        }

        // Record identity history
        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                humanIdentityId = humanIdentityId,
                peerNodeId = peerNodeId,
                bindingId = binding.bindingId,
                eventType = "TRUST_ESTABLISHED",
                previousState = "OBSERVED",
                newState = "TRUSTED",
                reason = "VERIFIED_VIA_${evidence.method.name}",
                timestamp = now,
                relatedEvidenceId = evidenceId
            )
        )

        return binding
    }

    suspend fun establishUnverifiedTrustOverride(
        humanIdentityId: HumanIdentityId,
        peerNodeId: NodeId,
        confirmationMessage: String
    ): HumanNodeBinding {
        require(confirmationMessage.isNotBlank()) {
            throw LocalPeerIdentityError.TrustEstablishmentError.TrustOverrideConfirmationMissing(peerNodeId)
        }

        val existingBinding = repository.findActiveBindingByPeer(peerNodeId)
        if (existingBinding != null && existingBinding.humanIdentityId != humanIdentityId) {
            throw LocalPeerIdentityError.BindingError.PeerAlreadyActivelyBound(peerNodeId, existingBinding.bindingId)
        }

        val now = System.currentTimeMillis()
        val binding = HumanNodeBinding(
            humanIdentityId = humanIdentityId,
            peerNodeId = peerNodeId,
            state = BindingState.TRUSTED_UNVERIFIED,
            trustEstablishmentMode = TrustEstablishmentMode.UNVERIFIED_TRUST_OVERRIDE,
            createdAt = now,
            confirmedAt = now
        )

        repository.saveBinding(binding)

        // Delegate SPEC-009 security trust transition strictly via public API
        localPeerSecurityCore.trustPeer(peerNodeId)

        repository.getPeerDirectoryEntry(peerNodeId)?.let { entry ->
            repository.savePeerDirectoryEntry(
                entry.copy(
                    currentBindingId = binding.bindingId,
                    trustStateSnapshot = "TRUSTED",
                    updatedAt = now
                )
            )
        }

        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                humanIdentityId = humanIdentityId,
                peerNodeId = peerNodeId,
                bindingId = binding.bindingId,
                eventType = "TRUST_OVERRIDE_CREATED",
                previousState = "OBSERVED",
                newState = "TRUSTED_UNVERIFIED",
                reason = confirmationMessage,
                timestamp = now
            )
        )

        return binding
    }

    suspend fun revokeBinding(bindingId: BindingId, reason: String): HumanNodeBinding {
        val binding = repository.getBinding(bindingId)
            ?: throw LocalPeerIdentityError.BindingError.BindingNotFound(bindingId)

        val now = System.currentTimeMillis()
        val revokedBinding = binding.copy(
            state = BindingState.REVOKED,
            revokedAt = now,
            revocationReason = reason,
            bindingVersion = binding.bindingVersion + 1
        )

        repository.saveBinding(revokedBinding)

        // Delegate SPEC-009 security trust state downgrade strictly via public API
        localPeerSecurityCore.removeTrust(binding.peerNodeId, reason)

        repository.getPeerDirectoryEntry(binding.peerNodeId)?.let { entry ->
            repository.savePeerDirectoryEntry(
                entry.copy(
                    currentBindingId = null,
                    trustStateSnapshot = "OBSERVED",
                    updatedAt = now
                )
            )
        }

        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                humanIdentityId = binding.humanIdentityId,
                peerNodeId = binding.peerNodeId,
                bindingId = bindingId,
                eventType = "BINDING_REVOKED",
                previousState = binding.state.name,
                newState = BindingState.REVOKED.name,
                reason = reason,
                timestamp = now
            )
        )

        return revokedBinding
    }
}
