package spike.security.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.testing.InMemorySecurityRepository

class AuthorizationGatewayTest {

    private lateinit var repository: InMemorySecurityRepository
    private lateinit var rateLimiter: RateLimiter
    private lateinit var quotaManager: QuotaManager
    private lateinit var engine: PolicyEvaluationEngine
    private lateinit var reservationManager: ResourceReservationManager
    private lateinit var gateway: AuthorizationGatewayImpl

    private val peerNodeId = NodeId("node:v1:sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val capabilityId = CapabilityId("object.transfer.receive")

    @BeforeEach
    fun setUp() {
        repository = InMemorySecurityRepository()
        rateLimiter = RateLimiter(repository)
        quotaManager = QuotaManager(repository)
        engine = PolicyEvaluationEngine(repository, rateLimiter, quotaManager)
        reservationManager = ResourceReservationManager(repository, quotaManager)
        gateway = AuthorizationGatewayImpl(repository, engine, reservationManager)
    }

    @Test
    fun `authorizeAndReserve creates reservation for valid request`() = runBlocking {
        repository.saveCapability(
            CapabilityDefinition(
                capabilityId = capabilityId,
                description = "Receive object transfers",
                resourceType = ResourceType("OBJECT"),
                operationType = OperationType("TRANSFER"),
                allowedConstraintTypes = setOf("MaximumObjectBytesConstraint"),
                supportsReservation = true,
                createdAt = 100L
            )
        )
        repository.savePeerProfile(
            PeerSecurityProfile(
                peerNodeId = peerNodeId,
                trustState = TrustState.OBSERVED,
                firstSeenAt = 100L,
                lastSeenAt = 100L,
                createdAt = 100L,
                updatedAt = 100L
            )
        )
        repository.saveGrant(
            CapabilityGrant(
                peerNodeId = peerNodeId,
                capabilityId = capabilityId,
                effect = GrantEffect.ALLOW,
                createdAt = 100L,
                updatedAt = 100L
            )
        )

        val request = AuthorizationRequest(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            operationType = OperationType("TRANSFER"),
            resourceType = ResourceType("OBJECT"),
            requestedUsage = 1024L,
            requestedAt = 1000L
        )

        val (decision, reservation) = gateway.authorizeAndReserve(request)
        assertEquals(AuthorizationEffect.ALLOW, decision.effect)
        assertNotNull(reservation)
        assertEquals(1024L, reservation?.reservedUsage)
    }

    @Test
    fun `one-shot grant decrements remaining uses atomically`() = runBlocking {
        repository.saveCapability(
            CapabilityDefinition(
                capabilityId = capabilityId,
                description = "Receive object transfers",
                resourceType = ResourceType("OBJECT"),
                operationType = OperationType("TRANSFER"),
                allowedConstraintTypes = emptySet(),
                createdAt = 100L
            )
        )
        repository.savePeerProfile(
            PeerSecurityProfile(
                peerNodeId = peerNodeId,
                trustState = TrustState.OBSERVED,
                firstSeenAt = 100L,
                lastSeenAt = 100L,
                createdAt = 100L,
                updatedAt = 100L
            )
        )
        val grant = CapabilityGrant(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = GrantEffect.ALLOW,
            maxUses = 1L,
            remainingUses = 1L,
            createdAt = 100L,
            updatedAt = 100L
        )
        repository.saveGrant(grant)

        val request = AuthorizationRequest(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            operationType = OperationType("TRANSFER"),
            resourceType = ResourceType("OBJECT"),
            requestedAt = 1000L
        )

        val decision1 = gateway.authorize(request)
        assertEquals(AuthorizationEffect.ALLOW, decision1.effect)

        val updatedGrant = repository.getGrant(grant.grantId)
        assertEquals(0L, updatedGrant?.remainingUses)
        assertEquals(GrantState.EXHAUSTED, updatedGrant?.state)

        val decision2 = gateway.authorize(request)
        assertEquals(AuthorizationEffect.DENY, decision2.effect)
        assertEquals(AuthorizationReason.DENIED_GRANT_EXHAUSTED, decision2.reasonCode)
    }
}
