package spike.identity.domain

class IdentityStateMachine {
    private val allowed = mapOf(
        LocalIdentityState.CREATING to setOf(
            LocalIdentityState.ACTIVE,
            LocalIdentityState.INVALID,
            LocalIdentityState.KEY_UNAVAILABLE
        ),
        LocalIdentityState.ACTIVE to setOf(
            LocalIdentityState.INVALID,
            LocalIdentityState.KEY_UNAVAILABLE,
            LocalIdentityState.COMPROMISED,
            LocalIdentityState.RETIRED
        ),
        LocalIdentityState.INVALID to emptySet(),
        LocalIdentityState.KEY_UNAVAILABLE to setOf(LocalIdentityState.ACTIVE, LocalIdentityState.INVALID),
        LocalIdentityState.COMPROMISED to emptySet(),
        LocalIdentityState.RETIRED to emptySet()
    )

    fun validateTransition(from: LocalIdentityState, to: LocalIdentityState) {
        if (from == to) return
        if (to !in allowed.getValue(from)) {
            throw InvalidIdentityStateTransition(from, to)
        }
    }

    fun canSign(state: LocalIdentityState): Boolean = state == LocalIdentityState.ACTIVE
}
