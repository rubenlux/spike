package spike.security.domain

/**
 * Pure domain state machine enforcing valid TrustState transitions per SPEC-009.
 */
object TrustStateMachine {

    fun validateTransition(current: TrustState, target: TrustState) {
        if (current == target) return // No-op transition is allowed

        val isValid = when (current) {
            TrustState.UNKNOWN -> target == TrustState.OBSERVED
            TrustState.OBSERVED -> target in setOf(
                TrustState.PENDING_APPROVAL,
                TrustState.TRUSTED,
                TrustState.RESTRICTED,
                TrustState.BLOCKED
            )
            TrustState.PENDING_APPROVAL -> target in setOf(
                TrustState.TRUSTED,
                TrustState.RESTRICTED,
                TrustState.BLOCKED
            )
            TrustState.TRUSTED -> target in setOf(
                TrustState.RESTRICTED,
                TrustState.OBSERVED,
                TrustState.BLOCKED
            )
            TrustState.RESTRICTED -> target in setOf(
                TrustState.TRUSTED,
                TrustState.OBSERVED,
                TrustState.BLOCKED
            )
            TrustState.BLOCKED -> target == TrustState.OBSERVED
        }

        if (!isValid) {
            throw LocalPeerSecurityError.PeerSecurityError.InvalidTrustTransition(current, target)
        }
    }

    fun transition(
        profile: PeerSecurityProfile,
        targetState: TrustState,
        nowEpochMs: Long
    ): PeerSecurityProfile {
        validateTransition(profile.trustState, targetState)

        val newTrustVersion = profile.trustVersion.next()
        val newRevocationVersion = if (targetState == TrustState.BLOCKED || profile.trustState == TrustState.TRUSTED && targetState != TrustState.TRUSTED) {
            profile.revocationVersion.next()
        } else {
            profile.revocationVersion
        }

        val trustedAt = if (targetState == TrustState.TRUSTED) nowEpochMs else profile.trustedAt
        val blockedAt = if (targetState == TrustState.BLOCKED) nowEpochMs else if (targetState == TrustState.OBSERVED && profile.trustState == TrustState.BLOCKED) null else profile.blockedAt

        return profile.copy(
            trustState = targetState,
            trustVersion = newTrustVersion,
            revocationVersion = newRevocationVersion,
            lastSeenAt = nowEpochMs,
            trustedAt = trustedAt,
            blockedAt = blockedAt,
            updatedAt = nowEpochMs
        )
    }
}
