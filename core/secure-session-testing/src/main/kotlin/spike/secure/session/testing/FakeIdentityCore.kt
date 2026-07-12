package spike.secure.session.testing

import spike.identity.contracts.*
import spike.identity.domain.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.UUID

/**
 * Fake IdentityCore for testing SecureSessionCore.
 *
 * Signs using HMAC-SHA-256(privateKeyBytes, domainTag.wireName || payload) — not real Ed25519.
 * The 32-byte output is padded to 64 bytes to satisfy the wire codec's signature size requirement.
 *
 * Verification is done by re-computing the same HMAC using the stored public key bytes as key
 * (mirroring the setup: private = HMAC key, public = same bytes for test symmetry).
 */
class FakeIdentityCore(
    nodeIdHex: String,
    private val signingKeyBytes: ByteArray = ByteArray(32) { it.toByte() }
) : IdentityCore {

    private val nodeId = NodeId("node:v1:sha256:$nodeIdHex")
    private val keyId = KeyId("sha256:$nodeIdHex")
    private val publicKeyBytes = PublicKeyBytes(KeyAlgorithm.ED25519, signingKeyBytes.copyOf())
    private val identityDocument = IdentityDocument(
        identityVersion = IdentityVersion(1),
        nodeId = nodeId,
        keyId = keyId,
        keyAlgorithm = KeyAlgorithm.ED25519,
        signatureAlgorithm = SignatureAlgorithm.ED25519,
        publicKey = publicKeyBytes,
        createdAtEpochMillis = 0L,
        capabilities = emptyMap(),
        documentSignature = null
    )
    private val localIdentity = LocalNodeIdentity(
        nodeId = nodeId,
        keyId = keyId,
        identityVersion = IdentityVersion(1),
        keyAlgorithm = KeyAlgorithm.ED25519,
        publicKey = publicKeyBytes,
        privateKeyHandle = PrivateKeyHandle("fake-handle"),
        identityDocument = identityDocument,
        state = LocalIdentityState.ACTIVE,
        createdAtEpochMillis = 0L,
        protectionLevel = KeyProtectionLevel.SOFTWARE
    )

    // Registry of known remote identities
    private val remoteIdentities = mutableMapOf<String, RemoteNodeIdentity>()

    fun registerRemote(other: FakeIdentityCore) {
        val doc = other.identityDocument
        remoteIdentities[other.nodeId.value] = RemoteNodeIdentity(
            nodeId = other.nodeId,
            keyId = other.keyId,
            publicKey = other.publicKeyBytes,
            identityDocument = doc,
            validationState = RemoteIdentityValidationState.VALID,
            firstSeenAtEpochMillis = 0L,
            lastValidatedAtEpochMillis = 0L
        )
    }

    override suspend fun getLocalNodeId(): NodeId = nodeId

    override suspend fun getLocalIdentity(): LocalNodeIdentity = localIdentity

    override suspend fun getIdentityDocument(): IdentityDocument = identityDocument

    override suspend fun sign(domainTag: DomainTag, payload: ByteArray): IdentityCommandResult<SignatureBytes> {
        val sig = fakeSign(signingKeyBytes, domainTag.wireName.encodeToByteArray() + payload)
        return IdentityCommandResult(IdentityCommandDisposition.APPLIED, SignatureBytes(SignatureAlgorithm.ED25519, sig), null)
    }

    override suspend fun verify(
        identityDocument: IdentityDocument,
        domainTag: DomainTag,
        payload: ByteArray,
        signature: SignatureBytes
    ): VerificationResult {
        // Find the key bytes by nodeId
        val expectedKey = if (identityDocument.nodeId == nodeId) {
            signingKeyBytes
        } else {
            remoteIdentities[identityDocument.nodeId.value]?.publicKey?.bytes
                ?: return VerificationResult.INVALID_IDENTITY
        }
        val expected = fakeSign(expectedKey, domainTag.wireName.encodeToByteArray() + payload)
        return if (expected.contentEquals(signature.bytes)) VerificationResult.VALID
        else VerificationResult.INVALID_SIGNATURE
    }

    override suspend fun getRemoteIdentity(nodeId: NodeId): RemoteNodeIdentity? =
        remoteIdentities[nodeId.value]

    override suspend fun storeRemoteIdentity(document: IdentityDocument): IdentityCommandResult<RemoteNodeIdentity> {
        val remote = RemoteNodeIdentity(
            nodeId = document.nodeId,
            keyId = document.keyId,
            publicKey = document.publicKey,
            identityDocument = document,
            validationState = RemoteIdentityValidationState.VALID,
            firstSeenAtEpochMillis = System.currentTimeMillis(),
            lastValidatedAtEpochMillis = null
        )
        remoteIdentities[document.nodeId.value] = remote
        return IdentityCommandResult(IdentityCommandDisposition.APPLIED, remote, null)
    }

    override suspend fun validateIdentityDocument(document: IdentityDocument): VerificationResult =
        VerificationResult.VALID

    override suspend fun createIdentity(): IdentityCommandResult<LocalNodeIdentity> =
        IdentityCommandResult(IdentityCommandDisposition.APPLIED, localIdentity, null)

    override suspend fun createChallenge(
        claimantNodeId: NodeId,
        context: ChallengeContext
    ): IdentityCommandResult<Challenge> {
        val ch = Challenge(
            challengeId = ChallengeId(UUID.randomUUID().toString()),
            challengeHash = Sha256Digest("a".repeat(64)),
            randomBytes = ChallengeBytes(ByteArray(32)),
            claimantNodeId = claimantNodeId,
            context = context,
            issuedAtEpochMillis = System.currentTimeMillis(),
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000L,
            state = ChallengeState.ISSUED
        )
        return IdentityCommandResult(IdentityCommandDisposition.APPLIED, ch, null)
    }

    override suspend fun createProof(
        challenge: Challenge,
        verifierNodeId: NodeId,
        context: ChallengeContext
    ): IdentityCommandResult<IdentityProof> {
        val proof = IdentityProof(
            proofVersion = 1,
            challengeId = challenge.challengeId,
            challenge = challenge.randomBytes,
            verifierNodeId = verifierNodeId,
            claimantNodeId = nodeId,
            context = context,
            createdAtEpochMillis = System.currentTimeMillis(),
            signature = SignatureBytes(SignatureAlgorithm.ED25519, ByteArray(64))
        )
        return IdentityCommandResult(IdentityCommandDisposition.APPLIED, proof, null)
    }

    override suspend fun verifyProof(proof: IdentityProof): VerificationResult = VerificationResult.VALID

    override suspend fun markCompromised(): IdentityCommandResult<LocalNodeIdentity> =
        IdentityCommandResult(IdentityCommandDisposition.APPLIED, localIdentity, null)

    override suspend fun retireIdentity(): IdentityCommandResult<LocalNodeIdentity> =
        IdentityCommandResult(IdentityCommandDisposition.APPLIED, localIdentity, null)

    override suspend fun validateAtStartup(): IdentityCommandResult<LocalNodeIdentity> =
        IdentityCommandResult(IdentityCommandDisposition.APPLIED, localIdentity, null)

    override suspend fun recover(): IdentityCommandResult<LocalNodeIdentity> =
        IdentityCommandResult(IdentityCommandDisposition.APPLIED, localIdentity, null)

    override fun subscribeEvents(listener: IdentityEventListener) { /* no-op for tests */ }

    private fun fakeSign(keyBytes: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val hmac32 = mac.doFinal(data) // 32 bytes
        // Double and return as 64-byte signature
        return hmac32 + hmac32
    }
}
