package spike.peerdirectory.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*
import spike.security.domain.TrustState

class TrustEstablishmentServiceTest {

    private lateinit var repository: InMemoryPeerIdentityRepository
    private lateinit var fakeSecurityCore: FakeLocalPeerSecurityCore
    private lateinit var trustService: TrustEstablishmentServiceImpl

    private val peerNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    @BeforeEach
    fun setUp() {
        repository = InMemoryPeerIdentityRepository()
        fakeSecurityCore = FakeLocalPeerSecurityCore()
        trustService = TrustEstablishmentServiceImpl(repository, fakeSecurityCore)
    }

    @Test
    fun `establishTrust creates VERIFIED binding and delegates trustPeer to SPEC-009`() = runBlocking {
        val humanId = HumanIdentityId.generate()
        repository.saveHumanIdentity(HumanIdentity(humanIdentityId = humanId, localDisplayName = "Rubén", createdAt = 100L, updatedAt = 100L))

        val evidence = VerificationEvidence(
            verificationSessionId = VerificationSessionId.generate(),
            localNodeId = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111"),
            remoteNodeId = peerNode,
            method = VerificationMethod.QR,
            result = VerificationResult.VERIFIED,
            sessionBindingHash = "binding_hash_123",
            transcriptHash = VerificationTranscriptHash("transcript_123"),
            identityFingerprint = ReadableFingerprint("raw_fp", "AB7K 91QM X2RT 6NLP 4D8W K3YZ 7HCV M9PE 2TQF 5R6J"),
            verifiedAt = 1000L
        )
        repository.saveVerificationEvidence(evidence)

        val binding = trustService.establishTrust(humanId, peerNode, evidence.verificationEvidenceId)

        assertEquals(BindingState.VERIFIED, binding.state)
        assertEquals(TrustEstablishmentMode.VERIFIED_QR, binding.trustEstablishmentMode)

        // Verify SPEC-009 security core state delegation
        assertEquals(TrustState.TRUSTED, fakeSecurityCore.peerTrustStates[peerNode])
    }

    @Test
    fun `establishUnverifiedTrustOverride creates UNVERIFIED_TRUST_OVERRIDE binding`() = runBlocking {
        val humanId = HumanIdentityId.generate()
        repository.saveHumanIdentity(HumanIdentity(humanIdentityId = humanId, localDisplayName = "Maria", createdAt = 100L, updatedAt = 100L))

        val binding = trustService.establishUnverifiedTrustOverride(humanId, peerNode, "Manual override by user")

        assertEquals(BindingState.TRUSTED_UNVERIFIED, binding.state)
        assertEquals(TrustEstablishmentMode.UNVERIFIED_TRUST_OVERRIDE, binding.trustEstablishmentMode)

        // Verify SPEC-009 security core state delegation
        assertEquals(TrustState.TRUSTED, fakeSecurityCore.peerTrustStates[peerNode])
    }

    @Test
    fun `revokeBinding sets REVOKED state and downgrades SPEC-009 security state`() = runBlocking {
        val humanId = HumanIdentityId.generate()
        repository.saveHumanIdentity(HumanIdentity(humanIdentityId = humanId, localDisplayName = "Rubén", createdAt = 100L, updatedAt = 100L))

        val evidence = VerificationEvidence(
            verificationSessionId = VerificationSessionId.generate(),
            localNodeId = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111"),
            remoteNodeId = peerNode,
            method = VerificationMethod.SAS,
            result = VerificationResult.VERIFIED,
            sessionBindingHash = "binding_hash_123",
            transcriptHash = VerificationTranscriptHash("transcript_123"),
            identityFingerprint = ReadableFingerprint("raw_fp", "AB7K 91QM X2RT 6NLP 4D8W K3YZ 7HCV M9PE 2TQF 5R6J"),
            verifiedAt = 1000L
        )
        repository.saveVerificationEvidence(evidence)

        val binding = trustService.establishTrust(humanId, peerNode, evidence.verificationEvidenceId)
        val revoked = trustService.revokeBinding(binding.bindingId, "SECURITY_REASON")

        assertEquals(BindingState.REVOKED, revoked.state)
        assertEquals(TrustState.OBSERVED, fakeSecurityCore.peerTrustStates[peerNode])
    }
}
