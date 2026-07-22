package spike.peerdirectory.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*

class VerificationSessionServiceTest {

    private lateinit var repository: InMemoryPeerIdentityRepository
    private lateinit var service: VerificationSessionServiceImpl

    private val localNode = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    private val remoteNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    @BeforeEach
    fun setUp() {
        repository = InMemoryPeerIdentityRepository()
        service = VerificationSessionServiceImpl(repository)
    }

    @Test
    fun `QR verification flow creates session, payload, evidence and blocks nonce replay`() = runBlocking {
        val session = service.startVerificationSession(localNode, remoteNode, VerificationMethod.QR)
        assertEquals(VerificationState.CREATED, session.state)

        val payload = service.getQrVerificationPayload(session.verificationSessionId)
        assertNotNull(payload)
        assertEquals(session.verificationSessionId, payload.verificationSessionId)
        assertEquals(localNode, payload.presentingNodeId)
        assertEquals(remoteNode, payload.expectedRemoteNodeId)

        // Swap presenting/expected NodeIds for remote submission
        val remotePayload = payload.copy(
            presentingNodeId = remoteNode,
            expectedRemoteNodeId = localNode
        )

        val evidence = service.submitQrVerificationPayload(session.verificationSessionId, remotePayload)
        assertEquals(VerificationResult.VERIFIED, evidence.result)
        assertEquals(VerificationMethod.QR, evidence.method)

        val updatedSession = repository.getVerificationSession(session.verificationSessionId)
        assertEquals(VerificationState.VERIFIED, updatedSession?.state)

        // Replay submission should fail
        assertThrows<LocalPeerIdentityError.QrVerificationError.QrReplayDetected> {
            service.submitQrVerificationPayload(session.verificationSessionId, remotePayload)
        }
    }

    @Test
    fun `SAS verification flow derives 6 digits plus 3 words and confirms match`() = runBlocking {
        val session = service.startVerificationSession(localNode, remoteNode, VerificationMethod.SAS)

        val sas = service.getSas(session.verificationSessionId)
        assertEquals(6, sas.numericCode.length)
        assertEquals(3, sas.words.size)

        val evidence = service.confirmSasMatch(session.verificationSessionId)
        assertEquals(VerificationResult.VERIFIED, evidence.result)
        assertEquals(VerificationMethod.SAS, evidence.method)
    }

    @Test
    fun `SAS rejection marks session REJECTED`() = runBlocking {
        val session = service.startVerificationSession(localNode, remoteNode, VerificationMethod.SAS)

        service.rejectSas(session.verificationSessionId)

        val updatedSession = repository.getVerificationSession(session.verificationSessionId)
        assertEquals(VerificationState.REJECTED, updatedSession?.state)
    }

    @Test
    fun `Manual fingerprint confirmation produces evidence`() = runBlocking {
        val session = service.startVerificationSession(localNode, remoteNode, VerificationMethod.MANUAL_FINGERPRINT)

        val fp = ReadableFingerprintGenerator.generateFingerprint(remoteNode.value.toByteArray(Charsets.UTF_8))
        val evidence = service.confirmManualFingerprint(session.verificationSessionId, fp)

        assertEquals(VerificationResult.VERIFIED, evidence.result)
        assertEquals(VerificationMethod.MANUAL_FINGERPRINT, evidence.method)
    }
}
