package spike.security.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.testing.InMemorySecurityRepository

class PolicyEvaluationEngineTest {

    private lateinit var repository: InMemorySecurityRepository
    private lateinit var rateLimiter: RateLimiter
    private lateinit var quotaManager: QuotaManager
    private lateinit var engine: PolicyEvaluationEngine

    private val peerNodeId = NodeId("node:v1:sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
    private val capabilityId = CapabilityId("messaging.message.receive")

    @BeforeEach
    fun setUp() {
        repository = InMemorySecurityRepository()
        rateLimiter = RateLimiter(repository)
        quotaManager = QuotaManager(repository)
        engine = PolicyEvaluationEngine(repository, rateLimiter, quotaManager)
    }

    @Test
    fun `unregistered capability returns DENIED_UNKNOWN_CAPABILITY`() = runBlocking {
        val request = AuthorizationRequest(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            operationType = OperationType("RECEIVE"),
            resourceType = ResourceType("MESSAGE"),
            requestedAt = 1000L
        )

        val decision = engine.evaluate(request, 1000L)
        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_UNKNOWN_CAPABILITY, decision.reasonCode)
    }

    @Test
    fun `blocked peer returns DENIED_PEER_BLOCKED`() = runBlocking {
        repository.saveCapability(createCapabilityDef())
        repository.savePeerProfile(
            PeerSecurityProfile(
                peerNodeId = peerNodeId,
                trustState = TrustState.BLOCKED,
                firstSeenAt = 100L,
                lastSeenAt = 100L,
                createdAt = 100L,
                updatedAt = 100L
            )
        )

        val request = createRequest()
        val decision = engine.evaluate(request, 1000L)
        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_PEER_BLOCKED, decision.reasonCode)
    }

    @Test
    fun `explicit DENY grant overrides ALLOW grant`() = runBlocking {
        repository.saveCapability(createCapabilityDef())
        repository.savePeerProfile(createProfile(TrustState.TRUSTED))

        val allowGrant = CapabilityGrant(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = GrantEffect.ALLOW,
            createdAt = 100L,
            updatedAt = 100L
        )
        val denyGrant = CapabilityGrant(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = GrantEffect.DENY,
            createdAt = 100L,
            updatedAt = 100L
        )

        repository.saveGrant(allowGrant)
        repository.saveGrant(denyGrant)

        val decision = engine.evaluate(createRequest(), 1000L)
        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_EXPLICIT_GRANT, decision.reasonCode)
    }

    @Test
    fun `valid ALLOW grant authorizes request`() = runBlocking {
        repository.saveCapability(createCapabilityDef())
        repository.savePeerProfile(createProfile(TrustState.OBSERVED))

        val allowGrant = CapabilityGrant(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = GrantEffect.ALLOW,
            createdAt = 100L,
            updatedAt = 100L
        )
        repository.saveGrant(allowGrant)

        val decision = engine.evaluate(createRequest(), 1000L)
        assertEquals(AuthorizationEffect.ALLOW, decision.effect)
        assertEquals(AuthorizationReason.ALLOWED_BY_GRANT, decision.reasonCode)
    }

    @Test
    fun `message type constraint violation returns DENIED_MESSAGE_TYPE`() = runBlocking {
        repository.saveCapability(createCapabilityDef())
        repository.savePeerProfile(createProfile(TrustState.OBSERVED))

        val allowGrant = CapabilityGrant(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            effect = GrantEffect.ALLOW,
            constraints = ConstraintSet(listOf(MessageTypeConstraint(setOf("chat.text")))),
            createdAt = 100L,
            updatedAt = 100L
        )
        repository.saveGrant(allowGrant)

        val request = createRequest().copy(
            context = AuthorizationContext(messageType = "chat.video")
        )
        val decision = engine.evaluate(request, 1000L)
        assertEquals(AuthorizationEffect.DENY, decision.effect)
        assertEquals(AuthorizationReason.DENIED_MESSAGE_TYPE, decision.reasonCode)
    }

    private fun createCapabilityDef(): CapabilityDefinition {
        return CapabilityDefinition(
            capabilityId = capabilityId,
            description = "Receive offline messages",
            resourceType = ResourceType("MESSAGE"),
            operationType = OperationType("RECEIVE"),
            allowedConstraintTypes = setOf("MessageTypeConstraint", "MaximumPayloadBytesConstraint"),
            createdAt = 100L
        )
    }

    private fun createProfile(trustState: TrustState): PeerSecurityProfile {
        return PeerSecurityProfile(
            peerNodeId = peerNodeId,
            trustState = trustState,
            firstSeenAt = 100L,
            lastSeenAt = 100L,
            createdAt = 100L,
            updatedAt = 100L
        )
    }

    private fun createRequest(): AuthorizationRequest {
        return AuthorizationRequest(
            peerNodeId = peerNodeId,
            capabilityId = capabilityId,
            operationType = OperationType("RECEIVE"),
            resourceType = ResourceType("MESSAGE"),
            requestedAt = 1000L
        )
    }
}
