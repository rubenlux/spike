package spike.servicediscovery.contracts

import spike.identity.domain.NodeId
import spike.servicediscovery.domain.*

/** Context provided to an RPC handler during remote execution. */
data class RpcHandlerContext(
    val invocationId: InvocationId,
    val executionId: ExecutionId,
    val callerNodeId: NodeId,
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val contractVersion: ContractVersion,
    val methodId: MethodId,
    val methodSemantics: RpcMethodSemantics,
    val authorizationDecisionId: String?,
    val deadline: Deadline?
)

/** Application handler interface for executing RPC methods. */
fun interface ServiceHandler {
    suspend fun handle(context: RpcHandlerContext, requestPayload: PayloadDescriptor): PayloadDescriptor
}

/** Listener interface for Service Runtime audit event notifications. */
fun interface ServiceRuntimeEventListener {
    fun onServiceRuntimeEvent(eventType: String, details: Map<String, String>)
}

/** Local Service Registry interface. */
interface LocalServiceRegistry {
    suspend fun registerContract(contract: ServiceContract)
    suspend fun getContract(contractId: ContractId, version: ContractVersion): ServiceContract?
    suspend fun registerService(definition: ServiceDefinition, handler: ServiceHandler): LocalServiceRegistration
    suspend fun setServiceAvailable(serviceId: ServiceId): LocalServiceRegistration
    suspend fun pauseService(serviceId: ServiceId): LocalServiceRegistration
    suspend fun removeService(serviceId: ServiceId): LocalServiceRegistration
    suspend fun getLocalRegistration(serviceId: ServiceId): LocalServiceRegistration?
    suspend fun getServiceHandler(serviceId: ServiceId): ServiceHandler?
    suspend fun listLocalServices(): List<LocalServiceRegistration>
}

/** Service Advertisement Service interface. */
interface ServiceAdvertisementService {
    suspend fun publishAdvertisement(serviceId: ServiceId): ServiceAdvertisement
    suspend fun processAdvertisement(providerNodeId: NodeId, advertisement: ServiceAdvertisement): RemoteServiceRecord
    suspend fun queryRemoteServices(
        providerNodeId: NodeId? = null,
        serviceId: ServiceId? = null,
        contractId: ContractId? = null,
        availabilityState: ServiceAvailabilityState? = null
    ): List<RemoteServiceRecord>
    suspend fun getRemoteService(providerNodeId: NodeId, serviceId: ServiceId): RemoteServiceRecord?
}

/** Contract Version Negotiation Service interface. */
interface ContractNegotiationService {
    suspend fun negotiateContract(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        clientRequiredVersion: ContractVersion
    ): NegotiatedContract
}

/** RPC Outbound Invocation Service interface. */
interface RpcInvocationService {
    suspend fun createInvocation(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey? = null,
        deadline: Deadline? = null
    ): RpcInvocation

    suspend fun invoke(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey? = null,
        deadline: Deadline? = null
    ): RpcResult

    suspend fun getInvocation(invocationId: InvocationId): RpcInvocation?
    suspend fun getResult(invocationId: InvocationId): RpcResult?
    suspend fun cancelInvocation(invocationId: InvocationId, reason: CancellationReason): RpcCancellation
}

/** RPC Inbound Execution Service interface. */
interface RpcExecutionService {
    suspend fun processInboundRequest(requestInvocation: RpcInvocation): RpcResult
    suspend fun getExecution(executionId: ExecutionId): RpcExecution?
}

/** Normative administrative public facade contract for Offline Service Runtime per SPEC-011 Section 113. */
interface OfflineServiceRuntime {
    suspend fun registerContract(contract: ServiceContract)
    suspend fun getContract(contractId: ContractId, version: ContractVersion): ServiceContract?
    suspend fun registerService(definition: ServiceDefinition, handler: ServiceHandler): LocalServiceRegistration
    suspend fun setServiceAvailable(serviceId: ServiceId): LocalServiceRegistration
    suspend fun pauseService(serviceId: ServiceId): LocalServiceRegistration
    suspend fun removeService(serviceId: ServiceId): LocalServiceRegistration
    suspend fun getLocalService(serviceId: ServiceId): LocalServiceRegistration?
    suspend fun listLocalServices(): List<LocalServiceRegistration>

    suspend fun publishAdvertisement(serviceId: ServiceId): ServiceAdvertisement
    suspend fun processAdvertisement(providerNodeId: NodeId, advertisement: ServiceAdvertisement): RemoteServiceRecord
    suspend fun queryRemoteServices(
        providerNodeId: NodeId? = null,
        serviceId: ServiceId? = null,
        contractId: ContractId? = null,
        availabilityState: ServiceAvailabilityState? = null
    ): List<RemoteServiceRecord>
    suspend fun getRemoteService(providerNodeId: NodeId, serviceId: ServiceId): RemoteServiceRecord?

    suspend fun negotiateContract(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        clientRequiredVersion: ContractVersion
    ): NegotiatedContract

    suspend fun createInvocation(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey? = null,
        deadline: Deadline? = null
    ): RpcInvocation

    suspend fun invoke(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey? = null,
        deadline: Deadline? = null
    ): RpcResult

    suspend fun getInvocation(invocationId: InvocationId): RpcInvocation?
    suspend fun cancelInvocation(invocationId: InvocationId, reason: CancellationReason): RpcCancellation
    suspend fun processInboundRequest(requestInvocation: RpcInvocation): RpcResult
    suspend fun getExecution(executionId: ExecutionId): RpcExecution?
    suspend fun getResult(invocationId: InvocationId): RpcResult?

    suspend fun recover()
    fun subscribeServiceEvents(listener: ServiceRuntimeEventListener)
}
