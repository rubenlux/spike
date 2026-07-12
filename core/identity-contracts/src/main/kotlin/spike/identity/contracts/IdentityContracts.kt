package spike.identity.contracts

import spike.identity.domain.Challenge
import spike.identity.domain.ChallengeContext
import spike.identity.domain.IdentityDocument
import spike.identity.domain.IdentityEvent
import spike.identity.domain.IdentityProof
import spike.identity.domain.LocalNodeIdentity
import spike.identity.domain.NodeId
import spike.identity.domain.RemoteNodeIdentity
import spike.identity.domain.SignatureBytes

enum class VerificationResult {
    VALID,
    INVALID_SIGNATURE,
    INVALID_IDENTITY,
    UNSUPPORTED_ALGORITHM,
    UNSUPPORTED_VERSION,
    MALFORMED_INPUT
}

enum class IdentityCommandDisposition {
    APPLIED,
    ALREADY_APPLIED,
    REJECTED
}

data class IdentityCommandResult<T>(
    val disposition: IdentityCommandDisposition,
    val value: T?,
    val errorCode: String?
)

interface IdentityEventListener {
    fun onEvent(event: IdentityEvent)
}

interface IdentityCore {
    suspend fun createIdentity(): IdentityCommandResult<LocalNodeIdentity>

    suspend fun getLocalIdentity(): LocalNodeIdentity?

    suspend fun getLocalNodeId(): NodeId?

    suspend fun getIdentityDocument(): IdentityDocument?

    suspend fun validateIdentityDocument(document: IdentityDocument): VerificationResult

    suspend fun storeRemoteIdentity(document: IdentityDocument): IdentityCommandResult<RemoteNodeIdentity>

    suspend fun getRemoteIdentity(nodeId: NodeId): RemoteNodeIdentity?

    suspend fun createChallenge(claimantNodeId: NodeId, context: ChallengeContext): IdentityCommandResult<Challenge>

    suspend fun createProof(
        challenge: Challenge,
        verifierNodeId: NodeId,
        context: ChallengeContext
    ): IdentityCommandResult<IdentityProof>

    suspend fun verifyProof(proof: IdentityProof): VerificationResult

    suspend fun sign(domainTag: spike.identity.domain.DomainTag, payload: ByteArray): IdentityCommandResult<SignatureBytes>

    suspend fun verify(
        identityDocument: IdentityDocument,
        domainTag: spike.identity.domain.DomainTag,
        payload: ByteArray,
        signature: SignatureBytes
    ): VerificationResult

    suspend fun markCompromised(): IdentityCommandResult<LocalNodeIdentity>

    suspend fun retireIdentity(): IdentityCommandResult<LocalNodeIdentity>

    suspend fun validateAtStartup(): IdentityCommandResult<LocalNodeIdentity>

    suspend fun recover(): IdentityCommandResult<LocalNodeIdentity>

    fun subscribeEvents(listener: IdentityEventListener)
}
