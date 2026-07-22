package spike.servicediscovery.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId
import spike.servicediscovery.contracts.ServiceHandler
import spike.servicediscovery.domain.*

class RpcExecutionServiceTest {

    private lateinit var repository: InMemoryRpcRuntimeRepository
    private lateinit var fakeSecurityCore: FakeLocalPeerSecurityCore
    private lateinit var registry: LocalServiceRegistryImpl
    private lateinit var executionService: RpcExecutionServiceImpl
    private lateinit var recoveryService: ServiceRuntimeRecoveryServiceImpl

    private val providerNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    private val callerNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    private val serviceId = ServiceId("app.mesh.file-index")
    private val contractId = ContractId("app.mesh.file-index.contract")
    private val methodId = MethodId("app.mesh.file-index/search")
    private val capId = "service.app.mesh.file-index.search.invoke"

    private var handlerExecutionCount = 0

    @BeforeEach
    fun setUp() = runBlocking {
        repository = InMemoryRpcRuntimeRepository()
        fakeSecurityCore = FakeLocalPeerSecurityCore()
        registry = LocalServiceRegistryImpl(repository)
        executionService = RpcExecutionServiceImpl(providerNode, repository, registry, fakeSecurityCore)
        recoveryService = ServiceRuntimeRecoveryServiceImpl(repository)

        handlerExecutionCount = 0

        val method = ServiceMethodDefinition(
            methodId = methodId,
            methodName = "search",
            semantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            requiredCapabilityId = capId
        )

        val definition = ServiceDefinition(
            serviceId = serviceId,
            displayName = "File Index",
            contractId = contractId,
            supportedVersions = listOf(ContractVersion("1.0")),
            methods = listOf(method),
            createdAt = 100L,
            updatedAt = 100L
        )

        val handler = ServiceHandler { _, request ->
            handlerExecutionCount++
            PayloadDescriptor(
                inlinePayload = "{\"results\":[]}".toByteArray(),
                payloadHash = "res_hash",
                payloadLength = 14L
            )
        }

        registry.registerService(definition, handler)
        Unit
    }

    @Test
    fun `SPEC-009 authorization denial blocks handler execution and records REJECTED result`() = runBlocking {
        // Capability capId is NOT in fakeSecurityCore.allowedCapabilities
        val invocation = createSampleInvocation()

        var caughtError: ServiceRuntimeError.AuthorizationDenied? = null
        try {
            executionService.processInboundRequest(invocation)
        } catch (e: ServiceRuntimeError.AuthorizationDenied) {
            caughtError = e
        }

        assertNotNull(caughtError)
        assertEquals(0, handlerExecutionCount)
        assertTrue(caughtError!!.message!!.contains("DENIED_NO_MATCHING_GRANT"))

        val execution = repository.findExecutionByInvocation(callerNode, invocation.invocationId)
        assertNotNull(execution)
        assertEquals(RpcExecutionState.REJECTED, execution?.state)

        val result = repository.getResultByInvocation(invocation.invocationId)
        assertEquals(RpcResultStatus.AUTHORIZATION_DENIED, result?.status)
    }

    @Test
    fun `SPEC-009 authorization approval executes handler and commits SUCCESS result`() = runBlocking {
        fakeSecurityCore.allowedCapabilities.add(capId)
        val invocation = createSampleInvocation()

        val result = executionService.processInboundRequest(invocation)

        assertEquals(1, handlerExecutionCount)
        assertEquals(RpcResultStatus.SUCCESS, result.status)

        val execution = repository.findExecutionByInvocation(callerNode, invocation.invocationId)
        assertEquals(RpcExecutionState.COMPLETED, execution?.state)
    }

    @Test
    fun `Durable deduplication returns cached result on duplicate invocation without re-executing handler`() = runBlocking {
        fakeSecurityCore.allowedCapabilities.add(capId)
        val invocation = createSampleInvocation()

        val result1 = executionService.processInboundRequest(invocation)
        assertEquals(1, handlerExecutionCount)

        // Resend identical invocation
        val result2 = executionService.processInboundRequest(invocation)
        assertEquals(1, handlerExecutionCount) // Zero additional executions
        assertEquals(result1.resultId, result2.resultId)
    }

    @Test
    fun `Crash recovery marks interrupted NON_IDEMPOTENT_COMMAND as UNKNOWN_OUTCOME without re-execution`() = runBlocking {
        fakeSecurityCore.allowedCapabilities.add(capId)
        val invocation = createSampleInvocation()

        // Simulate interrupted execution in state RUNNING
        val execution = RpcExecution(
            invocationId = invocation.invocationId,
            callerNodeId = callerNode,
            providerNodeId = providerNode,
            serviceId = serviceId,
            serviceInstanceId = ServiceInstanceId("inst1"),
            methodId = methodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            state = RpcExecutionState.RUNNING,
            requestReceivedAt = 100L
        )
        repository.saveExecution(execution)
        repository.saveInvocation(invocation.copy(state = RpcInvocationState.EXECUTING))

        recoveryService.recover()

        val recoveredExecution = repository.getExecution(execution.executionId)
        assertEquals(RpcExecutionState.UNKNOWN_OUTCOME, recoveredExecution?.state)

        val recoveredResult = repository.getResultByInvocation(invocation.invocationId)
        assertEquals(RpcResultStatus.UNKNOWN_OUTCOME, recoveredResult?.status)


        // Attempting to re-process the same invocation MUST return the UNKNOWN_OUTCOME result
        // and MUST NOT re-execute the handler (handler execution count stays at 0)
        val unknownResult = executionService.processInboundRequest(invocation)
        assertEquals(RpcResultStatus.UNKNOWN_OUTCOME, unknownResult.status)
        assertEquals(0, handlerExecutionCount)
    }

    private fun createSampleInvocation(): RpcInvocation {
        return RpcInvocation(
            callerNodeId = callerNode,
            providerNodeId = providerNode,
            serviceId = serviceId,
            serviceInstanceId = ServiceInstanceId("inst1"),
            contractId = contractId,
            contractVersion = ContractVersion("1.0"),
            methodId = methodId,
            methodSemantics = RpcMethodSemantics.NON_IDEMPOTENT_COMMAND,
            requestPayloadDescriptor = PayloadDescriptor(
                inlinePayload = "{\"query\":\"test\"}".toByteArray(),
                payloadHash = "req_hash",
                payloadLength = 16L
            ),
            state = RpcInvocationState.PERSISTED,
            createdAt = 100L,
            expiresAt = 1000L
        )
    }
}
