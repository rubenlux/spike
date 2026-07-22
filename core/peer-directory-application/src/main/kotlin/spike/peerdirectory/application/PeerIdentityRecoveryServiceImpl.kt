package spike.peerdirectory.application

import spike.peerdirectory.domain.*
import spike.peerdirectory.storage.PeerIdentityRepository
import spike.security.contracts.LocalPeerSecurityCore

/**
 * Boot Recovery Service for Peer Directory & Identity Subsystem per SPEC-010 Section 96.
 * Recovery invariant: RECOVERY MUST NEVER SILENTLY UPGRADE TRUST.
 */
class PeerIdentityRecoveryServiceImpl(
    private val repository: PeerIdentityRepository,
    private val localPeerSecurityCore: LocalPeerSecurityCore
) {
    suspend fun recover() {
        val now = System.currentTimeMillis()

        // 1. Recover non-terminal verification sessions
        val nonTerminalSessions = repository.findNonTerminalVerificationSessions()
        for (session in nonTerminalSessions) {
            if (now > session.expiresAt) {
                val expiredSession = session.copy(
                    state = VerificationState.EXPIRED,
                    failedAt = now,
                    failureReason = "EXPIRED_DURING_RECOVERY"
                )
                repository.saveVerificationSession(expiredSession)
            }
        }

        // 2. Load all human identities and validate active bindings invariant
        val humanIdentities = repository.listHumanIdentities()
        val peerBindingMap = mutableMapOf<String, HumanNodeBinding>()

        for (humanIdentity in humanIdentities) {
            val bindings = repository.findBindingsByHumanIdentity(humanIdentity.humanIdentityId)
            val activeBindings = bindings.filter { it.state == BindingState.VERIFIED || it.state == BindingState.TRUSTED_UNVERIFIED }

            for (binding in activeBindings) {
                val peerKey = binding.peerNodeId.value
                val existing = peerBindingMap[peerKey]
                if (existing != null) {
                    throw LocalPeerIdentityError.RecoveryError.PeerIdentityStateInconsistency(
                        "Peer $peerKey has multiple active bindings: ${existing.bindingId.value} and ${binding.bindingId.value}"
                    )
                }
                peerBindingMap[peerKey] = binding
            }
        }

        // 3. Record recovery audit log event
        repository.appendIdentityHistory(
            IdentityHistoryEntry(
                peerNodeId = spike.identity.domain.NodeId("node:v1:sha256:0000000000000000000000000000000000000000000000000000000000000000"),
                eventType = "PEER_IDENTITY_RECOVERY_COMPLETED",
                previousState = "BOOT",
                newState = "RECOVERED",
                reason = "Boot recovery completed successfully",
                timestamp = now
            )
        )
    }
}
