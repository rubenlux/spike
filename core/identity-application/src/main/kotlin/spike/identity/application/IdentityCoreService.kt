package spike.identity.application

import spike.crypto.CanonicalIdentityEncoder
import spike.crypto.IdentityCryptoProvider
import spike.identity.contracts.IdentityCommandDisposition
import spike.identity.contracts.IdentityCommandResult
import spike.identity.contracts.IdentityCore
import spike.identity.contracts.IdentityEventListener
import spike.identity.contracts.VerificationResult
import spike.identity.domain.Challenge
import spike.identity.domain.ChallengeContext
import spike.identity.domain.DomainTag
import spike.identity.domain.IdentityDocument
import spike.identity.domain.IdentityEvent
import spike.identity.domain.IdentityProof
import spike.identity.domain.KeyAlgorithm
import spike.identity.domain.LocalNodeIdentity
import spike.identity.domain.NodeId
import spike.identity.domain.NodeIdDerivation
import spike.identity.domain.RemoteIdentityValidationState
import spike.identity.domain.RemoteNodeIdentity
import spike.identity.domain.SignatureAlgorithm
import spike.identity.domain.SignatureBytes
import spike.storage.identity.ChallengeRepository
import spike.storage.identity.IdentityEventRepository
import spike.storage.identity.IdentityTransactionManager
import spike.storage.identity.LocalIdentityRepository
import spike.storage.identity.RemoteIdentityRepository

class IdentityCoreService(
    private val cryptoProvider: IdentityCryptoProvider,
    private val encoder: CanonicalIdentityEncoder,
    private val localIdentityRepository: LocalIdentityRepository,
    private val remoteIdentityRepository: RemoteIdentityRepository,
    private val challengeRepository: ChallengeRepository,
    private val eventRepository: IdentityEventRepository,
    private val transactionManager: IdentityTransactionManager,
    private val clock: Clock
) : IdentityCore {
    private val listeners = mutableListOf<IdentityEventListener>()

    override suspend fun createIdentity(): IdentityCommandResult<LocalNodeIdentity> =
        rejected("Identity creation is not wired yet")

    override suspend fun getLocalIdentity(): LocalNodeIdentity? =
        localIdentityRepository.getLocalIdentity()

    override suspend fun getLocalNodeId(): NodeId? =
        localIdentityRepository.getLocalIdentity()?.nodeId

    override suspend fun getIdentityDocument(): IdentityDocument? =
        localIdentityRepository.getLocalIdentity()?.identityDocument

    override suspend fun validateIdentityDocument(document: IdentityDocument): VerificationResult {
        if (document.identityVersion.value != spike.identity.domain.IDENTITY_DOCUMENT_VERSION) {
            return VerificationResult.UNSUPPORTED_VERSION
        }
        if (document.keyAlgorithm != KeyAlgorithm.ED25519 || document.signatureAlgorithm != SignatureAlgorithm.ED25519) {
            return VerificationResult.UNSUPPORTED_ALGORITHM
        }
        val signature = document.documentSignature ?: return VerificationResult.MALFORMED_INPUT

        return runCatching {
            NodeIdDerivation.validateBinding(document.nodeId, document.keyId, document.publicKey)
            val canonical = encoder.encodeIdentityDocumentUnsigned(document.copy(documentSignature = null))
            val valid = cryptoProvider.verify(
                publicKey = document.publicKey,
                domainTag = DomainTag.IDENTITY_DOCUMENT_V1,
                canonicalPayload = canonical,
                signature = signature
            )
            if (valid) VerificationResult.VALID else VerificationResult.INVALID_SIGNATURE
        }.getOrDefault(VerificationResult.INVALID_IDENTITY)
    }

    override suspend fun storeRemoteIdentity(document: IdentityDocument): IdentityCommandResult<RemoteNodeIdentity> {
        val validation = validateIdentityDocument(document)
        if (validation != VerificationResult.VALID) {
            return IdentityCommandResult(IdentityCommandDisposition.REJECTED, null, validation.name)
        }

        val now = clock.nowEpochMillis()
        val remote = RemoteNodeIdentity(
            nodeId = document.nodeId,
            keyId = document.keyId,
            publicKey = document.publicKey,
            identityDocument = document,
            validationState = RemoteIdentityValidationState.VALID,
            firstSeenAtEpochMillis = now,
            lastValidatedAtEpochMillis = now
        )

        transactionManager.transaction {
            remoteIdentityRepository.saveRemoteIdentity(remote)
        }

        return IdentityCommandResult(IdentityCommandDisposition.APPLIED, remote, null)
    }

    override suspend fun getRemoteIdentity(nodeId: NodeId): RemoteNodeIdentity? =
        remoteIdentityRepository.getRemoteIdentity(nodeId)

    override suspend fun createChallenge(
        claimantNodeId: NodeId,
        context: ChallengeContext
    ): IdentityCommandResult<Challenge> =
        rejected("Challenge creation is not wired yet")

    override suspend fun createProof(
        challenge: Challenge,
        verifierNodeId: NodeId,
        context: ChallengeContext
    ): IdentityCommandResult<IdentityProof> =
        rejected("Proof creation is not wired yet")

    override suspend fun verifyProof(proof: IdentityProof): VerificationResult =
        VerificationResult.MALFORMED_INPUT

    override suspend fun sign(domainTag: DomainTag, payload: ByteArray): IdentityCommandResult<SignatureBytes> =
        rejected("Signing is not wired yet")

    override suspend fun verify(
        identityDocument: IdentityDocument,
        domainTag: DomainTag,
        payload: ByteArray,
        signature: SignatureBytes
    ): VerificationResult {
        val identityValidation = validateIdentityDocument(identityDocument)
        if (identityValidation != VerificationResult.VALID) return identityValidation
        val canonical = encoder.encodeApplicationPayload(payload)
        val valid = cryptoProvider.verify(identityDocument.publicKey, domainTag, canonical, signature)
        return if (valid) VerificationResult.VALID else VerificationResult.INVALID_SIGNATURE
    }

    override suspend fun markCompromised(): IdentityCommandResult<LocalNodeIdentity> =
        rejected("Compromise transition is not wired yet")

    override suspend fun retireIdentity(): IdentityCommandResult<LocalNodeIdentity> =
        rejected("Retirement transition is not wired yet")

    override suspend fun validateAtStartup(): IdentityCommandResult<LocalNodeIdentity> =
        rejected("Startup validation is not wired yet")

    override suspend fun recover(): IdentityCommandResult<LocalNodeIdentity> =
        rejected("Recovery is not wired yet")

    override fun subscribeEvents(listener: IdentityEventListener) {
        listeners += listener
    }

    private fun publish(event: IdentityEvent) {
        listeners.forEach { it.onEvent(event) }
    }

    private fun <T> rejected(errorCode: String): IdentityCommandResult<T> =
        IdentityCommandResult(IdentityCommandDisposition.REJECTED, null, errorCode)

    interface Clock {
        fun nowEpochMillis(): Long
    }
}
