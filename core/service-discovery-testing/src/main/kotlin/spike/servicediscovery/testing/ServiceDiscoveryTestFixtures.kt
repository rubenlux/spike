package spike.servicediscovery.testing

import spike.identity.domain.NodeId
import spike.security.contracts.SecurityEventListener
import spike.security.domain.*
import spike.servicediscovery.application.*
import spike.servicediscovery.contracts.ServiceHandler
import spike.servicediscovery.domain.*
import java.util.concurrent.ConcurrentHashMap

// ─────────────────────────────────────────────────────────────
// Test-scoped security core supporting per-peer allow/deny
// ─────────────────────────────────────────────────────────────

class TestLocalPeerSecurityCore : spike.security.contracts.LocalPeerSecurityCore {
    val allowedCapabilities = ConcurrentHashMap.newKeySet<String>()
    val auditLog = mutableListOf<AuthorizationRequest>()

    override suspend fun authorize(request: AuthorizationRequest): AuthorizationDecision {
        auditLog.add(request)
        val allowed = allowedCapabilities.contains(request.capabilityId.raw)
        return AuthorizationDecision(
            requestId = request.requestId,
            peerNodeId = request.peerNodeId,
            capabilityId = request.capabilityId,
            effect = if (allowed) AuthorizationEffect.ALLOW else AuthorizationEffect.DENY,
            reasonCode = if (allowed) AuthorizationReason.ALLOWED_BY_GRANT else AuthorizationReason.DENIED_NO_MATCHING_GRANT,
            decidedAt = System.currentTimeMillis()
        )
    }

    private fun fakeProfile(peerNodeId: NodeId): PeerSecurityProfile {
        val now = System.currentTimeMillis()
        return PeerSecurityProfile(peerNodeId = peerNodeId, firstSeenAt = now, lastSeenAt = now, createdAt = now, updatedAt = now)
    }

    override suspend fun observePeer(peerNodeId: NodeId) = fakeProfile(peerNodeId)
    override suspend fun getPeerSecurityProfile(peerNodeId: NodeId): PeerSecurityProfile? = null
    override suspend fun setTrustState(peerNodeId: NodeId, trustState: TrustState, reason: String) = fakeProfile(peerNodeId)
    override suspend fun trustPeer(peerNodeId: NodeId) = fakeProfile(peerNodeId)
    override suspend fun restrictPeer(peerNodeId: NodeId, reason: String) = fakeProfile(peerNodeId)
    override suspend fun removeTrust(peerNodeId: NodeId, reason: String) = fakeProfile(peerNodeId)
    override suspend fun blockPeer(peerNodeId: NodeId, reason: String) = fakeProfile(peerNodeId)
    override suspend fun unblockPeer(peerNodeId: NodeId, reason: String) = fakeProfile(peerNodeId)
    override suspend fun registerCapability(definition: CapabilityDefinition) {}
    override suspend fun getCapability(capabilityId: CapabilityId): CapabilityDefinition? = null
    override suspend fun listCapabilities(): List<CapabilityDefinition> = emptyList()
    override suspend fun createGrant(peerNodeId: NodeId, capabilityId: CapabilityId, constraints: ConstraintSet, effect: GrantEffect, validFrom: Long?, validUntil: Long?, maxUses: Long?): CapabilityGrant {
        val now = System.currentTimeMillis()
        return CapabilityGrant(peerNodeId = peerNodeId, capabilityId = capabilityId, effect = effect, constraints = constraints, maxUses = maxUses, createdAt = now, updatedAt = now)
    }
    override suspend fun activateGrant(grantId: GrantId): CapabilityGrant {
        val now = System.currentTimeMillis()
        return CapabilityGrant(peerNodeId = NodeId("dummy"), capabilityId = CapabilityId("dummy.resource.action"), effect = GrantEffect.ALLOW, constraints = ConstraintSet(), createdAt = now, updatedAt = now)
    }
    override suspend fun disableGrant(grantId: GrantId) = activateGrant(grantId)
    override suspend fun revokeGrant(grantId: GrantId, reason: RevocationReason) = activateGrant(grantId)
    override suspend fun getGrant(grantId: GrantId): CapabilityGrant? = null
    override suspend fun listPeerGrants(peerNodeId: NodeId): List<CapabilityGrant> = emptyList()
    override suspend fun authorizeAndReserve(request: AuthorizationRequest): Pair<AuthorizationDecision, ResourceReservation?> = Pair(authorize(request), null)
    override suspend fun validateLease(leaseId: AuthorizationLeaseId, context: AuthorizationContext): AuthorizationLease {
        val now = System.currentTimeMillis()
        return AuthorizationLease(decisionId = DecisionId.generate(), peerNodeId = NodeId("dummy"), capabilityId = CapabilityId("dummy.resource.action"), policyVersion = PolicyVersion(1L), trustVersion = TrustVersion(1L), revocationVersion = RevocationVersion(1L), issuedAt = now, expiresAt = now + 3_600_000L, lastValidatedAt = now)
    }
    override suspend fun consumeUsage(leaseId: AuthorizationLeaseId, usage: Long) {}
    override suspend fun completeOperation(leaseId: AuthorizationLeaseId, result: String) {}
    override suspend fun releaseReservation(reservationId: ReservationId) {}
    override suspend fun getPeerUsage(peerNodeId: NodeId): List<UsageCounter> = emptyList()
    override suspend fun getPeerRateLimits(peerNodeId: NodeId): List<RateLimitBucket> = emptyList()
    override suspend fun getPeerQuotas(peerNodeId: NodeId): List<UsageCounter> = emptyList()
    override suspend fun explainDecision(decisionId: DecisionId): AuthorizationExplanation =
        AuthorizationExplanation(decisionId = decisionId, effect = AuthorizationEffect.ALLOW, reasonCode = AuthorizationReason.ALLOWED_BY_GRANT, peerTrustState = TrustState.TRUSTED, capabilityId = CapabilityId("dummy.resource.action"), matchedAllowGrants = emptyList(), matchedDenyGrants = emptyList(), failedConstraints = emptyList(), policyVersion = PolicyVersion(1L), decidedAt = System.currentTimeMillis())
    override suspend fun recover() {}
    override fun subscribeSecurityEvents(listener: SecurityEventListener) {}
}

// ─────────────────────────────────────────────────────────────
// Test environment wiring
// ─────────────────────────────────────────────────────────────

/**
 * Fully wired acceptance test environment for SPEC-011.
 * Mirrors the production wiring of OfflineServiceRuntimeImpl.
 */
data class ServiceDiscoveryTestEnvironment(
    val providerNodeId: NodeId,
    val callerNodeId: NodeId,
    val securityCore: TestLocalPeerSecurityCore,
    val repository: InMemoryRpcRuntimeRepository,
    val registry: LocalServiceRegistryImpl,
    val advertisementService: ServiceAdvertisementServiceImpl,
    val negotiationService: ContractNegotiationServiceImpl,
    val executionService: RpcExecutionServiceImpl,
    val recoveryService: ServiceRuntimeRecoveryServiceImpl,
    val invocationService: RpcInvocationServiceImpl,
    val runtime: OfflineServiceRuntimeImpl
)

object ServiceDiscoveryTestFixtures {

    val providerNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    val callerNode   = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")
    val thirdNode    = NodeId("node:v1:sha256:3333333333333333333333333333333333333333333333333333333333333333")

    val fileIndexServiceId  = ServiceId("app.mesh.file-index")
    val fileIndexContractId = ContractId("app.mesh.file-index.contract")
    val searchMethodId      = MethodId("app.mesh.file-index/search")
    val searchCapabilityId  = "service.app.mesh.file-index.search.invoke"

    val chatServiceId  = ServiceId("app.mesh.chat")
    val chatContractId = ContractId("app.mesh.chat.contract")
    val sendMethodId   = MethodId("app.mesh.chat/send")
    val sendCapabilityId = "service.app.mesh.chat.send.invoke"

    fun buildMethod(
        methodId: MethodId,
        name: String,
        capabilityId: String,
        semantics: RpcMethodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND
    ) = ServiceMethodDefinition(
        methodId = methodId,
        methodName = name,
        semantics = semantics,
        requiredCapabilityId = capabilityId
    )

    fun buildDefinition(
        serviceId: ServiceId,
        contractId: ContractId,
        vararg methods: ServiceMethodDefinition
    ) = ServiceDefinition(
        serviceId = serviceId,
        displayName = serviceId.value,
        contractId = contractId,
        supportedVersions = listOf(ContractVersion("1.0")),
        methods = methods.toList(),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    fun buildRemoteRecord(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        contractId: ContractId,
        instanceId: String = "inst_remote"
    ) = RemoteServiceRecord(
        providerNodeId = providerNodeId,
        serviceId = serviceId,
        serviceInstanceId = ServiceInstanceId(instanceId),
        contractId = contractId,
        supportedVersions = listOf(ContractVersion("1.0")),
        contractHashes = mapOf(ContractVersion("1.0") to ContractHash("hash_1_0")),
        methodSummary = emptyList(),
        latestAdvertisementVersion = AdvertisementVersion(1L),
        lastAdvertisedAt = System.currentTimeMillis(),
        lastSeenAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 300_000L,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    fun buildNegotiatedContract(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        contractId: ContractId,
        instanceId: String = "inst_remote"
    ) = NegotiatedContract(
        providerNodeId = providerNodeId,
        serviceId = serviceId,
        serviceInstanceId = ServiceInstanceId(instanceId),
        contractId = contractId,
        selectedVersion = ContractVersion("1.0"),
        localContractHash = ContractHash("hash_1_0"),
        remoteContractHash = ContractHash("hash_1_0"),
        negotiatedAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 3_600_000L
    )

    fun createEnvironment(): ServiceDiscoveryTestEnvironment {
        val security   = TestLocalPeerSecurityCore()
        val repository = InMemoryRpcRuntimeRepository()
        val registry   = LocalServiceRegistryImpl(repository)
        val advService = ServiceAdvertisementServiceImpl(providerNode, repository)
        val negService = ContractNegotiationServiceImpl(repository)
        val execService = RpcExecutionServiceImpl(providerNode, repository, registry, security)
        val recoveryService = ServiceRuntimeRecoveryServiceImpl(repository)
        val invService = RpcInvocationServiceImpl(callerNode, repository, negService, execService)
        val runtime = OfflineServiceRuntimeImpl(
            localServiceRegistry = registry,
            advertisementService = advService,
            negotiationService = negService,
            invocationService = invService,
            executionService = execService,
            recoveryService = recoveryService
        )
        return ServiceDiscoveryTestEnvironment(
            providerNodeId = providerNode,
            callerNodeId = callerNode,
            securityCore = security,
            repository = repository,
            registry = registry,
            advertisementService = advService,
            negotiationService = negService,
            executionService = execService,
            recoveryService = recoveryService,
            invocationService = invService,
            runtime = runtime
        )
    }

    /** Standard test payload descriptor. */
    fun sampleRequest(queryText: String = "test") = PayloadDescriptor(
        inlinePayload = """{"query":"$queryText"}""".toByteArray(),
        payloadHash = "hash_req_$queryText",
        payloadLength = (10 + queryText.length).toLong()
    )

    /** Standard echo-handler that increments a counter and returns a fixed response. */
    fun echoHandler(counter: () -> Unit = {}): ServiceHandler = ServiceHandler { _, _ ->
        counter()
        PayloadDescriptor(
            inlinePayload = """{"results":[]}""".toByteArray(),
            payloadHash = "hash_resp_ok",
            payloadLength = 14L
        )
    }

    /**
     * Register the file-index service with an optional handler and pre-grant its capability.
     */
    suspend fun registerFileIndexService(
        env: ServiceDiscoveryTestEnvironment,
        handler: ServiceHandler = echoHandler(),
        grantCapability: Boolean = true
    ) {
        val method = buildMethod(searchMethodId, "search", searchCapabilityId)
        val definition = buildDefinition(fileIndexServiceId, fileIndexContractId, method)
        env.registry.registerService(definition, handler)
        env.registry.setServiceAvailable(fileIndexServiceId)
        if (grantCapability) {
            env.securityCore.allowedCapabilities.add(searchCapabilityId)
        }

        // Seed a negotiated contract so invocation tests can resolve the service
        val negotiated = buildNegotiatedContract(providerNode, fileIndexServiceId, fileIndexContractId)
        env.repository.saveRemoteServiceRecord(buildRemoteRecord(providerNode, fileIndexServiceId, fileIndexContractId))
        env.repository.saveNegotiatedContract(negotiated)
    }

    /** Build a durable persisted invocation, returning it without executing. */
    suspend fun persistInvocation(
        env: ServiceDiscoveryTestEnvironment,
        invocationId: InvocationId = InvocationId.generate(),
        idempotencyKey: IdempotencyKey? = null,
        deadline: Deadline? = null
    ): RpcInvocation {
        val inv = RpcInvocation(
            invocationId = invocationId,
            callerNodeId = callerNode,
            providerNodeId = providerNode,
            serviceId = fileIndexServiceId,
            serviceInstanceId = ServiceInstanceId("inst_remote"),
            contractId = fileIndexContractId,
            contractVersion = ContractVersion("1.0"),
            methodId = searchMethodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            idempotencyKey = idempotencyKey,
            requestPayloadDescriptor = sampleRequest(),
            state = RpcInvocationState.PERSISTED,
            createdAt = System.currentTimeMillis(),
            deadlineAt = deadline,
            expiresAt = deadline?.epochMillis ?: (System.currentTimeMillis() + 86_400_000L)
        )
        env.repository.saveInvocation(inv)
        return inv
    }
}
