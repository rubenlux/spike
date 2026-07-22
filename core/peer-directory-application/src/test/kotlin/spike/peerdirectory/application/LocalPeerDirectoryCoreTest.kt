package spike.peerdirectory.application

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*

class LocalPeerDirectoryCoreTest {

    private lateinit var repository: InMemoryPeerIdentityRepository
    private lateinit var fakeSecurityCore: FakeLocalPeerSecurityCore
    private lateinit var vSessionService: VerificationSessionServiceImpl
    private lateinit var trustService: TrustEstablishmentServiceImpl
    private lateinit var deviceService: DeviceReplacementServiceImpl
    private lateinit var recoveryService: PeerIdentityRecoveryServiceImpl
    private lateinit var core: LocalPeerDirectoryCoreImpl

    private val peerNode = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")

    @BeforeEach
    fun setUp() {
        repository = InMemoryPeerIdentityRepository()
        fakeSecurityCore = FakeLocalPeerSecurityCore()
        vSessionService = VerificationSessionServiceImpl(repository)
        trustService = TrustEstablishmentServiceImpl(repository, fakeSecurityCore)
        deviceService = DeviceReplacementServiceImpl(repository, fakeSecurityCore)
        recoveryService = PeerIdentityRecoveryServiceImpl(repository, fakeSecurityCore)
        core = LocalPeerDirectoryCoreImpl(
            repository = repository,
            verificationSessionService = vSessionService,
            trustEstablishmentService = trustService,
            deviceReplacementService = deviceService,
            recoveryService = recoveryService,
            localPeerSecurityCore = fakeSecurityCore
        )
    }

    @Test
    fun `observeAuthenticatedPeer creates OBSERVED entry and updates metadata`() = runBlocking {
        val entry1 = core.observeAuthenticatedPeer(peerNode, mapOf("displayName" to "Rubén Phone"))
        assertEquals("OBSERVED", entry1.trustStateSnapshot)
        assertEquals("Rubén Phone", entry1.advertisedDisplayName)

        val entry2 = core.observeAuthenticatedPeer(peerNode, mapOf("displayName" to "Rubén Phone Updated"))
        assertEquals(2L, entry2.encounterCount)
        assertEquals("Rubén Phone Updated", entry2.advertisedDisplayName)
    }

    @Test
    fun `setLocalAlias assigns mutable local alias`() = runBlocking {
        core.observeAuthenticatedPeer(peerNode)

        val alias = LocalAlias("Work Phone")
        val updated = core.setLocalAlias(peerNode, alias)

        assertEquals(alias, updated.localAlias)
    }
}
