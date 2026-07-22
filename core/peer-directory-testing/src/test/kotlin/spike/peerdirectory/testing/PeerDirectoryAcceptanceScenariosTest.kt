package spike.peerdirectory.testing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*
import spike.security.domain.TrustState

class PeerDirectoryAcceptanceScenariosTest {

    private lateinit var env: TestPeerDirectoryEnvironment

    @BeforeEach
    fun setUp() {
        env = PeerDirectoryTestFixtures.createDefaultEnvironment()
    }

    @Test
    fun `Scenario A - First Encounter & Observation creates OBSERVED entry without trust`() = runBlocking {
        val entry = env.core.observeAuthenticatedPeer(
            PeerDirectoryTestFixtures.peerA,
            mapOf("displayName" to "Rubén Phone", "deviceName" to "Android 14")
        )

        assertEquals("OBSERVED", entry.trustStateSnapshot)
        assertEquals("Rubén Phone", entry.advertisedDisplayName)
        assertNull(entry.currentBindingId)

        // SPEC-009 security state remains OBSERVED
        assertEquals(TrustState.OBSERVED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }

    @Test
    fun `Scenario B - QR Verification Ceremony produces evidence and establishes trust`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)

        val session = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.QR)
        val payload = env.core.getQrVerificationPayload(session.verificationSessionId)

        // Remote scanner payload with swapped presenting/expected nodes
        val remotePayload = payload.copy(
            presentingNodeId = PeerDirectoryTestFixtures.peerA,
            expectedRemoteNodeId = payload.presentingNodeId
        )

        val evidence = env.core.submitQrVerificationPayload(session.verificationSessionId, remotePayload)
        assertEquals(VerificationResult.VERIFIED, evidence.result)

        env.core.createHumanIdentity("Rubén")
        val binding = env.core.establishTrust(PeerDirectoryTestFixtures.peerA, evidence.verificationEvidenceId)

        assertEquals(BindingState.VERIFIED, binding.state)
        assertEquals(TrustEstablishmentMode.VERIFIED_QR, binding.trustEstablishmentMode)
        assertEquals(TrustState.TRUSTED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }

    @Test
    fun `Scenario C - SAS Verification Ceremony generates 6 digits plus 3 words and establishes trust`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)

        val session = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.SAS)
        val sas = env.core.getSas(session.verificationSessionId)

        assertEquals(6, sas.numericCode.length)
        assertEquals(3, sas.words.size)

        val evidence = env.core.confirmSasMatch(session.verificationSessionId)
        val binding = env.core.establishTrust(PeerDirectoryTestFixtures.peerA, evidence.verificationEvidenceId)

        assertEquals(BindingState.VERIFIED, binding.state)
        assertEquals(TrustEstablishmentMode.VERIFIED_SAS, binding.trustEstablishmentMode)
        assertEquals(TrustState.TRUSTED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }

    @Test
    fun `Scenario D - Manual Fingerprint Verification computes 10 groups of 4 characters`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)

        val fp = env.core.getReadableFingerprint(PeerDirectoryTestFixtures.peerA)
        val groups = fp.formatted.split(" ")
        assertEquals(10, groups.size)

        val session = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.MANUAL_FINGERPRINT)
        val evidence = env.core.confirmManualFingerprint(session.verificationSessionId)
        val binding = env.core.establishTrust(PeerDirectoryTestFixtures.peerA, evidence.verificationEvidenceId)

        assertEquals(BindingState.VERIFIED, binding.state)
        assertEquals(TrustEstablishmentMode.VERIFIED_MANUAL_FINGERPRINT, binding.trustEstablishmentMode)
        assertEquals(TrustState.TRUSTED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }

    @Test
    fun `Scenario E - Unverified Trust Override records explicit confirmation`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)

        val binding = env.core.establishUnverifiedTrustOverride(
            PeerDirectoryTestFixtures.peerA,
            "User explicitly confirmed unverified override for local peer"
        )

        assertEquals(BindingState.TRUSTED_UNVERIFIED, binding.state)
        assertEquals(TrustEstablishmentMode.UNVERIFIED_TRUST_OVERRIDE, binding.trustEstablishmentMode)
        assertEquals(TrustState.TRUSTED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])

        val history = env.repository.listIdentityHistoryForPeer(PeerDirectoryTestFixtures.peerA)
        assertTrue(history.any { it.eventType == "TRUST_OVERRIDE_CREATED" })
    }

    @Test
    fun `Scenario F - Identity Change Detection logs event when same alias appears with new NodeId`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA, mapOf("displayName" to "Rubén"))
        env.core.setLocalAlias(PeerDirectoryTestFixtures.peerA, LocalAlias("Rubén Personal"))

        // New node appears claiming same advertised display name
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerB, mapOf("displayName" to "Rubén"))

        val historyA = env.repository.listIdentityHistoryForPeer(PeerDirectoryTestFixtures.peerA)
        assertTrue(historyA.any { it.eventType == "LOCAL_ALIAS_CHANGED" })

        // PeerB remains OBSERVED without inheriting trust
        assertEquals(TrustState.OBSERVED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerB])
    }

    @Test
    fun `Scenario G - Multi-Device Binding binds single HumanIdentity to multiple NodeIds`() = runBlocking {
        val human = env.core.createHumanIdentity("Rubén")

        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)
        val s1 = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.SAS)
        val e1 = env.core.confirmSasMatch(s1.verificationSessionId)
        env.core.bindPeerToHumanIdentity(human.humanIdentityId, PeerDirectoryTestFixtures.peerA, e1.verificationEvidenceId)

        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerB)
        val s2 = env.core.startVerification(PeerDirectoryTestFixtures.peerB, VerificationMethod.SAS)
        val e2 = env.core.confirmSasMatch(s2.verificationSessionId)
        env.core.bindPeerToHumanIdentity(human.humanIdentityId, PeerDirectoryTestFixtures.peerB, e2.verificationEvidenceId)

        val humanBindings = env.repository.findBindingsByHumanIdentity(human.humanIdentityId)
        assertEquals(2, humanBindings.size)
        assertTrue(humanBindings.any { it.peerNodeId == PeerDirectoryTestFixtures.peerA })
        assertTrue(humanBindings.any { it.peerNodeId == PeerDirectoryTestFixtures.peerB })
    }

    @Test
    fun `Scenario H - Binding Revocation sets REVOKED state and downgrades SPEC-009 trust`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)
        val session = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.SAS)
        val evidence = env.core.confirmSasMatch(session.verificationSessionId)
        val binding = env.core.establishTrust(PeerDirectoryTestFixtures.peerA, evidence.verificationEvidenceId)

        assertEquals(TrustState.TRUSTED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])

        val revoked = env.core.revokeBinding(binding.bindingId, "USER_REVOKED")
        assertEquals(BindingState.REVOKED, revoked.state)
        assertEquals(TrustState.OBSERVED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }

    @Test
    fun `Scenario I - Device Replacement and Trust Migration Workflow`() = runBlocking {
        val human = env.core.createHumanIdentity("Rubén")

        // Old device verified
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)
        val s1 = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.SAS)
        val e1 = env.core.confirmSasMatch(s1.verificationSessionId)
        env.core.bindPeerToHumanIdentity(human.humanIdentityId, PeerDirectoryTestFixtures.peerA, e1.verificationEvidenceId)

        // New device observed & verified
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerB)
        val s2 = env.core.startVerification(PeerDirectoryTestFixtures.peerB, VerificationMethod.SAS)
        env.core.confirmSasMatch(s2.verificationSessionId)

        // Start replacement
        val op = env.core.startDeviceReplacement(human.humanIdentityId, PeerDirectoryTestFixtures.peerA, PeerDirectoryTestFixtures.peerB)
        val plan = env.core.createTrustMigrationPlan(op.replacementOperationId)
        env.core.confirmTrustMigration(plan.trustMigrationPlanId, emptyList())
        val completedOp = env.core.completeDeviceReplacement(op.replacementOperationId)

        assertEquals(DeviceReplacementState.COMPLETED, completedOp.state)
        assertEquals(TrustState.TRUSTED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerB])
        assertEquals(TrustState.OBSERVED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }

    @Test
    fun `Scenario J - Verification Replay Protection blocks reused nonces`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)
        val session = env.core.startVerification(PeerDirectoryTestFixtures.peerA, VerificationMethod.QR)
        val payload = env.core.getQrVerificationPayload(session.verificationSessionId)

        val remotePayload = payload.copy(
            presentingNodeId = PeerDirectoryTestFixtures.peerA,
            expectedRemoteNodeId = payload.presentingNodeId
        )

        env.core.submitQrVerificationPayload(session.verificationSessionId, remotePayload)

        // Second attempt with same payload/nonce should throw QrReplayDetected
        assertThrows<LocalPeerIdentityError.QrVerificationError.QrReplayDetected> {
            env.core.submitQrVerificationPayload(session.verificationSessionId, remotePayload)
        }
    }

    @Test
    fun `Scenario K - Crash Recovery expires stale sessions and reconciles state without upgrading trust`() = runBlocking {
        env.core.observeAuthenticatedPeer(PeerDirectoryTestFixtures.peerA)

        // Start session past TTL
        val staleSession = VerificationSession(
            localNodeId = NodeId("node:v1:sha256:0000000000000000000000000000000000000000000000000000000000000000"),
            remoteNodeId = PeerDirectoryTestFixtures.peerA,
            method = VerificationMethod.SAS,
            localNonce = "nonce123",
            startedAt = 100L,
            expiresAt = 200L
        )
        env.repository.saveVerificationSession(staleSession)

        env.core.recover()

        val recoveredSession = env.repository.getVerificationSession(staleSession.verificationSessionId)
        assertEquals(VerificationState.EXPIRED, recoveredSession?.state)

        // Recovery never upgrades trust
        assertEquals(TrustState.OBSERVED, env.fakeSecurityCore.peerTrustStates[PeerDirectoryTestFixtures.peerA])
    }
}
