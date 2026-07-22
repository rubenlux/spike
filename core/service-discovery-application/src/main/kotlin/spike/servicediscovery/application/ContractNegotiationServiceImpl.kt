package spike.servicediscovery.application

import spike.identity.domain.NodeId
import spike.servicediscovery.contracts.ContractNegotiationService
import spike.servicediscovery.domain.*
import spike.servicediscovery.storage.RpcRuntimeRepository

class ContractNegotiationServiceImpl(
    private val repository: RpcRuntimeRepository
) : ContractNegotiationService {

    override suspend fun negotiateContract(
        providerNodeId: NodeId,
        serviceId: ServiceId,
        clientRequiredVersion: ContractVersion
    ): NegotiatedContract {
        val remoteRecord = repository.getRemoteServiceRecord(providerNodeId, serviceId)
            ?: throw ServiceRuntimeError.ServiceNotFound(serviceId)

        // Select highest compatible remote version matching clientRequiredVersion
        val compatibleVersion = remoteRecord.supportedVersions
            .filter { it.isCompatibleWith(clientRequiredVersion) }
            .maxByOrNull { it.minor }
            ?: throw ServiceRuntimeError.ContractNotSupported(remoteRecord.contractId, clientRequiredVersion)

        val localContract = repository.getContract(remoteRecord.contractId, compatibleVersion)
        val remoteHash = remoteRecord.contractHashes[compatibleVersion]
            ?: throw ServiceRuntimeError.ContractNotSupported(remoteRecord.contractId, compatibleVersion)

        if (localContract != null && localContract.contractHash != remoteHash) {
            throw ServiceRuntimeError.ContractDefinitionMismatch(remoteRecord.contractId, localContract.contractHash, remoteHash)
        }

        val now = System.currentTimeMillis()
        val negotiated = NegotiatedContract(
            providerNodeId = providerNodeId,
            serviceId = serviceId,
            serviceInstanceId = remoteRecord.serviceInstanceId,
            contractId = remoteRecord.contractId,
            selectedVersion = compatibleVersion,
            localContractHash = localContract?.contractHash ?: remoteHash,
            remoteContractHash = remoteHash,
            negotiatedAt = now,
            expiresAt = remoteRecord.expiresAt
        )

        // Contract negotiation is purely descriptive. Does not grant trust or capability authorization.
        repository.saveNegotiatedContract(negotiated)
        return negotiated
    }
}
