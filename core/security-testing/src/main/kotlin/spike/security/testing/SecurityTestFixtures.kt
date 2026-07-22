package spike.security.testing

import spike.identity.domain.NodeId
import spike.security.application.*
import spike.security.domain.*

object SecurityTestFixtures {

    val peerA = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    val peerB = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    val messagingReceiveCap = CapabilityId("messaging.message.receive")
    val messagingProcessCap = CapabilityId("messaging.message.process")
    val objectReceiveCap = CapabilityId("object.transfer.receive")
    val objectResumeCap = CapabilityId("object.transfer.resume")
    val storageConsumeCap = CapabilityId("storage.bytes.consume")

    fun createDefaultEnvironment(): TestSecurityEnvironment {
        val repository = InMemorySecurityRepository()
        val rateLimiter = RateLimiter(repository)
        val quotaManager = QuotaManager(repository)
        val engine = PolicyEvaluationEngine(repository, rateLimiter, quotaManager)
        val reservationManager = ResourceReservationManager(repository, quotaManager)
        val gateway = AuthorizationGatewayImpl(repository, engine, reservationManager)
        val recoveryService = SecurityRecoveryService(repository, quotaManager)
        val core = LocalPeerSecurityCoreImpl(repository, gateway, engine, recoveryService)

        return TestSecurityEnvironment(
            repository = repository,
            rateLimiter = rateLimiter,
            quotaManager = quotaManager,
            policyEngine = engine,
            reservationManager = reservationManager,
            gateway = gateway,
            recoveryService = recoveryService,
            core = core
        )
    }
}

data class TestSecurityEnvironment(
    val repository: InMemorySecurityRepository,
    val rateLimiter: RateLimiter,
    val quotaManager: QuotaManager,
    val policyEngine: PolicyEvaluationEngine,
    val reservationManager: ResourceReservationManager,
    val gateway: AuthorizationGatewayImpl,
    val recoveryService: SecurityRecoveryService,
    val core: LocalPeerSecurityCoreImpl
)
