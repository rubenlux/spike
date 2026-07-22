package spike.peerdirectory.testing

import spike.identity.domain.NodeId
import spike.peerdirectory.application.*

object PeerDirectoryTestFixtures {

    val peerA = NodeId("node:v1:sha256:1111111111111111111111111111111111111111111111111111111111111111")
    val peerB = NodeId("node:v1:sha256:2222222222222222222222222222222222222222222222222222222222222222")
    val peerC = NodeId("node:v1:sha256:3333333333333333333333333333333333333333333333333333333333333333")

    fun createDefaultEnvironment(): TestPeerDirectoryEnvironment {
        val repository = InMemoryPeerIdentityRepository()
        val fakeSecurityCore = FakeLocalPeerSecurityCore()
        val vSessionService = VerificationSessionServiceImpl(repository)
        val trustService = TrustEstablishmentServiceImpl(repository, fakeSecurityCore)
        val deviceService = DeviceReplacementServiceImpl(repository, fakeSecurityCore)
        val recoveryService = PeerIdentityRecoveryServiceImpl(repository, fakeSecurityCore)
        val core = LocalPeerDirectoryCoreImpl(
            repository = repository,
            verificationSessionService = vSessionService,
            trustEstablishmentService = trustService,
            deviceReplacementService = deviceService,
            recoveryService = recoveryService,
            localPeerSecurityCore = fakeSecurityCore
        )

        return TestPeerDirectoryEnvironment(
            repository = repository,
            fakeSecurityCore = fakeSecurityCore,
            vSessionService = vSessionService,
            trustService = trustService,
            deviceService = deviceService,
            recoveryService = recoveryService,
            core = core
        )
    }
}

data class TestPeerDirectoryEnvironment(
    val repository: InMemoryPeerIdentityRepository,
    val fakeSecurityCore: FakeLocalPeerSecurityCore,
    val vSessionService: VerificationSessionServiceImpl,
    val trustService: TrustEstablishmentServiceImpl,
    val deviceService: DeviceReplacementServiceImpl,
    val recoveryService: PeerIdentityRecoveryServiceImpl,
    val core: LocalPeerDirectoryCoreImpl
)
