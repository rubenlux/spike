package spike.peerdirectory.application

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*
import spike.peerdirectory.storage.PeerIdentityRepository
import spike.security.contracts.LocalPeerSecurityCore

/**
 * Device Replacement Service managing explicit device replacement and grant migration workflows.
 * Preserves the separation between cryptographic identity (NodeId) and human identity (HumanIdentityId).
 */
class DeviceReplacementServiceImpl(
    private val repository: PeerIdentityRepository,
    private val localPeerSecurityCore: LocalPeerSecurityCore
) {
    suspend fun startDeviceReplacement(
        humanIdentityId: HumanIdentityId,
        oldPeerNodeId: NodeId,
        newPeerNodeId: NodeId
    ): DeviceReplacementOperation {
        require(oldPeerNodeId != newPeerNodeId) {
            throw LocalPeerIdentityError.DeviceReplacementError.ReplacementIdentityMismatch("oldPeerNodeId and newPeerNodeId must be different")
        }

        val oldBinding = repository.findActiveBindingByPeer(oldPeerNodeId)
            ?: throw LocalPeerIdentityError.BindingError.BindingNotFound(BindingId("old_binding"))

        if (oldBinding.humanIdentityId != humanIdentityId) {
            throw LocalPeerIdentityError.DeviceReplacementError.ReplacementIdentityMismatch("oldPeerNodeId is bound to a different HumanIdentity")
        }

        val now = System.currentTimeMillis()
        val operation = DeviceReplacementOperation(
            humanIdentityId = humanIdentityId,
            oldPeerNodeId = oldPeerNodeId,
            newPeerNodeId = newPeerNodeId,
            state = DeviceReplacementState.VERIFIED,
            initiatedAt = now
        )

        repository.saveReplacementOperation(operation)

        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                humanIdentityId = humanIdentityId,
                peerNodeId = newPeerNodeId,
                eventType = "DEVICE_REPLACEMENT_STARTED",
                previousState = "OBSERVED",
                newState = "REPLACEMENT_IN_PROGRESS",
                reason = "Replacement for old device ${oldPeerNodeId.value}",
                timestamp = now,
                relatedReplacementOperationId = operation.replacementOperationId
            )
        )

        return operation
    }

    suspend fun createTrustMigrationPlan(
        replacementOperationId: ReplacementOperationId
    ): TrustMigrationPlan {
        val op = repository.getReplacementOperation(replacementOperationId)
            ?: throw LocalPeerIdentityError.DeviceReplacementError.ReplacementOperationNotFound(replacementOperationId)

        val now = System.currentTimeMillis()
        val plan = TrustMigrationPlan(
            oldPeerNodeId = op.oldPeerNodeId,
            newPeerNodeId = op.newPeerNodeId,
            sourceTrustState = "TRUSTED",
            proposedTrustState = "TRUSTED",
            sourceGrantIds = emptyList(),
            migratableGrantIds = emptyList(),
            nonMigratableGrantIds = emptyList(),
            selectedGrantIds = emptyList(),
            oldDevicePolicy = OldDevicePolicy.REMOVE_TRUST,
            createdAt = now
        )

        repository.saveTrustMigrationPlan(plan)

        val updatedOp = op.copy(
            trustMigrationPlanId = plan.trustMigrationPlanId,
            state = DeviceReplacementState.TRUST_MIGRATION_REVIEW
        )
        repository.saveReplacementOperation(updatedOp)

        return plan
    }

    suspend fun confirmTrustMigration(
        planId: TrustMigrationPlanId,
        selectedGrantIds: List<String>
    ): TrustMigrationPlan {
        val plan = repository.getTrustMigrationPlan(planId)
            ?: throw LocalPeerIdentityError.TrustMigrationError.TrustMigrationPlanNotFound(planId)

        val now = System.currentTimeMillis()
        val confirmedPlan = plan.copy(
            selectedGrantIds = selectedGrantIds,
            confirmedAt = now,
            state = "CONFIRMED"
        )

        repository.saveTrustMigrationPlan(confirmedPlan)
        return confirmedPlan
    }

    suspend fun completeDeviceReplacement(
        replacementOperationId: ReplacementOperationId
    ): DeviceReplacementOperation {
        val op = repository.getReplacementOperation(replacementOperationId)
            ?: throw LocalPeerIdentityError.DeviceReplacementError.ReplacementOperationNotFound(replacementOperationId)

        val oldBinding = repository.findActiveBindingByPeer(op.oldPeerNodeId)
            ?: throw LocalPeerIdentityError.BindingError.BindingNotFound(BindingId("old_binding"))

        val now = System.currentTimeMillis()
        val newBinding = HumanNodeBinding(
            humanIdentityId = op.humanIdentityId,
            peerNodeId = op.newPeerNodeId,
            state = BindingState.VERIFIED,
            trustEstablishmentMode = TrustEstablishmentMode.VERIFIED_MANUAL_FINGERPRINT,
            createdAt = now,
            confirmedAt = now
        )
        repository.saveBinding(newBinding)

        val replacedOldBinding = oldBinding.copy(
            state = BindingState.REPLACED,
            replacedByBindingId = newBinding.bindingId,
            revokedAt = now,
            revocationReason = "REPLACED_BY_NEW_DEVICE"
        )
        repository.saveBinding(replacedOldBinding)

        // Apply chosen policy to old device via SPEC-009 API
        localPeerSecurityCore.removeTrust(op.oldPeerNodeId, "REPLACED_BY_NEW_DEVICE")

        // Upgrade trust for new device via SPEC-009 API
        localPeerSecurityCore.trustPeer(op.newPeerNodeId)

        val completedOp = op.copy(
            state = DeviceReplacementState.COMPLETED,
            completedAt = now
        )
        repository.saveReplacementOperation(completedOp)

        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                humanIdentityId = op.humanIdentityId,
                peerNodeId = op.newPeerNodeId,
                bindingId = newBinding.bindingId,
                eventType = "DEVICE_REPLACEMENT_CONFIRMED",
                previousState = "REPLACEMENT_IN_PROGRESS",
                newState = "VERIFIED",
                reason = "Replacement completed from old device ${op.oldPeerNodeId.value}",
                timestamp = now,
                relatedReplacementOperationId = op.replacementOperationId
            )
        )

        return completedOp
    }
}
