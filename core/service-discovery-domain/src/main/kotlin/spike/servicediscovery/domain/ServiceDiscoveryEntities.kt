package spike.servicediscovery.domain

import spike.identity.domain.NodeId

data class ObjectReference(
    val objectId: String,
    val objectHash: String,
    val objectLength: Long,
    val mediaType: String? = null
)

data class PayloadDescriptor(
    val encoding: String = "JSON",
    val schemaId: String? = null,
    val inlinePayload: ByteArray? = null,
    val objectReference: ObjectReference? = null,
    val payloadHash: String,
    val payloadLength: Long
) {
    init {
        require((inlinePayload != null && objectReference == null) || (inlinePayload == null && objectReference != null)) {
            "PayloadDescriptor must have exactly one of inlinePayload or objectReference"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PayloadDescriptor

        if (encoding != other.encoding) return false
        if (schemaId != other.schemaId) return false
        if (inlinePayload != null) {
            if (other.inlinePayload == null) return false
            if (!inlinePayload.contentEquals(other.inlinePayload)) return false
        } else if (other.inlinePayload != null) return false
        if (objectReference != other.objectReference) return false
        if (payloadHash != other.payloadHash) return false
        if (payloadLength != other.payloadLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encoding.hashCode()
        result = 31 * result + (schemaId?.hashCode() ?: 0)
        result = 31 * result + (inlinePayload?.contentHashCode() ?: 0)
        result = 31 * result + (objectReference?.hashCode() ?: 0)
        result = 31 * result + payloadHash.hashCode()
        result = 31 * result + payloadLength.hashCode()
        return result
    }
}

data class ServiceMethodDefinition(
    val methodId: MethodId,
    val methodName: String,
    val requestSchemaId: String? = null,
    val responseSchemaId: String? = null,
    val errorSchemaId: String? = null,
    val semantics: RpcMethodSemantics,
    val requiredCapabilityId: String,
    val maximumInlineRequestBytes: Long = 64 * 1024L, // 64 KB
    val maximumInlineResponseBytes: Long = 64 * 1024L,
    val supportsCancellation: Boolean = true,
    val supportsDeadline: Boolean = true,
    val requiresIdempotencyKey: Boolean = false
)

data class ServiceContract(
    val contractId: ContractId,
    val contractVersion: ContractVersion,
    val schemaFormat: String = "JSON_SCHEMA",
    val methodDefinitions: List<ServiceMethodDefinition>,
    val contractHash: ContractHash,
    val createdAt: Long
)

data class ServiceDefinition(
    val serviceId: ServiceId,
    val displayName: String,
    val description: String? = null,
    val contractId: ContractId,
    val supportedVersions: List<ContractVersion>,
    val methods: List<ServiceMethodDefinition>,
    val createdAt: Long,
    val updatedAt: Long
)

data class LocalServiceRegistration(
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val contractId: ContractId,
    val supportedVersions: List<ContractVersion>,
    val state: LocalServiceState = LocalServiceState.REGISTERED,
    val registeredAt: Long,
    val updatedAt: Long,
    val advertisementVersion: AdvertisementVersion = AdvertisementVersion(1L)
)

data class ServiceAdvertisement(
    val advertisementId: AdvertisementId = AdvertisementId.generate(),
    val advertisementVersion: AdvertisementVersion,
    val providerNodeId: NodeId,
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val contractId: ContractId,
    val supportedVersions: List<ContractVersion>,
    val contractHashes: Map<ContractVersion, ContractHash>,
    val methodSummary: List<String>,
    val availabilityTtl: AvailabilityTtl,
    val advertisedAt: Long,
    val expiresAt: Long,
    val metadata: Map<String, String> = emptyMap()
)

data class RemoteServiceRecord(
    val providerNodeId: NodeId,
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val contractId: ContractId,
    val supportedVersions: List<ContractVersion>,
    val contractHashes: Map<ContractVersion, ContractHash>,
    val methodSummary: List<String>,
    val latestAdvertisementVersion: AdvertisementVersion,
    val availabilityState: ServiceAvailabilityState = ServiceAvailabilityState.AVAILABLE,
    val lastAdvertisedAt: Long,
    val lastSeenAt: Long,
    val expiresAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

data class NegotiatedContract(
    val providerNodeId: NodeId,
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val contractId: ContractId,
    val selectedVersion: ContractVersion,
    val localContractHash: ContractHash,
    val remoteContractHash: ContractHash,
    val negotiatedAt: Long,
    val expiresAt: Long
)

data class RpcInvocation(
    val invocationId: InvocationId = InvocationId.generate(),
    val callerNodeId: NodeId,
    val providerNodeId: NodeId,
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val contractId: ContractId,
    val contractVersion: ContractVersion,
    val methodId: MethodId,
    val methodSemantics: RpcMethodSemantics,
    val idempotencyKey: IdempotencyKey? = null,
    val requestPayloadDescriptor: PayloadDescriptor,
    val state: RpcInvocationState = RpcInvocationState.CREATED,
    val createdAt: Long,
    val deadlineAt: Deadline? = null,
    val expiresAt: Long,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val resultId: String? = null,
    val cancellationState: CancellationState? = null,
    val authorizationDecisionId: String? = null
)

data class RpcExecution(
    val executionId: ExecutionId = ExecutionId.generate(),
    val invocationId: InvocationId,
    val callerNodeId: NodeId,
    val providerNodeId: NodeId,
    val serviceId: ServiceId,
    val serviceInstanceId: ServiceInstanceId,
    val methodId: MethodId,
    val methodSemantics: RpcMethodSemantics,
    val idempotencyKey: IdempotencyKey? = null,
    val state: RpcExecutionState = RpcExecutionState.RECEIVED,
    val authorizationDecisionId: String? = null,
    val requestReceivedAt: Long,
    val acceptedAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val resultId: String? = null,
    val failureCode: RpcErrorCode? = null
)

data class RpcResult(
    val resultId: String,
    val invocationId: InvocationId,
    val executionId: ExecutionId,
    val status: RpcResultStatus,
    val responsePayloadDescriptor: PayloadDescriptor? = null,
    val errorDescriptor: String? = null,
    val createdAt: Long,
    val durableAt: Long? = null,
    val acknowledgedAt: Long? = null
)

data class RpcCancellation(
    val invocationId: InvocationId,
    val requestedByNodeId: NodeId,
    val reason: CancellationReason,
    val requestedAt: Long,
    val state: CancellationState = CancellationState.REQUESTED,
    val acknowledgedAt: Long? = null,
    val outcome: CancellationOutcome? = null
)

data class IdempotencyRecord(
    val callerNodeId: NodeId,
    val serviceId: ServiceId,
    val methodId: MethodId,
    val idempotencyKey: IdempotencyKey,
    val invocationId: InvocationId,
    val executionId: ExecutionId,
    val requestHash: String,
    val state: RpcExecutionState,
    val resultId: String? = null,
    val createdAt: Long,
    val expiresAt: Long
)
