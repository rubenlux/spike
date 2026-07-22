package spike.servicediscovery.testing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.servicediscovery.domain.*

/**
 * SPEC-011 Full Acceptance Test Suite — Scenarios A through M.
 *
 * Covers:
 *   A  Service Registration & Availability
 *   B  Advertisement Publishing (Descriptive, No Auth Implied)
 *   C  Advertisement Processing & Remote Catalog
 *   D  Contract Negotiation — Compatible Versions
 *   E  Contract Negotiation — Incompatible Version Rejected
 *   F  Authorized Inbound RPC Execution
 *   G  Unauthorized Inbound RPC — Denied Before Handler Execution
 *   H  Durable Deduplication — Same InvocationId Returns Cached Result
 *   I  Idempotency Key — Same Key + Same Payload Returns Cached Result
 *   J  Idempotency Key Conflict — Same Key + Different Payload Rejected
 *   K  Crash Recovery — NON_IDEMPOTENT_COMMAND in RUNNING → UNKNOWN_OUTCOME, Never Re-Executed
 *   L  Late Response After Deadline Expiration — EXPIRED state, no handler execution
 *   M  Transport Interruption + Reencounter — Same InvocationId, durable dedup, correct audit
 */
class OfflineServiceRuntimeAcceptanceScenariosTest {

    private lateinit var env: ServiceDiscoveryTestEnvironment

    @BeforeEach
    fun setUp() {
        env = ServiceDiscoveryTestFixtures.createEnvironment()
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario A: Service Registration & Availability
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario A - Registering a service stores definition and returns REGISTERED state`() = runBlocking {
        val method = ServiceDiscoveryTestFixtures.buildMethod(
            ServiceDiscoveryTestFixtures.searchMethodId, "search", ServiceDiscoveryTestFixtures.searchCapabilityId
        )
        val definition = ServiceDiscoveryTestFixtures.buildDefinition(
            ServiceDiscoveryTestFixtures.fileIndexServiceId,
            ServiceDiscoveryTestFixtures.fileIndexContractId,
            method
        )

        val registration = env.registry.registerService(definition, ServiceDiscoveryTestFixtures.echoHandler())

        assertEquals(ServiceDiscoveryTestFixtures.fileIndexServiceId, registration.serviceId)
        assertEquals(LocalServiceState.REGISTERED, registration.state)

        val available = env.registry.setServiceAvailable(ServiceDiscoveryTestFixtures.fileIndexServiceId)
        assertEquals(LocalServiceState.AVAILABLE, available.state)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario B: Advertisement Publishing — Purely Descriptive
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario B - Published advertisement is descriptive and does not imply authorization or trust`() = runBlocking {
        ServiceDiscoveryTestFixtures.registerFileIndexService(env)

        val advertisement = env.advertisementService.publishAdvertisement(
            ServiceDiscoveryTestFixtures.fileIndexServiceId
        )

        // Advertisement describes capability — does NOT authorize access
        assertEquals(ServiceDiscoveryTestFixtures.fileIndexServiceId, advertisement.serviceId)
        assertEquals(ServiceDiscoveryTestFixtures.fileIndexContractId, advertisement.contractId)
        assertTrue(advertisement.supportedVersions.isNotEmpty())
        assertTrue(advertisement.advertisedAt > 0L)
        assertTrue(advertisement.expiresAt > advertisement.advertisedAt)

        // Stripping capability from security core: advertisement still exists (authorization is separate)
        env.securityCore.allowedCapabilities.remove(ServiceDiscoveryTestFixtures.searchCapabilityId)
        val stillHasAdvert = env.repository.getLocalRegistration(ServiceDiscoveryTestFixtures.fileIndexServiceId)
        assertNotNull(stillHasAdvert) // Advertisement is independent of authorization state

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario C: Advertisement Processing & Remote Catalog
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario C - Processing received advertisement creates a remote service record`() = runBlocking {
        ServiceDiscoveryTestFixtures.registerFileIndexService(env)

        val advertisement = env.advertisementService.publishAdvertisement(
            ServiceDiscoveryTestFixtures.fileIndexServiceId
        )

        // Simulate receiving that advertisement from a remote peer
        val remoteRecord = env.advertisementService.processAdvertisement(
            ServiceDiscoveryTestFixtures.callerNode, advertisement
        )

        assertEquals(ServiceDiscoveryTestFixtures.fileIndexServiceId, remoteRecord.serviceId)
        assertEquals(ServiceDiscoveryTestFixtures.callerNode, remoteRecord.providerNodeId)
        assertEquals(ServiceAvailabilityState.AVAILABLE, remoteRecord.availabilityState)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario D: Contract Negotiation — Compatible Versions
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario D - Contract negotiation succeeds for compatible Major-Minor version`() = runBlocking {
        ServiceDiscoveryTestFixtures.registerFileIndexService(env)

        // Caller requires 1.0, provider offers 1.0 — should succeed
        val negotiated = env.negotiationService.negotiateContract(
            providerNodeId = ServiceDiscoveryTestFixtures.providerNode,
            serviceId = ServiceDiscoveryTestFixtures.fileIndexServiceId,
            clientRequiredVersion = ContractVersion("1.0")
        )

        assertEquals(ContractVersion("1.0"), negotiated.selectedVersion)
        assertEquals(ServiceDiscoveryTestFixtures.fileIndexContractId, negotiated.contractId)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario E: Contract Negotiation — Incompatible Version Rejected
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario E - Contract negotiation fails when provider does not support required version`() = runBlocking {
        ServiceDiscoveryTestFixtures.registerFileIndexService(env)

        var caught: ServiceRuntimeError.ContractNotSupported? = null
        try {
            env.negotiationService.negotiateContract(
                providerNodeId = ServiceDiscoveryTestFixtures.providerNode,
                serviceId = ServiceDiscoveryTestFixtures.fileIndexServiceId,
                clientRequiredVersion = ContractVersion("2.0") // Provider only offers 1.0
            )
        } catch (e: ServiceRuntimeError.ContractNotSupported) {
            caught = e
        }

        assertNotNull(caught)
        assertEquals(ServiceDiscoveryTestFixtures.fileIndexContractId, caught!!.contractId)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario F: Authorized Inbound RPC Execution
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario F - Authorized inbound request executes handler and commits SUCCESS result`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        val invocation = ServiceDiscoveryTestFixtures.persistInvocation(env)
        val result = env.executionService.processInboundRequest(invocation)

        assertEquals(RpcResultStatus.SUCCESS, result.status)
        assertEquals(1, executionCount)

        // Verify execution record is persisted and complete
        val execution = env.repository.findExecutionByInvocation(invocation.callerNodeId, invocation.invocationId)
        assertNotNull(execution)
        assertEquals(RpcExecutionState.COMPLETED, execution!!.state)
        assertNotNull(execution.authorizationDecisionId) // SPEC-009 decision was recorded

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario G: Unauthorized Inbound RPC — Denied Before Handler Execution
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario G - Unauthorized request is denied before any handler execution and audit record is created`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(
            env,
            handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ },
            grantCapability = false // No capability grant
        )

        val invocation = ServiceDiscoveryTestFixtures.persistInvocation(env)

        var caughtError: ServiceRuntimeError.AuthorizationDenied? = null
        try {
            env.executionService.processInboundRequest(invocation)
        } catch (e: ServiceRuntimeError.AuthorizationDenied) {
            caughtError = e
        }

        // Handler MUST NOT have executed
        assertEquals(0, executionCount)
        assertNotNull(caughtError)
        assertTrue(caughtError!!.message!!.contains("DENIED_NO_MATCHING_GRANT"))

        // Durable audit record: REJECTED execution + AUTHORIZATION_DENIED result must exist
        val execution = env.repository.findExecutionByInvocation(invocation.callerNodeId, invocation.invocationId)
        assertNotNull(execution)
        assertEquals(RpcExecutionState.REJECTED, execution!!.state)
        assertEquals(RpcErrorCode.AUTHORIZATION_DENIED, execution.failureCode)
        assertNotNull(execution.authorizationDecisionId)

        val result = env.repository.getResultByInvocation(invocation.invocationId)
        assertNotNull(result)
        assertEquals(RpcResultStatus.AUTHORIZATION_DENIED, result!!.status)

        // Security core was called exactly once (authorization check)
        assertEquals(1, env.securityCore.auditLog.size)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario H: Durable Deduplication — Same InvocationId
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario H - Duplicate InvocationId returns cached result without re-executing handler`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        val invocation = ServiceDiscoveryTestFixtures.persistInvocation(env)

        val result1 = env.executionService.processInboundRequest(invocation)
        assertEquals(1, executionCount)
        assertEquals(RpcResultStatus.SUCCESS, result1.status)

        // Resend the exact same invocation (e.g., transport retry)
        val result2 = env.executionService.processInboundRequest(invocation)

        // Handler MUST NOT re-execute
        assertEquals(1, executionCount)
        assertEquals(result1.resultId, result2.resultId)
        assertEquals(RpcResultStatus.SUCCESS, result2.status)

        // Verify only one execution record was created
        val executions = env.repository.executions.values.filter {
            it.invocationId == invocation.invocationId
        }
        assertEquals(1, executions.size)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario I: Idempotency Key — Same Key + Same Payload
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario I - Same idempotency key and payload returns cached result for different InvocationIds`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        val iKey = IdempotencyKey("client-req-00001")
        val inv1 = ServiceDiscoveryTestFixtures.persistInvocation(env, idempotencyKey = iKey)

        val result1 = env.executionService.processInboundRequest(inv1)
        assertEquals(1, executionCount)

        // Second invocation with new InvocationId but same idempotency key and same payload
        val inv2 = ServiceDiscoveryTestFixtures.persistInvocation(env, idempotencyKey = iKey)
        val result2 = env.executionService.processInboundRequest(inv2)

        assertEquals(1, executionCount) // Handler NOT re-executed
        assertEquals(result1.resultId, result2.resultId)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario J: Idempotency Key Conflict — Same Key + Different Payload
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario J - Idempotency key conflict when payload hash differs is rejected with IdempotencyKeyConflict`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        val iKey = IdempotencyKey("client-req-00002")
        val inv1 = ServiceDiscoveryTestFixtures.persistInvocation(env, idempotencyKey = iKey)
        env.executionService.processInboundRequest(inv1)
        assertEquals(1, executionCount)

        // Different payload with same idempotency key — MUST be rejected
        val inv2 = RpcInvocation(
            callerNodeId = ServiceDiscoveryTestFixtures.callerNode,
            providerNodeId = ServiceDiscoveryTestFixtures.providerNode,
            serviceId = ServiceDiscoveryTestFixtures.fileIndexServiceId,
            serviceInstanceId = ServiceInstanceId("inst_remote"),
            contractId = ServiceDiscoveryTestFixtures.fileIndexContractId,
            contractVersion = ContractVersion("1.0"),
            methodId = ServiceDiscoveryTestFixtures.searchMethodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            idempotencyKey = iKey,
            requestPayloadDescriptor = PayloadDescriptor(
                inlinePayload = """{"query":"DIFFERENT_PAYLOAD"}""".toByteArray(),
                payloadHash = "hash_req_DIFFERENT_PAYLOAD", // Different hash!
                payloadLength = 30L
            ),
            state = RpcInvocationState.PERSISTED,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86_400_000L
        )
        env.repository.saveInvocation(inv2)

        var caughtConflict: ServiceRuntimeError.IdempotencyKeyConflict? = null
        try {
            env.executionService.processInboundRequest(inv2)
        } catch (e: ServiceRuntimeError.IdempotencyKeyConflict) {
            caughtConflict = e
        }

        assertNotNull(caughtConflict)
        assertEquals(1, executionCount) // No additional execution

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario K: Crash Recovery — NON_IDEMPOTENT_COMMAND → UNKNOWN_OUTCOME
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario K - Crash recovery marks interrupted NON_IDEMPOTENT_COMMAND as UNKNOWN_OUTCOME without re-execution`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        val invocation = ServiceDiscoveryTestFixtures.persistInvocation(env)

        // Simulate interrupted execution: system crashed while handler was RUNNING
        val interruptedExecution = RpcExecution(
            invocationId = invocation.invocationId,
            callerNodeId = invocation.callerNodeId,
            providerNodeId = invocation.providerNodeId,
            serviceId = invocation.serviceId,
            serviceInstanceId = invocation.serviceInstanceId,
            methodId = invocation.methodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            state = RpcExecutionState.RUNNING, // Interrupted mid-execution
            requestReceivedAt = System.currentTimeMillis()
        )
        env.repository.saveExecution(interruptedExecution)
        env.repository.saveInvocation(invocation.copy(state = RpcInvocationState.EXECUTING))

        // Run recovery
        env.recoveryService.recover()

        // Invariant: NON_IDEMPOTENT_COMMAND interrupted → UNKNOWN_OUTCOME, never re-executed
        val recoveredExecution = env.repository.getExecution(interruptedExecution.executionId)
        assertNotNull(recoveredExecution)
        assertEquals(RpcExecutionState.UNKNOWN_OUTCOME, recoveredExecution!!.state)

        val recoveredResult = env.repository.getResultByInvocation(invocation.invocationId)
        assertNotNull(recoveredResult)
        assertEquals(RpcResultStatus.UNKNOWN_OUTCOME, recoveredResult!!.status)

        // Attempting to re-process the invocation returns the UNKNOWN_OUTCOME result without re-executing
        val reprocessResult = env.executionService.processInboundRequest(invocation)
        assertEquals(RpcResultStatus.UNKNOWN_OUTCOME, reprocessResult.status)
        assertEquals(0, executionCount) // Handler was never executed

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario L: Late Response After Deadline Expiration
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario L - Invocation received after deadline expiration is marked EXPIRED and handler is not executed`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        // Create an invocation with a deadline already in the past
        val expiredDeadline = Deadline(System.currentTimeMillis() - 10_000L) // 10 seconds ago
        val expiredInvocation = RpcInvocation(
            callerNodeId = ServiceDiscoveryTestFixtures.callerNode,
            providerNodeId = ServiceDiscoveryTestFixtures.providerNode,
            serviceId = ServiceDiscoveryTestFixtures.fileIndexServiceId,
            serviceInstanceId = ServiceInstanceId("inst_remote"),
            contractId = ServiceDiscoveryTestFixtures.fileIndexContractId,
            contractVersion = ContractVersion("1.0"),
            methodId = ServiceDiscoveryTestFixtures.searchMethodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            requestPayloadDescriptor = ServiceDiscoveryTestFixtures.sampleRequest("deadline-test"),
            state = RpcInvocationState.SENDING,
            createdAt = System.currentTimeMillis() - 15_000L,
            deadlineAt = expiredDeadline,
            expiresAt = expiredDeadline.epochMillis
        )
        env.repository.saveInvocation(expiredInvocation)

        // Run recovery — should detect the expired invocation and mark it EXPIRED
        env.recoveryService.recover()

        val expiredState = env.repository.getInvocation(expiredInvocation.invocationId)
        assertNotNull(expiredState)
        assertEquals(RpcInvocationState.EXPIRED, expiredState!!.state)

        // Handler must not have been executed
        assertEquals(0, executionCount)

        // Verify durable audit: the invocation record is marked EXPIRED (not FAILED, not UNKNOWN_OUTCOME)
        // This gives the caller correct information to retry with a new InvocationId
        assertNotEquals(RpcInvocationState.FAILED, expiredState.state)
        assertNotEquals(RpcInvocationState.UNKNOWN_OUTCOME, expiredState.state)

        Unit
    }

    // ─────────────────────────────────────────────────────────────
    // Scenario M: Transport Interruption + Reencounter with Same InvocationId
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `Scenario M - Transport interruption followed by reencounter reuses InvocationId and deduplication returns original result`() = runBlocking {
        var executionCount = 0
        ServiceDiscoveryTestFixtures.registerFileIndexService(env, handler = ServiceDiscoveryTestFixtures.echoHandler { executionCount++ })

        // Step 1: Caller creates an InvocationId and sends the request
        // The InvocationId MUST remain immutable across transport interruptions
        val fixedInvocationId = InvocationId.generate()
        val invocation = ServiceDiscoveryTestFixtures.persistInvocation(env, invocationId = fixedInvocationId)

        // Step 2: Transport delivers request to provider — it executes successfully
        val originalResult = env.executionService.processInboundRequest(invocation)
        assertEquals(RpcResultStatus.SUCCESS, originalResult.status)
        assertEquals(1, executionCount)

        // Verify the result was persisted durably
        val persistedResult = env.repository.getResultByInvocation(fixedInvocationId)
        assertNotNull(persistedResult)
        assertEquals(originalResult.resultId, persistedResult!!.resultId)

        // Step 3: Transport interrupts — response is lost before caller receives it
        // Caller reconnects (reencounter) and retransmits the SAME InvocationId
        val retransmittedInvocation = invocation.copy(
            state = RpcInvocationState.SENDING,
            attemptCount = 2, // Second attempt marker
            lastAttemptAt = System.currentTimeMillis()
        )

        // Step 4: Provider receives retransmission — durable deduplication MUST return original result
        val deduplicatedResult = env.executionService.processInboundRequest(retransmittedInvocation)

        // Execution count must NOT increase — handler was not re-executed
        assertEquals(1, executionCount)

        // Result is identical to original — same resultId, same status
        assertEquals(originalResult.resultId, deduplicatedResult.resultId)
        assertEquals(RpcResultStatus.SUCCESS, deduplicatedResult.status)

        // Audit: security core was called exactly once (for the original request)
        // The deduplication path returns the cached result WITHOUT calling SPEC-009 again
        assertEquals(1, env.securityCore.auditLog.size)

        // Only one execution record exists for this InvocationId
        val executions = env.repository.executions.values.filter {
            it.invocationId == fixedInvocationId
        }
        assertEquals(1, executions.size)
        assertEquals(RpcExecutionState.COMPLETED, executions.first().state)

        Unit
    }
}
