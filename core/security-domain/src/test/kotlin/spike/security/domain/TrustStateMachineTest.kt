package spike.security.domain

import spike.identity.domain.NodeId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TrustStateMachineTest {

    private val testNodeId = NodeId("node:v1:sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")

    @Test
    fun `valid transition UNKNOWN to OBSERVED succeeds`() {
        val profile = createProfile(TrustState.UNKNOWN)
        val updated = TrustStateMachine.transition(profile, TrustState.OBSERVED, 1000L)
        assertEquals(TrustState.OBSERVED, updated.trustState)
        assertEquals(2L, updated.trustVersion.value)
    }

    @Test
    fun `valid transitions from OBSERVED succeed`() {
        val profile = createProfile(TrustState.OBSERVED)
        
        val trusted = TrustStateMachine.transition(profile, TrustState.TRUSTED, 1000L)
        assertEquals(TrustState.TRUSTED, trusted.trustState)
        assertEquals(1000L, trusted.trustedAt)

        val pending = TrustStateMachine.transition(profile, TrustState.PENDING_APPROVAL, 1000L)
        assertEquals(TrustState.PENDING_APPROVAL, pending.trustState)

        val restricted = TrustStateMachine.transition(profile, TrustState.RESTRICTED, 1000L)
        assertEquals(TrustState.RESTRICTED, restricted.trustState)

        val blocked = TrustStateMachine.transition(profile, TrustState.BLOCKED, 1000L)
        assertEquals(TrustState.BLOCKED, blocked.trustState)
        assertEquals(1000L, blocked.blockedAt)
        assertEquals(2L, blocked.revocationVersion.value)
    }

    @Test
    fun `unblocking BLOCKED peer transitions to OBSERVED`() {
        val profile = createProfile(TrustState.BLOCKED).copy(blockedAt = 500L)
        val unblocked = TrustStateMachine.transition(profile, TrustState.OBSERVED, 1000L)
        assertEquals(TrustState.OBSERVED, unblocked.trustState)
        assertNull(unblocked.blockedAt)
    }

    @Test
    fun `invalid transition throws InvalidTrustTransition`() {
        val unknownProfile = createProfile(TrustState.UNKNOWN)
        assertThrows<LocalPeerSecurityError.PeerSecurityError.InvalidTrustTransition> {
            TrustStateMachine.transition(unknownProfile, TrustState.TRUSTED, 1000L)
        }

        val blockedProfile = createProfile(TrustState.BLOCKED)
        assertThrows<LocalPeerSecurityError.PeerSecurityError.InvalidTrustTransition> {
            TrustStateMachine.transition(blockedProfile, TrustState.TRUSTED, 1000L)
        }
    }

    private fun createProfile(state: TrustState): PeerSecurityProfile {
        return PeerSecurityProfile(
            peerNodeId = testNodeId,
            trustState = state,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }
}
