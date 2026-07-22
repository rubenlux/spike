package spike.servicediscovery.application

import spike.identity.domain.NodeId
import spike.servicediscovery.contracts.*
import spike.servicediscovery.domain.*
import java.util.concurrent.CopyOnWriteArrayList

class OfflineServiceRuntimeImpl(
    private val localServiceRegistry: LocalServiceRegistryImpl,
    private val advertisementService: ServiceAdvertisementServiceImpl,
    private val negotiationService: ContractNegotiationServiceImpl,
    private val invocationService: RpcInvocationServiceImpl,
    private val executionService: RpcExecutionServiceImpl,
    private val recoveryService: ServiceRuntimeRecoveryServiceImpl
) : OfflineServiceRuntime {

    private val listeners = CopyOnWriteArrayList<ServiceRuntimeEventListener>()

    override suspend fun registerContract(contract: ServiceContract) {
        localServiceRegistry.registerContract(contract)
    }

    override suspend fun getContract(contractId: ContractId, version: ContractVersion): ServiceContract? {
        return localServiceRegistry.getContract(contractId, version)
    }

    override suspend fun registerService(definition: ServiceDefinition, handler: ServiceHandler): LocalServiceRegistration {
        val reg = localServiceRegistry.registerService(definition, handler)
        notifyListeners("LOCAL_SERVICE_REGISTERED", mapOf("serviceId" to definition.serviceId.value))
        return reg
    }

    override suspend fun setServiceAvailable(serviceId: ServiceId): LocalServiceRegistration {
        val reg = localServiceRegistry.setServiceAvailable(serviceId)
        notifyListeners("LOCAL_SERVICE_AVAILABLE", mapOf("serviceId" to serviceId.value))
        return reg
    }

    override suspend fun pauseService(serviceId: ServiceId): LocalServiceRegistration {
        val reg = localServiceRegistry.pauseService(serviceId)
        notifyListeners("LOCAL_SERVICE_PAUSED", mapOf("serviceId" to serviceId.value))
        return reg
    }

    override suspend fun removeService(serviceId: ServiceId): LocalServiceRegistration {
        val reg = localServiceRegistry.removeService(serviceId)
        notifyListeners("LOCAL_SERVICE_REMOVED", mapOf("serviceId" to serviceId.value))
        return reg
    }

    override suspend fun getLocalService(serviceId: ServiceId): LocalServiceRegistration? {
        return localServiceRegistry.getLocalRegistration(serviceId)
    }

    override suspend fun listLocalServices(): List<LocalServiceRegistration> {
        return localServiceRegistry.listLocalServices()
    }

    override suspend fun publishAdvertisement(serviceId: ServiceId): ServiceAdvertisement {
        val adv = advertisementService.publishAdvertisement(serviceId)
        notifyListeners("SERVICE_ADVERTISEMENT_SENT", mapOf("serviceId" to serviceId.value))
        return adv
    }

    override suspend fun processAdvertisement(providerNodeId: NodeId, advertisement: ServiceAdvertisement): RemoteServiceRecord {
        val rec = advertisementService.processAdvertisement(providerNodeId, advertisement)
        notifyListeners("SERVICE_ADVERTISEMENT_RECEIVED", mapOf("providerNodeId" to providerNodeId.value, "serviceId" to advertisement.serviceId.value))
        return rec
    }

    override suspend fun queryRemoteServices(
        providerNodeId: NodeId?,
        serviceId: ServiceId?,
        contractId: ContractId?,
        availabilityState: ServiceAvailabilityState?
    ): List<RemoteServiceRecord> {
        return advertisementService.queryRemoteServices(providerNodeId, serviceId, contractId, availabilityState)
    }

    override suspend fun getRemoteService(providerNodeId: NodeId, serviceId: ServiceId): RemoteServiceRecord? {
        return advertisementService.getRemoteService(providerNodeId, serviceId)
    }

    override suspend fun negotiateContract(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        clientRequiredVersion: ContractVersion
    ): NegotiatedContract {
        val neg = negotiationService.negotiateContract(providerNodeId, serviceId, clientRequiredVersion)
        notifyListeners("CONTRACT_NEGOTIATED", mapOf("serviceId" to serviceId.value, "version" to clientRequiredVersion.value))
        return neg
    }

    override suspend fun createInvocation(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey?,
        deadline: Deadline?
    ): RpcInvocation {
        val inv = invocationService.createInvocation(providerNodeId, serviceId, methodId, requestPayload, idempotencyKey, deadline)
        notifyListeners("RPC_INVOCATION_CREATED", mapOf("invocationId" to inv.invocationId.value))
        return inv
    }

    override suspend fun invoke(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        methodId: MethodId,
        requestPayload: PayloadDescriptor,
        idempotencyKey: IdempotencyKey?,
        deadline: Deadline?
    ): RpcResult {
        return invocationService.invoke(providerNodeId, serviceId, methodId, requestPayload, idempotencyKey, deadline)
    }

    override suspend fun getInvocation(invocationId: InvocationId): RpcInvocation? {
        return invocationService.getInvocation(invocationId)
    }

    override suspend fun cancelInvocation(invocationId: InvocationId, reason: CancellationReason): RpcCancellation {
        val canc = invocationService.cancelInvocation(invocationId, reason)
        notifyListeners("RPC_CANCELLATION_REQUESTED", mapOf("invocationId" to invocationId.value))
        return canc
    }

    override suspend fun processInboundRequest(requestInvocation: RpcInvocation): RpcResult {
        return executionService.processInboundRequest(requestInvocation)
    }

    override suspend fun getExecution(executionId: ExecutionId): RpcExecution? {
        return executionService.getExecution(executionId)
    }

    override suspend fun getResult(invocationId: InvocationId): RpcResult? {
        return invocationService.getResult(invocationId)
    }

    override suspend fun recover() {
        recoveryService.recover()
        notifyListeners("RPC_RECOVERY_COMPLETED", emptyMap())
    }

    override fun subscribeServiceEvents(listener: ServiceRuntimeEventListener) {
        listeners.add(listener)
    }

    private fun notifyListeners(eventType: String, details: Map<String, String>) {
        listeners.forEach { it.onServiceRuntimeEvent(eventType, details) }
    }
}
