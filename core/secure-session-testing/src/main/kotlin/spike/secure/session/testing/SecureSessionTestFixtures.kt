package spike.secure.session.testing

import spike.secure.session.application.SecureSessionCoreImpl
import spike.secure.session.contracts.SecureSessionCore
import spike.secure.session.contracts.SecureSessionEventListener
import spike.secure.session.domain.SecureSessionEvent
import spike.secure.session.domain.SecureSessionSnapshot

/**
 * Shared test fixtures and helpers for SecureSessionCore tests.
 *
 * Provides:
 * - Standard Node IDs and signing key bytes for deterministic test scenarios
 * - Factory to build paired (Initiator/Responder) SecureSessionCore instances
 * - TwoNodeHarness that drives a complete handshake by relaying frames
 * - EventCaptor to collect and assert on emitted domain events
 */
object SecureSessionTestFixtures {

    // Two node IDs: "aaa..." < "bbb..." lexicographically, so A = INITIATOR, B = RESPONDER
    val NODE_A_HEX = "a".repeat(64)
    val NODE_B_HEX = "b".repeat(64)
    val NODE_A_ID = "node:v1:sha256:$NODE_A_HEX"
    val NODE_B_ID = "node:v1:sha256:$NODE_B_HEX"

    val NODE_A_KEY = ByteArray(32) { 0xAA.toByte() }
    val NODE_B_KEY = ByteArray(32) { 0xBB.toByte() }

    const val ENCOUNTER_ID = "encounter-test-001"

    /**
     * Creates a symmetric pair of (nodeA, nodeB) FakeIdentityCores
     * with cross-registered remote identities.
     */
    fun createIdentityPair(): Pair<FakeIdentityCore, FakeIdentityCore> {
        val identityA = FakeIdentityCore(NODE_A_HEX, NODE_A_KEY)
        val identityB = FakeIdentityCore(NODE_B_HEX, NODE_B_KEY)
        identityA.registerRemote(identityB)
        identityB.registerRemote(identityA)
        return Pair(identityA, identityB)
    }

    /**
     * Creates a symmetric pair of SecureSessionCore implementations.
     * Node A uses a crypto provider with seed 0x11; Node B uses 0x22 so nonces and
     * ephemeral keys differ between the two sides.
     */
    fun createSessionCorePair(): Pair<SecureSessionCore, SecureSessionCore> {
        val (identityA, identityB) = createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val cryptoB = FakeSessionCryptoProvider(0x22)
        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)
        return Pair(coreA, coreB)
    }
}

/**
 * Captures all events emitted by a SecureSessionCore.
 */
class EventCaptor : SecureSessionEventListener {
    private val captured = mutableListOf<SecureSessionEvent>()

    override fun onEvent(event: SecureSessionEvent) {
        synchronized(captured) { captured += event }
    }

    fun all(): List<SecureSessionEvent> = synchronized(captured) { captured.toList() }

    fun ofType(type: spike.secure.session.domain.SecureSessionEventType): List<SecureSessionEvent> =
        all().filter { it.eventType == type }

    fun hasEventType(type: spike.secure.session.domain.SecureSessionEventType): Boolean =
        all().any { it.eventType == type }

    fun clear() = synchronized(captured) { captured.clear() }
}

/**
 * Drives a complete two-node handshake by relaying frames between initiator and responder.
 *
 * Returns (sessionIdA, sessionIdB) on success.
 */
suspend fun TwoNodeHarness(
    coreA: SecureSessionCore,
    coreB: SecureSessionCore,
    encounterId: String = SecureSessionTestFixtures.ENCOUNTER_ID,
    nodeAId: String = SecureSessionTestFixtures.NODE_A_ID,
    nodeBId: String = SecureSessionTestFixtures.NODE_B_ID
): Pair<String, String> {
    // A creates, B accepts (roles will be determined internally)
    val snapA = coreA.createSession(encounterId, nodeBId)
    val snapB = coreB.acceptSession(encounterId, nodeAId)
    val sidA = snapA.sessionId
    val sidB = snapB.sessionId

    // Step 1: A emits SESSION_HELLO
    val frame1 = coreA.processHandshakeFrame(sidA, ByteArray(0))
        ?: error("Step 1: Expected SESSION_HELLO from A")

    // Step 2: B processes SESSION_HELLO → emits SESSION_HELLO_RESPONSE
    val frame2 = coreB.processHandshakeFrame(sidB, frame1)
        ?: error("Step 2: Expected SESSION_HELLO_RESPONSE from B")

    // Step 3: A processes SESSION_HELLO_RESPONSE → emits HANDSHAKE_AUTH (initiator)
    val frame3 = coreA.processHandshakeFrame(sidA, frame2)
        ?: error("Step 3: Expected HANDSHAKE_AUTH from A")

    // Step 4: B processes Initiator HANDSHAKE_AUTH → derives keys → emits HANDSHAKE_AUTH (responder)
    val frame4 = coreB.processHandshakeFrame(sidB, frame3)
        ?: error("Step 4: Expected HANDSHAKE_AUTH from B")

    // Step 5: A processes Responder HANDSHAKE_AUTH → emits KEY_CONFIRMATION (initiator)
    val frame5 = coreA.processHandshakeFrame(sidA, frame4)
        ?: error("Step 5: Expected KEY_CONFIRMATION from A")

    // Step 6: B processes Initiator KEY_CONFIRMATION → emits KEY_CONFIRMATION (responder)
    val frame6 = coreB.processHandshakeFrame(sidB, frame5)
        ?: error("Step 6: Expected KEY_CONFIRMATION from B")

    // Step 7: A processes Responder KEY_CONFIRMATION → session ESTABLISHED, no output
    val frame7 = coreA.processHandshakeFrame(sidA, frame6)
    check(frame7 == null) { "Step 7: Expected null (no more frames) from A, got ${frame7?.size} bytes" }

    return Pair(sidA, sidB)
}
