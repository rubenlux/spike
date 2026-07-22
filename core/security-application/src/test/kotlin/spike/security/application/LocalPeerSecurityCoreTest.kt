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

class LocalPeerSecurityCoreTest {

    private lateinit var repository: InMemorySecurityRepository
    private lateinit var rateLimiter: RateLimiter
    private lateinit var quotaManager: QuotaManager
    private lateinit var engine: PolicyEvaluationEngine
    private lateinit var reservationManager: ResourceReservationManager
    private lateinit var gateway: AuthorizationGatewayImpl
    private lateinit var recoveryService: SecurityRecoveryService
    private lateinit var core: LocalPeerSecurityCoreImpl

    private val peerNodeId = NodeId("node:v1:sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val capabilityId = CapabilityId("messaging.message.receive")

    @BeforeEach
    fun setUp() {
        repository = InMemorySecurityRepository()
        rateLimiter = RateLimiter(repository)
        quotaManager = QuotaManager(repository)
        engine = PolicyEvaluationEngine(repository, rateLimiter, quotaManager)
        reservationManager = ResourceReservationManager(repository, quotaManager)
        gateway = AuthorizationGatewayImpl(repository, engine, reservationManager)
        recoveryService = SecurityRecoveryService(repository, quotaManager)
        core = LocalPeerSecurityCoreImpl(repository, gateway, engine, recoveryService)
    }

    @Test
    fun `peer blocking revokes active leases and sets trustState to BLOCKED`() = runBlocking {
        core.observePeer(peerNodeId)
        core.registerCapability(
            CapabilityDefinition(
                capabilityId = capabilityId,
                description = "Receive offline messages",
                resourceType = ResourceType("MESSAGE"),
                operationType = OperationType("RECEIVE"),
                allowedConstraintTypes = emptySet(),
                createdAt = 100L
            )
        )
        val grant = core.createGrant(peerNodeId, capabilityId)
        val decision = core.authorize(
            AuthorizationRequest(
                peerNodeId = peerNodeId,
                capabilityId = capabilityId,
                operationType = OperationType("RECEIVE"),
                resourceType = ResourceType("MESSAGE"),
                requestedAt = 1000L
            )
        )

        val lease = AuthorizationLease(
            decisionId = decision.decisionId,
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            grantIds = listOf(grant.grantId),
            policyVersion = PolicyVersion(1L),
            trustVersion = TrustVersion(1L),
            revocationVersion = RevocationVersion(1L),
            state = LeaseState.ACTIVE,
            issuedAt = 1000L,
            expiresAt = 2000L,
            lastValidatedAt = 1000L
        )
        repository.saveLease(lease)

        val blockedProfile = core.blockPeer(peerNodeId, "SECURITY_VIOLATION")
        assertEquals(TrustState.BLOCKED, blockedProfile.trustState)

        val updatedLease = repository.getLease(lease.leaseId)
        assertEquals(LeaseState.REVOKED, updatedLease?.state)
    }

    @Test
    fun `explainDecision details decision rationale and matching grants`() = runBlocking {
        core.observePeer(peerNodeId)
        core.registerCapability(
            CapabilityDefinition(
                capabilityId = capabilityId,
                description = "Receive offline messages",
                resourceType = ResourceType("MESSAGE"),
                operationType = OperationType("RECEIVE"),
                allowedConstraintTypes = emptySet(),
                createdAt = 100L
            )
        )
        val grant = core.createGrant(peerNodeId, capabilityId)
        val decision = core.authorize(
            AuthorizationRequest(
                peerNodeId = peerNodeId,
                capabilityId = capabilityId,
                operationType = OperationType("RECEIVE"),
                resourceType = ResourceType("MESSAGE"),
                requestedAt = 1000L
            )
        )

        val explanation = core.explainDecision(decision.decisionId)
        assertEquals(decision.decisionId, explanation.decisionId)
        assertEquals(AuthorizationEffect.ALLOW, explanation.effect)
        assertEquals(AuthorizationReason.ALLOWED_BY_GRANT, explanation.reasonCode)
        assertEquals(listOf(grant.grantId), explanation.matchedAllowGrants)
    }
}
