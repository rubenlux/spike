package spike.security.testing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import spike.identity.domain.NodeId
import spike.security.domain.*

class SecurityAcceptanceScenariosTest {

    private lateinit var env: TestSecurityEnvironment

    @BeforeEach
    fun setUp() {
        env = SecurityTestFixtures.createDefaultEnvironment()
    }

    @Test
    fun `Scenario A - Authenticated Unknown Peer without grants is DENIED_BY_DEFAULT`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)

        val request = createMessagingRequest(SecurityTestFixtures.peerA)
        val decision = env.core.authorize(request)

        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_NO_MATCHING_GRANT, decision.reasonCode)
    }

    @Test
    fun `Scenario B - Trusted Peer without specific grant is DENIED_NO_MATCHING_GRANT`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)
        env.core.trustPeer(SecurityTestFixtures.peerA)

        val request = createMessagingRequest(SecurityTestFixtures.peerA)
        val decision = env.core.authorize(request)

        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_NO_MATCHING_GRANT, decision.reasonCode)
    }

    @Test
    fun `Scenario C - Explicit DENY Grant Precedence overrides ALLOW grant`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)

        env.core.createGrant(SecurityTestFixtures.peerA, SecurityTestFixtures.messagingReceiveCap, effect = GrantEffect.ALLOW)
        env.core.createGrant(SecurityTestFixtures.peerA, SecurityTestFixtures.messagingReceiveCap, effect = GrantEffect.DENY)

        val decision = env.core.authorize(createMessagingRequest(SecurityTestFixtures.peerA))

        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_EXPLICIT_GRANT, decision.reasonCode)
    }

    @Test
    fun `Scenario D - Expired and Exhausted Grant returns DENIED_GRANT_EXPIRED and DENIED_GRANT_EXHAUSTED`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)

        // Expired grant
        env.core.createGrant(
            peerNodeId = SecurityTestFixtures.peerA,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            validUntil = 500L
        )

        val expiredDecision = env.gateway.authorize(createMessagingRequest(SecurityTestFixtures.peerA))
        assertEquals(AuthorizationEffect.DENY, expiredDecision.effect)
        assertEquals(AuthorizationReason.DENIED_GRANT_EXPIRED, expiredDecision.reasonCode)

        // Exhausted one-shot grant
        env.core.createGrant(
            peerNodeId = SecurityTestFixtures.peerB,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            maxUses = 1L
        )

        val allowedDecision = env.core.authorize(createMessagingRequest(SecurityTestFixtures.peerB))
        assertEquals(AuthorizationEffect.ALLOW, allowedDecision.effect)

        val exhaustedDecision = env.core.authorize(createMessagingRequest(SecurityTestFixtures.peerB))
        assertEquals(AuthorizationEffect.DENY, exhaustedDecision.effect)
        assertEquals(AuthorizationReason.DENIED_GRANT_EXHAUSTED, exhaustedDecision.reasonCode)
    }

    @Test
    fun `Scenario E - Message Type and Payload Size Constraints evaluation`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)

        val constraints = ConstraintSet(
            listOf(
                MessageTypeConstraint(setOf("chat.text")),
                MaximumPayloadBytesConstraint(1024L)
            )
        )
        env.core.createGrant(SecurityTestFixtures.peerA, SecurityTestFixtures.messagingReceiveCap, constraints = constraints)

        // Wrong message type
        val wrongTypeReq = createMessagingRequest(SecurityTestFixtures.peerA).copy(
            context = AuthorizationContext(messageType = "chat.video", payloadBytes = 500L)
        )
        val decisionWrongType = env.core.authorize(wrongTypeReq)
        assertEquals(AuthorizationEffect.DENY, decisionWrongType.effect)
        assertEquals(AuthorizationReason.DENIED_MESSAGE_TYPE, decisionWrongType.reasonCode)

        // Oversized payload
        val oversizedReq = createMessagingRequest(SecurityTestFixtures.peerA).copy(
            context = AuthorizationContext(messageType = "chat.text", payloadBytes = 2048L)
        )
        val decisionOversized = env.core.authorize(oversizedReq)
        assertEquals(AuthorizationEffect.DENY, decisionOversized.effect)
        assertEquals(AuthorizationReason.DENIED_PAYLOAD_SIZE, decisionOversized.reasonCode)

        // Valid context
        val validReq = createMessagingRequest(SecurityTestFixtures.peerA).copy(
            context = AuthorizationContext(messageType = "chat.text", payloadBytes = 500L)
        )
        val validDecision = env.core.authorize(validReq)
        assertEquals(AuthorizationEffect.ALLOW, validDecision.effect)
    }

    @Test
    fun `Scenario F - Object Transfer Authorization and Resource Reservation`() = runBlocking {
        env.core.registerCapability(
            CapabilityDefinition(
                capabilityId = SecurityTestFixtures.objectReceiveCap,
                description = "Receive object transfer",
                resourceType = ResourceType("OBJECT"),
                operationType = OperationType("TRANSFER"),
                allowedConstraintTypes = setOf("MaximumObjectBytesConstraint"),
                supportsReservation = true,
                createdAt = 100L
            )
        )
        env.core.observePeer(SecurityTestFixtures.peerA)
        env.core.createGrant(SecurityTestFixtures.peerA, SecurityTestFixtures.objectReceiveCap)

        val request = AuthorizationRequest(
            peerNodeId = SecurityTestFixtures.peerA,
            capabilityId = SecurityTestFixtures.objectReceiveCap,
            operationType = OperationType("TRANSFER"),
            resourceType = ResourceType("OBJECT"),
            requestedUsage = 5000L,
            requestedAt = 1000L
        )

        val (decision, reservation) = env.core.authorizeAndReserve(request)
        assertEquals(AuthorizationEffect.ALLOW, decision.effect)
        assertNotNull(reservation)
        assertEquals(5000L, reservation?.reservedUsage)

        // Complete operation and release reservation
        env.core.releaseReservation(reservation!!.reservationId)
        val updatedRes = env.repository.getReservation(reservation.reservationId)
        assertEquals(ReservationState.RELEASED, updatedRes?.state)
    }

    @Test
    fun `Scenario G - Rate Limits Enforcement`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)

        val rateLimitConstraint = RateLimitConstraint(
            dimension = UsageDimension.OPERATIONS_PER_MINUTE,
            limit = 2L,
            windowSeconds = 60L
        )
        env.core.createGrant(
            peerNodeId = SecurityTestFixtures.peerA,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            constraints = ConstraintSet(listOf(rateLimitConstraint))
        )

        val req = createMessagingRequest(SecurityTestFixtures.peerA)

        val d1 = env.core.authorize(req)
        assertEquals(AuthorizationEffect.ALLOW, d1.effect)

        val d2 = env.core.authorize(req)
        assertEquals(AuthorizationEffect.ALLOW, d2.effect)

        val d3 = env.core.authorize(req)
        assertEquals(AuthorizationEffect.DENY, d3.effect)
        assertEquals(AuthorizationReason.DENIED_RATE_LIMIT, d3.reasonCode)
    }

    @Test
    fun `Scenario H - Peer Blocking revokes active leases and denies new requests`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)
        val grant = env.core.createGrant(SecurityTestFixtures.peerA, SecurityTestFixtures.messagingReceiveCap)

        val decision = env.core.authorize(createMessagingRequest(SecurityTestFixtures.peerA))
        assertEquals(AuthorizationEffect.ALLOW, decision.effect)

        val now = System.currentTimeMillis()
        val lease = AuthorizationLease(
            decisionId = decision.decisionId,
            peerNodeId = SecurityTestFixtures.peerA,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            grantIds = listOf(grant.grantId),
            policyVersion = PolicyVersion(1L),
            trustVersion = TrustVersion(1L),
            revocationVersion = RevocationVersion(1L),
            state = LeaseState.ACTIVE,
            issuedAt = now,
            expiresAt = now + 60_000L,
            lastValidatedAt = now
        )
        env.repository.saveLease(lease)

        // Block peer
        env.core.blockPeer(SecurityTestFixtures.peerA, "SECURITY_VIOLATION")

        // Active lease should be revoked
        val revokedLease = env.repository.getLease(lease.leaseId)
        assertEquals(LeaseState.REVOKED, revokedLease?.state)

        // Validate lease throws exception
        assertThrows<LocalPeerSecurityError.LeaseError.AuthorizationLeaseRevoked> {
            env.core.validateLease(lease.leaseId)
        }

        // New request denied
        val newDecision = env.core.authorize(createMessagingRequest(SecurityTestFixtures.peerA))
        assertEquals(AuthorizationEffect.DENY, newDecision.effect)
        assertEquals(AuthorizationReason.DENIED_PEER_BLOCKED, newDecision.reasonCode)
    }

    @Test
    fun `Scenario I - Lease Validation and Expiration`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)

        val now = System.currentTimeMillis()
        val validLease = AuthorizationLease(
            decisionId = DecisionId.generate(),
            peerNodeId = SecurityTestFixtures.peerA,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            policyVersion = PolicyVersion(1L),
            trustVersion = TrustVersion(1L),
            revocationVersion = RevocationVersion(1L),
            state = LeaseState.ACTIVE,
            issuedAt = now,
            expiresAt = now + 60_000L,
            lastValidatedAt = now
        )
        env.repository.saveLease(validLease)

        val validatedLease = env.core.validateLease(validLease.leaseId)
        assertEquals(LeaseState.ACTIVE, validatedLease.state)

        val expiredLease = AuthorizationLease(
            decisionId = DecisionId.generate(),
            peerNodeId = SecurityTestFixtures.peerA,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            policyVersion = PolicyVersion(1L),
            trustVersion = TrustVersion(1L),
            revocationVersion = RevocationVersion(1L),
            state = LeaseState.ACTIVE,
            issuedAt = now - 2000L,
            expiresAt = now - 1000L,
            lastValidatedAt = now - 2000L
        )
        env.repository.saveLease(expiredLease)

        assertThrows<LocalPeerSecurityError.LeaseError.AuthorizationLeaseExpired> {
            env.core.validateLease(expiredLease.leaseId)
        }
    }

    @Test
    fun `Scenario J - Explainability API returns detailed explanation`() = runBlocking {
        env.core.registerCapability(createMessagingCap())
        env.core.observePeer(SecurityTestFixtures.peerA)
        val grant = env.core.createGrant(SecurityTestFixtures.peerA, SecurityTestFixtures.messagingReceiveCap)

        val decision = env.core.authorize(createMessagingRequest(SecurityTestFixtures.peerA))
        val explanation = env.core.explainDecision(decision.decisionId)

        assertEquals(decision.decisionId, explanation.decisionId)
        assertEquals(AuthorizationEffect.ALLOW, explanation.effect)
        assertEquals(AuthorizationReason.ALLOWED_BY_GRANT, explanation.reasonCode)
        assertEquals(listOf(grant.grantId), explanation.matchedAllowGrants)
    }

    @Test
    fun `Scenario K - Crash Recovery executes state recovery and emits audit events`() = runBlocking {
        env.core.recover()
        val events = env.repository.listAuditEvents()
        assertTrue(events.any { it.eventType == "SECURITY_STATE_RECOVERY_STARTED" })
        assertTrue(events.any { it.eventType == "SECURITY_STATE_RECOVERY_COMPLETED" })
    }

    private fun createMessagingCap(): CapabilityDefinition {
        return CapabilityDefinition(
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            description = "Receive offline messages",
            resourceType = ResourceType("MESSAGE"),
            operationType = OperationType("RECEIVE"),
            allowedConstraintTypes = setOf("MessageTypeConstraint", "MaximumPayloadBytesConstraint", "RateLimitConstraint"),
            createdAt = 100L
        )
    }

    private fun createMessagingRequest(peerNodeId: NodeId): AuthorizationRequest {
        return AuthorizationRequest(
            peerNodeId = peerNodeId,
            capabilityId = SecurityTestFixtures.messagingReceiveCap,
            operationType = OperationType("RECEIVE"),
            resourceType = ResourceType("MESSAGE"),
            requestedAt = 1000L
        )
    }
}
