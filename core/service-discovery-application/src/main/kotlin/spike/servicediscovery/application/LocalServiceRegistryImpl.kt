package spike.servicediscovery.application

import spike.servicediscovery.contracts.LocalServiceRegistry
import spike.servicediscovery.contracts.ServiceHandler
import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository
import java.util.concurrent.ConcurrentHashMap

class LocalServiceRegistryImpl(
    private val repository: RpcRuntimeRepository
) : LocalServiceRegistry {

    private val handlers = ConcurrentHashMap<ServiceId, ServiceHandler>()

    override suspend fun registerContract(contract: ServiceContract) {
        repository.saveContract(contract)
    }

    override suspend fun getContract(contractId: ContractId, version: ContractVersion): ServiceContract? {
        return repository.getContract(contractId, version)
    }

    override suspend fun registerService(definition: ServiceDefinition, handler: ServiceHandler): LocalServiceRegistration {
        val now = System.currentTimeMillis()
        repository.saveServiceDefinition(definition)

        val registration = LocalServiceRegistration(
            serviceId = definition.serviceId,
            serviceInstanceId = ServiceInstanceId.generate(),
            contractId = definition.contractId,
            supportedVersions = definition.supportedVersions,
            state = LocalServiceState.REGISTERED,
            registeredAt = now,
            updatedAt = now,
            advertisementVersion = AdvertisementVersion(1L)
        )

        repository.saveLocalRegistration(registration)
        handlers[definition.serviceId] = handler
        return registration
    }

    override suspend fun setServiceAvailable(serviceId: ServiceId): LocalServiceRegistration {
        val registration = getRegistrationOrThrow(serviceId)
        val now = System.currentTimeMillis()
        val updated = registration.copy(
            state = LocalServiceState.AVAILABLE,
            updatedAt = now,
            advertisementVersion = AdvertisementVersion(registration.advertisementVersion.value + 1L)
        )
        repository.saveLocalRegistration(updated)
        return updated
    }

    override suspend fun pauseService(serviceId: ServiceId): LocalServiceRegistration {
        val registration = getRegistrationOrThrow(serviceId)
        val now = System.currentTimeMillis()
        val updated = registration.copy(
            state = LocalServiceState.PAUSED,
            updatedAt = now
        )
        repository.saveLocalRegistration(updated)
        return updated
    }

    override suspend fun removeService(serviceId: ServiceId): LocalServiceRegistration {
        val registration = getRegistrationOrThrow(serviceId)
        val now = System.currentTimeMillis()
        val updated = registration.copy(
            state = LocalServiceState.UNREGISTERED,
            updatedAt = now,
            advertisementVersion = AdvertisementVersion(registration.advertisementVersion.value + 1L)
        )
        repository.saveLocalRegistration(updated)
        handlers.remove(serviceId)
        return updated
    }

    override suspend fun getLocalRegistration(serviceId: ServiceId): LocalServiceRegistration? {
        return repository.getLocalRegistration(serviceId)
    }

    override suspend fun getServiceHandler(serviceId: ServiceId): ServiceHandler? {
        return handlers[serviceId]
    }

    override suspend fun listLocalServices(): List<LocalServiceRegistration> {
        return repository.listLocalRegistrations()
    }

    private suspend fun getRegistrationOrThrow(serviceId: ServiceId): LocalServiceRegistration {
        return repository.getLocalRegistration(serviceId)
            ?: throw ServiceRuntimeError.ServiceNotFound(serviceId)
    }
}
