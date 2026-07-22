package spike.servicediscovery.application

import spike.identity.domain.NodeId
import spike.servicediscovery.contracts.ServiceAdvertisementService
import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository

class ServiceAdvertisementServiceImpl(
    private val localNodeId: NodeId,
    private val repository: RpcRuntimeRepository,
    private val defaultTtlMs: Long = 600_000L // 10 minutes default
) : ServiceAdvertisementService {

    override suspend fun publishAdvertisement(serviceId: ServiceId): ServiceAdvertisement {
        val registration = repository.getLocalRegistration(serviceId)
            ?: throw ServiceRuntimeError.ServiceNotFound(serviceId)
        val definition = repository.getServiceDefinition(serviceId)
            ?: throw ServiceRuntimeError.ServiceNotFound(serviceId)

        val contractHashes = mutableMapOf<ContractVersion, ContractHash>()
        for (version in registration.supportedVersions) {
            val contract = repository.getContract(registration.contractId, version)
            val hash = if (contract != null) {
                contract.contractHash
            } else {
                ContractHashGenerator.generateHash(
                    contractId = registration.contractId,
                    version = version,
                    schemaFormat = "JSON_SCHEMA",
                    methods = definition.methods
                )
            }
            contractHashes[version] = hash
        }

        val now = System.currentTimeMillis()
        val advertisement = ServiceAdvertisement(
            advertisementVersion = registration.advertisementVersion,
            providerNodeId = localNodeId,
            serviceId = serviceId,
            serviceInstanceId = registration.serviceInstanceId,
            contractId = registration.contractId,
            supportedVersions = registration.supportedVersions,
            contractHashes = contractHashes,
            methodSummary = definition.methods.map { it.methodName },
            availabilityTtl = AvailabilityTtl(defaultTtlMs),
            advertisedAt = now,
            expiresAt = now + defaultTtlMs
        )

        return advertisement
    }

    override suspend fun processAdvertisement(
        providerNodeId: NodeId,
        advertisement: ServiceAdvertisement
    ): RemoteServiceRecord {
        val existing = repository.getRemoteServiceRecord(providerNodeId, advertisement.serviceId)

        // Monotonic advertisement version check
        if (existing != null && advertisement.advertisementVersion.value <= existing.latestAdvertisementVersion.value) {
            return existing
        }

        val now = System.currentTimeMillis()
        val record = RemoteServiceRecord(
            providerNodeId = providerNodeId,
            serviceId = advertisement.serviceId,
            serviceInstanceId = advertisement.serviceInstanceId,
            contractId = advertisement.contractId,
            supportedVersions = advertisement.supportedVersions,
            contractHashes = advertisement.contractHashes,
            methodSummary = advertisement.methodSummary,
            latestAdvertisementVersion = advertisement.advertisementVersion,
            availabilityState = ServiceAvailabilityState.AVAILABLE,
            lastAdvertisedAt = advertisement.advertisedAt,
            lastSeenAt = now,
            expiresAt = advertisement.expiresAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        // Remote service advertisement is strictly descriptive claimed availability.
        // It NEVER grants trust or capability authorization in SPEC-009.
        repository.saveRemoteServiceRecord(record)
        return record
    }

    override suspend fun queryRemoteServices(
        providerNodeId: NodeId?,
        serviceId: ServiceId?,
        contractId: ContractId?,
        availabilityState: ServiceAvailabilityState?
    ): List<RemoteServiceRecord> {
        val records = repository.findRemoteServiceRecords(providerNodeId, serviceId, contractId, availabilityState)
        val now = System.currentTimeMillis()

        return records.map { record ->
            if (record.availabilityState == ServiceAvailabilityState.AVAILABLE && now > record.expiresAt) {
                val stale = record.copy(availabilityState = ServiceAvailabilityState.STALE, updatedAt = now)
                repository.saveRemoteServiceRecord(stale)
                stale
            } else {
                record
            }
        }
    }

    override suspend fun getRemoteService(providerNodeId: NodeId, serviceId: ServiceId): RemoteServiceRecord? {
        val record = repository.getRemoteServiceRecord(providerNodeId, serviceId) ?: return null
        val now = System.currentTimeMillis()
        if (record.availabilityState == ServiceAvailabilityState.AVAILABLE && now > record.expiresAt) {
            val stale = record.copy(availabilityState = ServiceAvailabilityState.STALE, updatedAt = now)
            repository.saveRemoteServiceRecord(stale)
            return stale
        }
        return record
    }
}
