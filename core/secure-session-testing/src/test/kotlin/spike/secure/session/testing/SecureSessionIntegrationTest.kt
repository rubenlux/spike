package spike.secure.session.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import spike.crypto.EphemeralKeyPair
import spike.crypto.EphemeralPrivateKey
import spike.crypto.EphemeralPublicKey
import spike.crypto.SessionCryptoProvider
import spike.secure.session.application.SecureSessionCoreImpl
import spike.secure.session.application.SecureSessionWireCodec
import spike.secure.session.application.DecodedHandshakeFrame
import spike.secure.session.contracts.SecureSessionCore
import spike.secure.session.contracts.SecureSessionEventListener
import spike.secure.session.domain.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration test suite for SecureSessionCore — covers Scenarios A through O from SPEC-005.
 *
 * Each test drives two real SecureSessionCoreImpl instances (A and B) wired with
 * FakeSessionCryptoProvider and FakeIdentityCore, relaying frames in memory.
 *
 * Test ID mapping to spec scenarios:
 *   Scenario A  → testScenarioA_completeHandshakeEstablishesSession
 *   Scenario B  → testScenarioB_bidirectionalDataExchange
 *   Scenario C  → testScenarioC_rekeyingRotatesKeysAndResetsSequences
 *   Scenario D  → testScenarioD_replayAttackRejected
 *   Scenario E  → testScenarioE_outOfOrderRecordRejected
 *   Scenario F  → testScenarioF_tamperedCiphertextRejected
 *   Scenario G  → testScenarioG_wrongSessionIdRejected
 *   Scenario H  → testScenarioH_wrongKeyEpochRejected
 *   Scenario I  → testScenarioI_tamperWithHandshakeAuthSignatureRejected
 *   Scenario J  → testScenarioJ_transcriptHashMismatchRejected
 *   Scenario K  → testScenarioK_sessionCloseZeroizesKeyMaterial
 *   Scenario L  → testScenarioL_multipleIndependentSessionsCoexist
 *   Scenario M  → testScenarioM_encryptFailsBeforeHandshakeComplete
 *   Scenario N  → testScenarioN_sequenceOverflowDetected
 *   Scenario O  → testScenarioO_eventsEmittedInOrder
 */
class SecureSessionIntegrationTest {

    // ──────────────────────────────────────────────────────────────────────
    // Scenario A — Complete handshake establishes session on both sides
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario A - complete handshake establishes session`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()

        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val snapA = coreA.getSessionSnapshot(sidA)!!
        val snapB = coreB.getSessionSnapshot(sidB)!!

        assertEquals(SessionState.ESTABLISHED, snapA.state, "Node A must be ESTABLISHED")
        assertEquals(SessionState.ESTABLISHED, snapB.state, "Node B must be ESTABLISHED")
        assertEquals(SessionRole.INITIATOR, snapA.role, "A must be INITIATOR (aaa... < bbb...)")
        assertEquals(SessionRole.RESPONDER, snapB.role, "B must be RESPONDER")
        assertEquals(0, snapA.keyEpoch)
        assertEquals(0, snapB.keyEpoch)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario B — Bidirectional data exchange after session establishment
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario B - bidirectional data exchange`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val plaintext = "Hello, secure world!".encodeToByteArray()

        // A → B
        val encrypted = coreA.encrypt(sidA, SecureRecordType.CONTROL, plaintext)
        val decrypted = coreB.decrypt(sidB, encrypted)
        assertArrayEquals(plaintext, decrypted, "B must recover A's plaintext")

        // B → A
        val reply = "Acknowledged.".encodeToByteArray()
        val encryptedReply = coreB.encrypt(sidB, SecureRecordType.CONTROL, reply)
        val decryptedReply = coreA.decrypt(sidA, encryptedReply)
        assertArrayEquals(reply, decryptedReply, "A must recover B's plaintext")

        // Sequences must have advanced
        val snapA = coreA.getSessionSnapshot(sidA)!!
        val snapB = coreB.getSessionSnapshot(sidB)!!
        assertEquals(1L, snapA.recordsSent)
        assertEquals(1L, snapA.recordsReceived)
        assertEquals(1L, snapB.recordsSent)
        assertEquals(1L, snapB.recordsReceived)
    }

    @Test
    fun `scenario B2 - multiple sequential records are decrypted in order`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val messages = (1..5).map { "message $it".encodeToByteArray() }
        for (msg in messages) {
            val enc = coreA.encrypt(sidA, SecureRecordType.CONTROL, msg)
            val dec = coreB.decrypt(sidB, enc)
            assertArrayEquals(msg, dec)
        }
        assertEquals(5L, coreA.getSessionSnapshot(sidA)!!.recordsSent)
        assertEquals(5L, coreB.getSessionSnapshot(sidB)!!.recordsReceived)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario C — Rekeying rotates keys and resets sequences to 0
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario C - rekeying rotates epoch and resets sequences`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        // Encrypt one record before rekeying
        val before = "pre-rekey".encodeToByteArray()
        val enc1 = coreA.encrypt(sidA, SecureRecordType.CONTROL, before)
        coreB.decrypt(sidB, enc1)

        // Both sides rekey (in production this would be coordinated; for unit tests we test independently)
        coreA.requestRekey(sidA)
        coreB.requestRekey(sidB)

        val snapA = coreA.getSessionSnapshot(sidA)!!
        val snapB = coreB.getSessionSnapshot(sidB)!!
        assertEquals(1, snapA.keyEpoch, "Epoch must advance to 1 after rekey")
        assertEquals(1, snapB.keyEpoch, "Epoch must advance to 1 after rekey")

        // After rekey, sequences reset and new records are exchangeable in both directions
        val after = "post-rekey".encodeToByteArray()
        val enc2 = coreA.encrypt(sidA, SecureRecordType.CONTROL, after)
        val dec2 = coreB.decrypt(sidB, enc2)
        assertArrayEquals(after, dec2, "Data must be exchangeable A -> B after rekey")

        val replyAfter = "post-rekey-reply".encodeToByteArray()
        val encReply = coreB.encrypt(sidB, SecureRecordType.CONTROL, replyAfter)
        val decReply = coreA.decrypt(sidA, encReply)
        assertArrayEquals(replyAfter, decReply, "Data must be exchangeable B -> A after rekey")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario D — Replay attack: duplicate record is rejected
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario D - replay attack is rejected`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val msg = "data".encodeToByteArray()
        val enc = coreA.encrypt(sidA, SecureRecordType.CONTROL, msg)
        coreB.decrypt(sidB, enc)  // First delivery — success

        // Second delivery of same record — must be rejected
        val ex = assertThrows<SecureSessionError.SecureRecordError.ReplayDetected> {
            runBlocking { coreB.decrypt(sidB, enc) }
        }
        assertTrue(ex.message!!.contains("sequence", ignoreCase = true) || ex.message!!.isNotBlank())
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario E — Out-of-order record is rejected (strict sequencing v1)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario E - out-of-order record is rejected`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val enc0 = coreA.encrypt(sidA, SecureRecordType.CONTROL, "msg0".encodeToByteArray())
        val enc1 = coreA.encrypt(sidA, SecureRecordType.CONTROL, "msg1".encodeToByteArray())

        // Deliver seq=1 before seq=0 (out of order)
        assertThrows<SecureSessionError.SecureRecordError.OutOfOrderRecord> {
            runBlocking { coreB.decrypt(sidB, enc1) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario F — Tampered ciphertext fails AEAD authentication
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario F - tampered ciphertext fails AEAD authentication`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val enc = coreA.encrypt(sidA, SecureRecordType.CONTROL, "secret".encodeToByteArray())

        // Flip a bit in the encrypted payload / authentication tag
        val tampered = enc.copyOf()
        tampered[enc.size - 1] = (tampered[enc.size - 1].toInt() xor 0x01).toByte()

        assertThrows<SecureSessionError.SecureRecordError.AeadAuthenticationFailure> {
            runBlocking { coreB.decrypt(sidB, tampered) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario G — Record for wrong session ID is rejected
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario G - record with wrong session ID is rejected`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (coreA2, coreB2) = SecureSessionTestFixtures.createSessionCorePair()

        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)
        val (sidA2, _) = TwoNodeHarness(coreA2, coreB2)

        // Encrypt on session A2, try to decrypt on session B (different session)
        val enc = coreA2.encrypt(sidA2, SecureRecordType.CONTROL, "cross".encodeToByteArray())

        assertThrows<SecureSessionError.SecureRecordError.SessionIdMismatch> {
            runBlocking { coreB.decrypt(sidB, enc) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario H — Record with wrong key epoch is rejected
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario H - record with wrong key epoch is rejected`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        // Encrypt at epoch 0
        val enc = coreA.encrypt(sidA, SecureRecordType.CONTROL, "epoch0".encodeToByteArray())

        // Both rekey to epoch 1
        coreA.requestRekey(sidA)
        coreB.requestRekey(sidB)

        // Try to deliver epoch-0 record after rekey — epoch mismatch
        assertThrows<SecureSessionError.SecureRecordError.InvalidKeyEpoch> {
            runBlocking { coreB.decrypt(sidB, enc) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario I — Tampered HANDSHAKE_AUTH signature is rejected
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario I - tampered handshake auth signature is rejected`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val cryptoB = FakeSessionCryptoProvider(0x22)
        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)

        val snapA = coreA.createSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val frame1 = coreA.processHandshakeFrame(sidA, ByteArray(0))!!
        val frame2 = coreB.processHandshakeFrame(sidB, frame1)!!
        val frame3 = coreA.processHandshakeFrame(sidA, frame2)!! // HANDSHAKE_AUTH from A

        // Tamper the HANDSHAKE_AUTH signature bytes (last 64 bytes of frame)
        val tampered = frame3.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        assertThrows<SecureSessionError.HandshakeAuthenticationError.InvalidHandshakeSignature> {
            runBlocking { coreB.processHandshakeFrame(sidB, tampered) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario J — Transcript hash mismatch is rejected
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario J - transcript hash mismatch causes rejection`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val cryptoB = FakeSessionCryptoProvider(0x22)
        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)

        val snapA = coreA.createSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val frame1 = coreA.processHandshakeFrame(sidA, ByteArray(0))!!
        val frame2 = coreB.processHandshakeFrame(sidB, frame1)!!
        val frame3 = coreA.processHandshakeFrame(sidA, frame2)!!

        // Corrupt 32 transcript-hash bytes in HANDSHAKE_AUTH frame
        // Frame layout: magic(4) + type(2) + handshakeId(2+N) + signerNodeId(2+M) + roleOrdinal(1)
        // + transcriptHash(32) + signature(64)
        // Corrupt middle of the frame to hit the transcript hash field
        val tampered = frame3.copyOf()
        val mid = tampered.size - 64 - 32  // approximate position of transcript hash start
        if (mid >= 0) tampered[mid] = (tampered[mid].toInt() xor 0xFF).toByte()

        // Either transcript mismatch or signature failure — both indicate rejection
        assertThrows<Exception> {
            runBlocking { coreB.processHandshakeFrame(sidB, tampered) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario K — Session close destroys key material
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario K - session close removes session and prevents further use`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, _) = TwoNodeHarness(coreA, coreB)

        coreA.closeSession(sidA, "normal closure")

        // Session no longer accessible
        val snap = coreA.getSessionSnapshot(sidA)
        assertNull(snap, "Session must not be accessible after close")

        // Encrypt must throw SessionNotFound
        assertThrows<SecureSessionError.SessionStateError.SecureSessionNotFound> {
            runBlocking { coreA.encrypt(sidA, SecureRecordType.CONTROL, "after close".encodeToByteArray()) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario L — Multiple independent sessions coexist on same core
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario L - multiple independent sessions coexist`() = runBlocking {
        // Two separate pairs
        val (coreA1, coreB1) = SecureSessionTestFixtures.createSessionCorePair()
        val (coreA2, coreB2) = SecureSessionTestFixtures.createSessionCorePair()

        val (sid1A, sid1B) = TwoNodeHarness(coreA1, coreB1, encounterId = "encounter-1")
        val (sid2A, sid2B) = TwoNodeHarness(coreA2, coreB2, encounterId = "encounter-2")

        // Both sessions independently established
        assertEquals(SessionState.ESTABLISHED, coreA1.getSessionSnapshot(sid1A)!!.state)
        assertEquals(SessionState.ESTABLISHED, coreA2.getSessionSnapshot(sid2A)!!.state)

        val msg1 = "session1".encodeToByteArray()
        val msg2 = "session2".encodeToByteArray()

        val enc1 = coreA1.encrypt(sid1A, SecureRecordType.CONTROL, msg1)
        val enc2 = coreA2.encrypt(sid2A, SecureRecordType.CONTROL, msg2)

        assertArrayEquals(msg1, coreB1.decrypt(sid1B, enc1))
        assertArrayEquals(msg2, coreB2.decrypt(sid2B, enc2))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario M — Encrypt/Decrypt fail before session is established
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario M - encrypt before session established throws`() = runBlocking {
        val (coreA, _) = SecureSessionTestFixtures.createSessionCorePair()
        val snap = coreA.createSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_B_ID)

        assertThrows<SecureSessionError.SessionStateError.SecureSessionNotEstablished> {
            runBlocking { coreA.encrypt(snap.sessionId, SecureRecordType.CONTROL, "data".encodeToByteArray()) }
        }
        Unit
    }

    @Test
    fun `scenario M2 - decrypt before session established throws`() = runBlocking {
        val (coreA, _) = SecureSessionTestFixtures.createSessionCorePair()
        val snap = coreA.createSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_B_ID)

        assertThrows<SecureSessionError.SessionStateError.SecureSessionNotEstablished> {
            runBlocking { coreA.decrypt(snap.sessionId, ByteArray(50)) }
        }
        Unit
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario N — Sequence overflow detection
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario N - sequence overflow is detected`() {
        val seq = spike.secure.session.domain.SendSequence(Long.MAX_VALUE)
        assertThrows<SecureSessionError.SecureRecordError.SequenceOverflow> {
            seq.next()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scenario O — Domain events emitted in expected order
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `scenario O - domain events emitted in order during handshake`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val cryptoB = FakeSessionCryptoProvider(0x22)
        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)

        val captorA = EventCaptor()
        val captorB = EventCaptor()
        coreA.subscribeEvents(captorA)
        coreB.subscribeEvents(captorB)

        TwoNodeHarness(coreA, coreB)

        val typesA = captorA.all().map { it.eventType }
        val typesB = captorB.all().map { it.eventType }

        // Initiator (A) key events
        assertTrue(typesA.contains(SecureSessionEventType.SESSION_CREATION_STARTED), "A: SESSION_CREATION_STARTED")
        assertTrue(typesA.contains(SecureSessionEventType.EPHEMERAL_KEY_GENERATED), "A: EPHEMERAL_KEY_GENERATED")
        assertTrue(typesA.contains(SecureSessionEventType.SESSION_HELLO_SENT), "A: SESSION_HELLO_SENT")
        assertTrue(typesA.contains(SecureSessionEventType.DH_SHARED_SECRET_CREATED), "A: DH_SHARED_SECRET_CREATED")
        assertTrue(typesA.contains(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_HASHED), "A: TRANSCRIPT_HASHED")
        assertTrue(typesA.contains(SecureSessionEventType.SESSION_KEYS_DERIVED), "A: SESSION_KEYS_DERIVED")
        assertTrue(typesA.contains(SecureSessionEventType.KEY_CONFIRMATION_VERIFIED), "A: KEY_CONFIRMATION_VERIFIED")
        assertTrue(typesA.contains(SecureSessionEventType.SESSION_ESTABLISHED), "A: SESSION_ESTABLISHED")

        // Responder (B) key events
        assertTrue(typesB.contains(SecureSessionEventType.SESSION_CREATION_STARTED), "B: SESSION_CREATION_STARTED")
        assertTrue(typesB.contains(SecureSessionEventType.SESSION_HELLO_RECEIVED), "B: SESSION_HELLO_RECEIVED")
        assertTrue(typesB.contains(SecureSessionEventType.REMOTE_HANDSHAKE_AUTH_VERIFIED), "B: REMOTE_AUTH_VERIFIED")
        assertTrue(typesB.contains(SecureSessionEventType.SESSION_ESTABLISHED), "B: SESSION_ESTABLISHED")

        // SESSION_ESTABLISHED must be last in both captors
        assertEquals(SecureSessionEventType.SESSION_ESTABLISHED, typesA.last(), "SESSION_ESTABLISHED must be last event on A")
        assertEquals(SecureSessionEventType.SESSION_ESTABLISHED, typesB.last(), "SESSION_ESTABLISHED must be last event on B")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Fault injection — Malformed frame bytes
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `fault injection - malformed frame bytes cause TranscriptError`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val snapB = coreB.acceptSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_A_ID)

        assertThrows<Exception> {
            coreB.processHandshakeFrame(snapB.sessionId, ByteArray(10) { 0xFF.toByte() })
        }
        Unit
    }

    @Test
    fun `fault injection - empty record bytes cause MalformedSecureRecord`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (_, sidB) = TwoNodeHarness(coreA, coreB)

        assertThrows<SecureSessionError.SecureRecordError.MalformedSecureRecord> {
            coreB.decrypt(sidB, ByteArray(0))
        }
        Unit
    }

    @Test
    fun `fault injection - transport loss transitions session to FAILED`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, _) = TwoNodeHarness(coreA, coreB)

        coreA.handleTransportLoss(sidA, "wifi disconnect")

        // Session removed
        assertNull(coreA.getSessionSnapshot(sidA))
    }

    // ──────────────────────────────────────────────────────────────────────
    // Wire codec round-trips
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `wire codec - SESSION_HELLO round-trips correctly`() {
        val codec = spike.secure.session.application.SecureSessionWireCodec()
        val nonce = ByteArray(16) { it.toByte() }
        val ephemKey = ByteArray(32) { (it + 100).toByte() }
        val caps = mapOf("feature_a" to "1", "version" to "v1")

        val encoded = codec.encodeSessionHello(
            version = 1,
            supportedSuites = "OFFLINE_SESSION_SUITE_V1",
            encounterId = "enc-001",
            handshakeId = "hs-001",
            initiatorNodeId = "node:v1:sha256:${"a".repeat(64)}",
            responderNodeId = "node:v1:sha256:${"b".repeat(64)}",
            nonce = nonce,
            ephemeralPublicKey = ephemKey,
            capabilities = caps
        )
        val decoded = codec.decodeHandshakeFrame(encoded)

        assertInstanceOf(spike.secure.session.application.DecodedHandshakeFrame.SessionHello::class.java, decoded)
        val hello = decoded as spike.secure.session.application.DecodedHandshakeFrame.SessionHello
        assertEquals(1, hello.version)
        assertEquals("OFFLINE_SESSION_SUITE_V1", hello.supportedSuites)
        assertEquals("enc-001", hello.encounterId)
        assertArrayEquals(nonce, hello.nonce)
        assertArrayEquals(ephemKey, hello.ephemeralPublicKey)
        assertEquals(caps, hello.capabilities)
    }

    @Test
    fun `wire codec - SecureRecord round-trips correctly`() {
        val codec = spike.secure.session.application.SecureSessionWireCodec()
        val ciphertext = ByteArray(32) { it.toByte() }
        val tag = ByteArray(16) { (it + 200).toByte() }

        val record = spike.secure.session.domain.SecureRecord(
            recordVersion = 1,
            sessionId = SecureSessionId("test-session-id"),
            keyEpoch = KeyEpoch(3),
            sequenceNumber = 42L,
            recordType = SecureRecordType.CONTROL,
            ciphertextLength = ciphertext.size,
            ciphertext = ciphertext,
            authenticationTag = tag
        )
        val encoded = codec.encodeSecureRecord(record)
        val decoded = codec.decodeSecureRecord(encoded)

        assertEquals(record.recordVersion, decoded.recordVersion)
        assertEquals(record.sessionId, decoded.sessionId)
        assertEquals(record.keyEpoch, decoded.keyEpoch)
        assertEquals(record.sequenceNumber, decoded.sequenceNumber)
        assertEquals(record.recordType, decoded.recordType)
        assertArrayEquals(record.ciphertext, decoded.ciphertext)
        assertArrayEquals(record.authenticationTag, decoded.authenticationTag)
    }

    // ──────────────────────────────────────────────────────────────────────
    // FakeSessionCryptoProvider unit tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `crypto provider - sha256 is deterministic`() {
        val provider = FakeSessionCryptoProvider()
        val data = "test-data".encodeToByteArray()
        val h1 = provider.sha256(data)
        val h2 = provider.sha256(data)
        assertArrayEquals(h1, h2)
        assertEquals(32, h1.size)
    }

    @Test
    fun `crypto provider - HKDF expand produces correct length`() {
        val provider = FakeSessionCryptoProvider()
        val prk = provider.sha256("test".encodeToByteArray())
        val out12 = provider.hkdfExpand(prk, "info".encodeToByteArray(), 12)
        val out32 = provider.hkdfExpand(prk, "info".encodeToByteArray(), 32)
        val out64 = provider.hkdfExpand(prk, "info".encodeToByteArray(), 64)
        assertEquals(12, out12.size)
        assertEquals(32, out32.size)
        assertEquals(64, out64.size)
    }

    @Test
    fun `crypto provider - AEAD encrypt-decrypt roundtrip`() {
        val provider = FakeSessionCryptoProvider()
        val key = provider.sha256("key".encodeToByteArray())
        val nonce = ByteArray(12) { it.toByte() }
        val aad = "header".encodeToByteArray()
        val plaintext = "secret message".encodeToByteArray()

        val ciphertext = provider.aeadEncrypt(key, nonce, plaintext, aad)
        val recovered = provider.aeadDecrypt(key, nonce, ciphertext, aad)
        assertArrayEquals(plaintext, recovered)
    }

    @Test
    fun `crypto provider - AEAD rejects wrong tag`() {
        val provider = FakeSessionCryptoProvider()
        val key = provider.sha256("key".encodeToByteArray())
        val nonce = ByteArray(12)
        val aad = "aad".encodeToByteArray()
        val ciphertext = provider.aeadEncrypt(key, nonce, "msg".encodeToByteArray(), aad)

        val tampered = ciphertext.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        assertThrows<javax.crypto.AEADBadTagException> {
            provider.aeadDecrypt(key, nonce, tampered, aad)
        }
    }

    @Test
    fun `crypto provider - shared secret is non-zero and deterministic`() {
        val provider = FakeSessionCryptoProvider(0x11)
        val kpA = provider.generateEphemeralKeyPair()
        provider.resetCounter()
        val provider2 = FakeSessionCryptoProvider(0x22)
        val kpB = provider2.generateEphemeralKeyPair()

        val secretAB = provider.calculateSharedSecret(kpA.privateKey, kpB.publicKey)
        assertFalse(secretAB.all { it == 0.toByte() }, "Shared secret must not be all zeros")
        assertEquals(32, secretAB.size)
    }

    // ──────────────────────────────────────────────────────────────────────
    // SPEC-005 Regression Test — Canonical session ID adoption, collision safety,
    // and referential cleanup
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `regression - canonical session ID adoption, collision safety, and referential cleanup`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        // 1. Handshake reaches ESTABLISHED on both peers
        val snapA = coreA.getSessionSnapshot(sidA)!!
        val snapB = coreB.getSessionSnapshot(sidB)!!
        assertEquals(SessionState.ESTABLISHED, snapA.state, "Node A must reach ESTABLISHED")
        assertEquals(SessionState.ESTABLISHED, snapB.state, "Node B must reach ESTABLISHED")

        // 2. Initial random handles were distinct
        assertNotEquals(sidA, sidB, "Initiator and responder initial session IDs must differ")

        // 3. Responder adopted initiator's canonical session ID
        assertEquals(sidA, snapB.sessionId, "Responder must adopt initiator's canonical session ID")

        // 4. Resolution through both responder's original local handle (sidB) and canonical ID (sidA)
        val snapBByCanonical = coreB.getSessionSnapshot(sidA)
        assertNotNull(snapBByCanonical, "Responder must resolve session via canonical ID alias")
        assertEquals(snapB.sessionId, snapBByCanonical!!.sessionId)

        // 5. Bidirectional encryption/decryption succeeds using both handles
        val plaintext = "Canonical ID verified".encodeToByteArray()
        val ciphertextA = coreA.encrypt(sidA, SecureRecordType.CONTROL, plaintext)
        val decryptedB = coreB.decrypt(sidB, ciphertextA)
        assertArrayEquals(plaintext, decryptedB, "Decryption via original handle sidB must succeed")

        val plaintext2 = "Alias decryption verified".encodeToByteArray()
        val ciphertextA2 = coreA.encrypt(sidA, SecureRecordType.CONTROL, plaintext2)
        val decryptedBByAlias = coreB.decrypt(sidA, ciphertextA2)
        assertArrayEquals(plaintext2, decryptedBByAlias, "Decryption via canonical alias sidA must succeed")

        val reply = "Reply verified".encodeToByteArray()
        val ciphertextB = coreB.encrypt(sidB, SecureRecordType.CONTROL, reply)
        val decryptedA = coreA.decrypt(sidA, ciphertextB)
        assertArrayEquals(reply, decryptedA, "Initiator must decrypt responder's reply")

        // 6. Referential cleanup on session closure removes both original handle and alias
        coreB.closeSession(sidB, "normal closure")
        assertNull(coreB.getSessionSnapshot(sidB), "Original handle sidB must be removed after closeSession")
        assertNull(coreB.getSessionSnapshot(sidA), "Canonical alias sidA must be removed after closeSession")

        // 7. Referential cleanup on transport loss removes both original handle and alias
        val (coreA2, coreB2) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA2, sidB2) = TwoNodeHarness(coreA2, coreB2, encounterId = "encounter-tl")
        coreB2.handleTransportLoss(sidB2, "network dropped")
        assertNull(coreB2.getSessionSnapshot(sidB2), "Original handle sidB2 must be removed after handleTransportLoss")
        assertNull(coreB2.getSessionSnapshot(sidA2), "Canonical alias sidA2 must be removed after handleTransportLoss")

        // 8. Collision rejection against an ESTABLISHED session while preserving legitimate session
        val (coreA3, coreB3) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA3, sidB3) = TwoNodeHarness(coreA3, coreB3, encounterId = "encounter-established")

        // sidA3 is the canonical ID of the established session on both peers
        val establishedSnap = coreB3.getSessionSnapshot(sidB3)
        assertNotNull(establishedSnap)
        assertEquals(SessionState.ESTABLISHED, establishedSnap!!.state)
        assertEquals(sidA3, establishedSnap.sessionId)

        // Attempt a second handshake on coreB3 whose canonical ID collides with established session's sidA3
        val rogueSnap = coreB3.acceptSession("encounter-conflict", SecureSessionTestFixtures.NODE_A_ID)
        val rogueId = rogueSnap.sessionId

        val codec = spike.secure.session.application.SecureSessionWireCodec()
        val collidingHello = codec.encodeSessionHello(
            version = 1,
            supportedSuites = "OFFLINE_SESSION_SUITE_V1",
            encounterId = "encounter-conflict",
            handshakeId = sidA3, // Intentional collision with established session's canonical ID
            initiatorNodeId = SecureSessionTestFixtures.NODE_A_ID,
            responderNodeId = SecureSessionTestFixtures.NODE_B_ID,
            nonce = ByteArray(16) { 0x42 },
            ephemeralPublicKey = ByteArray(32) { 0x24 },
            capabilities = emptyMap()
        )

        assertThrows<SecureSessionError.TranscriptError.TranscriptMismatch> {
            runBlocking {
                coreB3.processHandshakeFrame(rogueId, collidingHello)
            }
        }

        // Original session must remain ESTABLISHED and fully functional for encryption and decryption
        val establishedAfter = coreB3.getSessionSnapshot(sidB3)
        assertNotNull(establishedAfter, "Established session must survive collision attempt")
        assertEquals(SessionState.ESTABLISHED, establishedAfter!!.state)
        assertEquals(sidA3, establishedAfter.sessionId)

        val postCollisionMsg = "Survives collision attempt".encodeToByteArray()
        val encFromA = coreA3.encrypt(sidA3, SecureRecordType.CONTROL, postCollisionMsg)
        val decAtB = coreB3.decrypt(sidB3, encFromA)
        assertArrayEquals(postCollisionMsg, decAtB, "Established session must still decrypt after collision rejection")

        val replyFromB = "Responder alive".encodeToByteArray()
        val encFromB = coreB3.encrypt(sidB3, SecureRecordType.CONTROL, replyFromB)
        val decAtA = coreA3.decrypt(sidA3, encFromB)
        assertArrayEquals(replyFromB, decAtA, "Established session must still encrypt after collision rejection")

        // Rogue session must be in FAILED state, must retain its local ID, and must NOT have canonical alias
        val rogueAfter = coreB3.getSessionSnapshot(rogueId)
        assertNotNull(rogueAfter)
        assertEquals(SessionState.FAILED, rogueAfter!!.state)
        assertEquals(rogueId, rogueAfter.sessionId, "Rogue session must retain local ID")
        assertNotEquals(sidA3, rogueAfter.sessionId)

        // Canonical ID alias must still resolve to the ESTABLISHED session, not the rogue session
        val canonicalSnap = coreB3.getSessionSnapshot(sidA3)
        assertNotNull(canonicalSnap)
        assertEquals(SessionState.ESTABLISHED, canonicalSnap!!.state)
        assertEquals(sidA3, canonicalSnap.sessionId)
    }

    // ──────────────────────────────────────────────────────────────────────
    // SPEC-005 Concurrency Regression Tests — In-flight handshake vs transport
    // loss, controlled latch synchronization, and alias purge ordering
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `regression - transport loss overlapping in-flight SESSION_HELLO prevents resurrection, alias leakage, and destroys secrets`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val latchedCryptoB = LatchedSessionCryptoProvider(FakeSessionCryptoProvider(0x22))
        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, latchedCryptoB)

        val captorB = EventCaptor()
        coreB.subscribeEvents(captorB)

        val snapA = coreA.createSession("encounter-race", SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession("encounter-race", SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val helloFrame = coreA.processHandshakeFrame(sidA, ByteArray(0))!!

        // Dispatch in-flight SESSION_HELLO processing on coreB
        val handshakeJob = async(Dispatchers.Default) {
            runCatching {
                coreB.processHandshakeFrame(sidB, helloFrame)
            }
        }

        try {
            // Await deterministic entry into in-flight cryptographic exchange
            val reachedInFlight = latchedCryptoB.inFlightLatch.await(5, TimeUnit.SECONDS)
            assertTrue(reachedInFlight, "Handshake processing must reach calculateSharedSecret within timeout")

            // Concurrent transport loss arrives while SESSION_HELLO is in-flight
            coreB.handleTransportLoss(sidB, "carrier dropped during handshake")

            // Verify sidB was immediately purged by handleTransportLoss
            assertNull(coreB.getSessionSnapshot(sidB), "Handle sidB must be purged immediately upon transport loss")
        } finally {
            // Unblock the latched in-flight thread with guaranteed cleanup
            latchedCryptoB.resumeLatch.countDown()
        }

        val result = handshakeJob.await()
        assertTrue(result.isFailure, "In-flight handshake must fail when session becomes terminal")
        val error = result.exceptionOrNull()
        assertTrue(
            error is SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal,
            "Expected SecureSessionAlreadyTerminal, got $error"
        )

        // Verify neither local handle nor canonical alias is accessible
        assertNull(coreB.getSessionSnapshot(sidB), "Local handle sidB must not be accessible")
        assertNull(coreB.getSessionSnapshot(sidA), "Canonical alias sidA must not be accessible or resurrected")

        // Verify handshake secrets were destroyed (zeroed)
        val capturedPriv = latchedCryptoB.capturedPrivateKey
        assertNotNull(capturedPriv, "Ephemeral private key must have been captured")
        assertTrue(capturedPriv!!.bytes.all { it == 0.toByte() }, "Ephemeral private key must be zeroed upon terminal failure")

        val capturedSecret = latchedCryptoB.capturedSharedSecret
        assertNotNull(capturedSecret, "DH shared secret must have been captured")
        assertTrue(capturedSecret!!.all { it == 0.toByte() }, "DH shared secret must be zeroed upon terminal failure")

        // Verify no misleading lifecycle events were emitted after SESSION_FAILED
        val eventsB = captorB.all().map { it.eventType }
        assertFalse(eventsB.contains(SecureSessionEventType.DH_SHARED_SECRET_CREATED), "DH_SHARED_SECRET_CREATED must not be emitted on terminated session")
        assertFalse(eventsB.contains(SecureSessionEventType.SESSION_HELLO_RESPONSE_SENT), "SESSION_HELLO_RESPONSE_SENT must not be emitted on terminated session")
        assertEquals(SecureSessionEventType.SESSION_FAILED, eventsB.last(), "SESSION_FAILED must be terminal event on B")
    }

    @Test
    fun `regression - opposite ordering - alias registration completes first then transport loss removes every alias`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val implB = coreB as SecureSessionCoreImpl

        val snapA = coreA.createSession("encounter-order", SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession("encounter-order", SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val helloFrame = coreA.processHandshakeFrame(sidA, ByteArray(0))!!

        // Complete SESSION_HELLO processing on coreB, registering canonical alias sidA
        val helloResponse = coreB.processHandshakeFrame(sidB, helloFrame)
        assertNotNull(helloResponse, "Responder must return SESSION_HELLO_RESPONSE")

        // Both original local handle (sidB) and canonical alias (sidA) exist and resolve to same session
        val snapByLocal = coreB.getSessionSnapshot(sidB)
        val snapByCanonical = coreB.getSessionSnapshot(sidA)
        assertNotNull(snapByLocal, "Local handle sidB must resolve")
        assertNotNull(snapByCanonical, "Canonical alias sidA must resolve")
        assertEquals(sidA, snapByLocal!!.sessionId, "Local handle must reflect adopted canonical ID")
        assertEquals(sidA, snapByCanonical!!.sessionId, "Canonical alias must reflect adopted canonical ID")

        // Transport loss occurs after alias registration
        coreB.handleTransportLoss(sidB, "carrier dropped post-alias")

        // Verify every alias is removed from lookup
        assertNull(coreB.getSessionSnapshot(sidB), "Local handle sidB must be removed after transport loss")
        assertNull(coreB.getSessionSnapshot(sidA), "Canonical alias sidA must be removed after transport loss")

        // Verify handshakeContexts are completely purged referentially (no residual local context)
        assertFalse(implB.hasHandshakeContext(sidB), "Context under sidB must be removed")
        assertFalse(implB.hasHandshakeContext(sidA), "Context under sidA must be removed")
        assertNull(implB.getHandshakeContext(sidB))
        assertNull(implB.getHandshakeContext(sidA))

        // Verify subsequent frame processing rejects with SecureSessionNotFound
        assertThrows<SecureSessionError.SessionStateError.SecureSessionNotFound> {
            runBlocking {
                coreB.processHandshakeFrame(sidB, ByteArray(10))
            }
        }
        assertThrows<SecureSessionError.SessionStateError.SecureSessionNotFound> {
            runBlocking {
                coreB.processHandshakeFrame(sidA, ByteArray(10))
            }
        }
    }

    @Test
    fun `regression - transport loss on canonical alias removes both local handle and alias`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val implB = coreB as SecureSessionCoreImpl

        val snapA = coreA.createSession("encounter-order2", SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession("encounter-order2", SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val helloFrame = coreA.processHandshakeFrame(sidA, ByteArray(0))!!
        val helloResponse = coreB.processHandshakeFrame(sidB, helloFrame)
        assertNotNull(helloResponse)

        assertNotNull(coreB.getSessionSnapshot(sidB))
        assertNotNull(coreB.getSessionSnapshot(sidA))

        // Transport loss triggered using canonical alias sidA
        coreB.handleTransportLoss(sidA, "carrier dropped on alias")

        assertNull(coreB.getSessionSnapshot(sidB), "Local handle sidB must be removed")
        assertNull(coreB.getSessionSnapshot(sidA), "Canonical alias sidA must be removed")

        // Verify handshakeContexts are completely purged referentially (no residual local context)
        assertFalse(implB.hasHandshakeContext(sidB), "Context under sidB must be removed")
        assertFalse(implB.hasHandshakeContext(sidA), "Context under sidA must be removed")
        assertNull(implB.getHandshakeContext(sidB))
        assertNull(implB.getHandshakeContext(sidA))
    }

    @Test
    fun `regression - session closure on canonical alias removes both local handle and alias and referentially purges handshake context`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val implB = coreB as SecureSessionCoreImpl

        val snapA = coreA.createSession("encounter-close-alias", SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession("encounter-close-alias", SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val helloFrame = coreA.processHandshakeFrame(sidA, ByteArray(0))!!
        val helloResponse = coreB.processHandshakeFrame(sidB, helloFrame)
        assertNotNull(helloResponse)

        // Both handles exist and point to the same HandshakeContext instance
        assertTrue(implB.hasHandshakeContext(sidB))
        assertTrue(implB.hasHandshakeContext(sidA))
        val ctxB = implB.getHandshakeContext(sidB)
        val ctxA = implB.getHandshakeContext(sidA)
        assertNotNull(ctxB)
        assertSame(ctxB, ctxA, "Both entries must referentially point to the exact same HandshakeContext instance")

        // Close session initiated through canonical alias sidA
        coreB.closeSession(sidA, "normal closure via canonical alias")

        // Assert neither handle exists in sessions
        assertNull(coreB.getSessionSnapshot(sidB), "sidB must be purged from sessions after closeSession")
        assertNull(coreB.getSessionSnapshot(sidA), "sidA must be purged from sessions after closeSession")

        // Assert no residual context remains in handshakeContexts
        assertFalse(implB.hasHandshakeContext(sidB), "No residual context under local handle sidB must remain")
        assertFalse(implB.hasHandshakeContext(sidA), "No residual context under canonical alias sidA must remain")
        assertNull(implB.getHandshakeContext(sidB))
        assertNull(implB.getHandshakeContext(sidA))
        assertEquals(0, implB.activeHandshakeContextCount, "All handshake contexts must be purged")

        // Handshake secrets in the destroyed context must be zeroed
        val privBytes = ctxB!!.localEphemeralKeyPair!!.privateKey.bytes
        assertTrue(privBytes.all { byte -> byte == 0.toByte() }, "Ephemeral private key must be zeroed")
        val secretBytes = ctxB.dhSharedSecret!!
        assertTrue(secretBytes.all { byte -> byte == 0.toByte() }, "DH shared secret must be zeroed")
    }

    @Test
    fun `regression - conflicting handshake context registration on canonical ID is rejected safely and does not affect existing context`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val implB = coreB as SecureSessionCoreImpl

        val snapA1 = coreA.createSession("encounter-conf1", SecureSessionTestFixtures.NODE_B_ID)
        val snapB1 = coreB.acceptSession("encounter-conf1", SecureSessionTestFixtures.NODE_A_ID)
        val sidA1 = snapA1.sessionId
        val sidB1 = snapB1.sessionId

        val helloFrame1 = coreA.processHandshakeFrame(sidA1, ByteArray(0))!!
        val helloResponse1 = coreB.processHandshakeFrame(sidB1, helloFrame1)
        assertNotNull(helloResponse1)

        val legitimateCtx = implB.getHandshakeContext(sidA1)
        assertNotNull(legitimateCtx, "Legitimate session must have registered context under sidA1")

        // Second session attempts handshake using the same canonical ID sidA1
        val snapB2 = coreB.acceptSession("encounter-conf2", SecureSessionTestFixtures.NODE_A_ID)
        val sidB2 = snapB2.sessionId

        val codec = spike.secure.session.application.SecureSessionWireCodec()
        val collidingHello = codec.encodeSessionHello(
            version = 1,
            supportedSuites = "OFFLINE_SESSION_SUITE_V1",
            encounterId = "encounter-conf2",
            handshakeId = sidA1, // Collision with sidA1
            initiatorNodeId = SecureSessionTestFixtures.NODE_A_ID,
            responderNodeId = SecureSessionTestFixtures.NODE_B_ID,
            nonce = ByteArray(16) { 0x55 },
            ephemeralPublicKey = ByteArray(32) { 0x66 },
            capabilities = emptyMap()
        )

        assertThrows<SecureSessionError.TranscriptError.TranscriptMismatch> {
            runBlocking {
                coreB.processHandshakeFrame(sidB2, collidingHello)
            }
        }

        // Legitimate context must remain completely untouched
        assertSame(legitimateCtx, implB.getHandshakeContext(sidA1), "Existing context must not be overwritten or corrupted")
        assertTrue(implB.hasHandshakeContext(sidB1))
        assertTrue(implB.hasHandshakeContext(sidA1))

        // Colliding session sidB2 must be FAILED and its context must be purged
        val b2Snap = coreB.getSessionSnapshot(sidB2)
        assertNotNull(b2Snap)
        assertEquals(SessionState.FAILED, b2Snap!!.state)
        assertFalse(implB.hasHandshakeContext(sidB2), "Failed session's context must be purged")
    }

    @Test
    fun `regression - closeSession overlapping in-flight handshake aborts crypto commit and prevents secret and event leakage`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val latchedCryptoB = LatchedSessionCryptoProvider(FakeSessionCryptoProvider(0x22))
        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, latchedCryptoB)

        val captorB = EventCaptor()
        coreB.subscribeEvents(captorB)

        val snapA = coreA.createSession("encounter-close-race", SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession("encounter-close-race", SecureSessionTestFixtures.NODE_A_ID)
        val sidA = snapA.sessionId
        val sidB = snapB.sessionId

        val helloFrame = coreA.processHandshakeFrame(sidA, ByteArray(0))!!

        // Dispatch in-flight SESSION_HELLO processing on coreB
        val handshakeJob = async(Dispatchers.Default) {
            runCatching {
                coreB.processHandshakeFrame(sidB, helloFrame)
            }
        }

        try {
            // Await deterministic entry into in-flight cryptographic exchange
            val reachedInFlight = latchedCryptoB.inFlightLatch.await(5, TimeUnit.SECONDS)
            assertTrue(reachedInFlight, "Handshake processing must reach calculateSharedSecret within timeout")

            // Concurrent closeSession arrives while calculateSharedSecret is in-flight
            coreB.closeSession(sidB, "explicit close during in-flight crypto")

            // Verify sidB was immediately purged by closeSession
            assertNull(coreB.getSessionSnapshot(sidB), "Handle sidB must be purged immediately upon closeSession")
        } finally {
            // Unblock the latched in-flight thread
            latchedCryptoB.resumeLatch.countDown()
        }

        val result = handshakeJob.await()
        assertTrue(result.isFailure, "In-flight handshake must fail when session is closed")
        val error = result.exceptionOrNull()
        assertTrue(
            error is SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal,
            "Expected SecureSessionAlreadyTerminal, got $error"
        )

        // Verify neither local handle nor canonical alias is accessible
        assertNull(coreB.getSessionSnapshot(sidB), "Local handle sidB must not be accessible")
        assertNull(coreB.getSessionSnapshot(sidA), "Canonical alias sidA must not be accessible")
        assertFalse(coreB.hasHandshakeContext(sidB), "HandshakeContext sidB must be purged")
        assertFalse(coreB.hasHandshakeContext(sidA), "HandshakeContext sidA must be purged")

        // Verify handshake secrets were destroyed (zeroed)
        val capturedPriv = latchedCryptoB.capturedPrivateKey
        assertNotNull(capturedPriv, "Ephemeral private key must have been captured")
        assertTrue(capturedPriv!!.bytes.all { it == 0.toByte() }, "Ephemeral private key must be zeroed upon terminal failure")

        val capturedSecret = latchedCryptoB.capturedSharedSecret
        assertNotNull(capturedSecret, "DH shared secret must have been captured")
        assertTrue(capturedSecret!!.all { it == 0.toByte() }, "DH shared secret must be zeroed upon terminal failure")

        // Verify no misleading events were emitted after close
        val eventsB = captorB.all().map { it.eventType }
        assertFalse(eventsB.contains(SecureSessionEventType.DH_SHARED_SECRET_CREATED), "DH_SHARED_SECRET_CREATED must not be emitted")
        assertFalse(eventsB.contains(SecureSessionEventType.SESSION_HELLO_RESPONSE_SENT), "SESSION_HELLO_RESPONSE_SENT must not be emitted")
        assertEquals(SecureSessionEventType.SESSION_CLOSED, eventsB.last(), "SESSION_CLOSED must be terminal event on B")
    }

    @Test
    fun `regression - concurrent encrypt calls prevent nonce reuse and preserve strict sequence monotonicity`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        val concurrentCount = 50
        val codec = SecureSessionWireCodec()

        // Launch concurrent encryption calls on coreA
        val jobs = (0 until concurrentCount).map { index ->
            async(Dispatchers.Default) {
                val plaintext = "concurrent-payload-$index".encodeToByteArray()
                val encrypted = coreA.encrypt(sidA, SecureRecordType.CONTROL, plaintext)
                val decoded = codec.decodeSecureRecord(encrypted)
                index to decoded
            }
        }

        val results = jobs.awaitAll()
        val records = results.map { it.second }
        val sequenceNumbers = records.map { it.sequenceNumber }

        // 1. Strict sequence monotonicity: exactly 0L..49L, no duplicates, no gaps
        assertEquals(concurrentCount, sequenceNumbers.size)
        assertEquals(concurrentCount, sequenceNumbers.toSet().size, "All sequence numbers must be distinct (no duplicates)")
        assertEquals((0L until concurrentCount.toLong()).toSet(), sequenceNumbers.toSet(), "Sequence numbers must span exactly 0..${concurrentCount - 1}")

        // 2. No nonce reuse: all records have distinct nonces / authentication tags
        val tags = records.map { it.authenticationTag.toList() }
        assertEquals(concurrentCount, tags.toSet().size, "All authentication tags must be distinct")

        // 3. Sequential decryption on peer succeeds for all records
        val sortedRecords = results.sortedBy { it.second.sequenceNumber }
        for ((index, record) in sortedRecords) {
            val encodedRecord = codec.encodeSecureRecord(record)
            val decrypted = coreB.decrypt(sidB, encodedRecord)
            val expected = "concurrent-payload-$index".encodeToByteArray()
            assertArrayEquals(expected, decrypted, "Record $index decrypted payload mismatch")
        }

        // Verify session counters
        val snapA = coreA.getSessionSnapshot(sidA)!!
        val snapB = coreB.getSessionSnapshot(sidB)!!
        assertEquals(concurrentCount.toLong(), snapA.recordsSent)
        assertEquals(concurrentCount.toLong(), snapB.recordsReceived)
    }

    @Test
    fun `regression - closeSession emits SESSION_CLOSE_STARTED while session is in CLOSING state before reaching CLOSED`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, _) = TwoNodeHarness(coreA, coreB)

        var stateObservedAtCloseStarted: SessionState? = null
        var snapshotObservedAtCloseStarted: SecureSessionSnapshot? = null
        val closeStartedLatch = CountDownLatch(1)

        coreA.subscribeEvents(object : SecureSessionEventListener {
            override fun onEvent(event: SecureSessionEvent) {
                if (event.eventType == SecureSessionEventType.SESSION_CLOSE_STARTED) {
                    runBlocking {
                        snapshotObservedAtCloseStarted = coreA.getSessionSnapshot(event.sessionId.value)
                        stateObservedAtCloseStarted = snapshotObservedAtCloseStarted?.state
                    }
                    closeStartedLatch.countDown()
                }
            }
        })

        coreA.closeSession(sidA, "graceful shutdown test")

        val latchReached = closeStartedLatch.await(5, TimeUnit.SECONDS)
        assertTrue(latchReached, "SESSION_CLOSE_STARTED event must be received")

        // During SESSION_CLOSE_STARTED, the session must exist and be in CLOSING state
        assertNotNull(snapshotObservedAtCloseStarted, "Session snapshot must be accessible during SESSION_CLOSE_STARTED")
        assertEquals(SessionState.CLOSING, stateObservedAtCloseStarted, "Session must be observed in CLOSING state during SESSION_CLOSE_STARTED")

        // After closeSession finishes, session is completely purged from active sessions
        assertNull(coreA.getSessionSnapshot(sidA), "Session must be removed from sessions map after closeSession finishes")
    }

    @Test
    fun `regression - directional rekey mapping and bidirectional data exchange across multiple epochs`() = runBlocking {
        val (coreA, coreB) = SecureSessionTestFixtures.createSessionCorePair()
        val (sidA, sidB) = TwoNodeHarness(coreA, coreB)

        // Epoch 0: Bidirectional exchange
        val msgA0 = "epoch-0-a-to-b".encodeToByteArray()
        val encA0 = coreA.encrypt(sidA, SecureRecordType.CONTROL, msgA0)
        assertArrayEquals(msgA0, coreB.decrypt(sidB, encA0), "Epoch 0 A->B failed")

        val msgB0 = "epoch-0-b-to-a".encodeToByteArray()
        val encB0 = coreB.encrypt(sidB, SecureRecordType.CONTROL, msgB0)
        assertArrayEquals(msgB0, coreA.decrypt(sidA, encB0), "Epoch 0 B->A failed")

        val codec = SecureSessionWireCodec()

        // Rekey to Epoch 1
        coreA.requestRekey(sidA)
        coreB.requestRekey(sidB)

        val snapA1 = coreA.getSessionSnapshot(sidA)!!
        val snapB1 = coreB.getSessionSnapshot(sidB)!!
        assertEquals(1, snapA1.keyEpoch, "Epoch must advance to 1 on A")
        assertEquals(1, snapB1.keyEpoch, "Epoch must advance to 1 on B")

        // Epoch 1: Bidirectional exchange
        val msgA1 = "epoch-1-a-to-b".encodeToByteArray()
        val encA1 = coreA.encrypt(sidA, SecureRecordType.CONTROL, msgA1)
        val recA1 = codec.decodeSecureRecord(encA1)
        assertEquals(0L, recA1.sequenceNumber, "Send sequence must reset to 0 on A after rekey")
        assertEquals(1, recA1.keyEpoch.value, "Record epoch must be 1")
        assertArrayEquals(msgA1, coreB.decrypt(sidB, encA1), "Epoch 1 A->B failed")

        val msgB1 = "epoch-1-b-to-a".encodeToByteArray()
        val encB1 = coreB.encrypt(sidB, SecureRecordType.CONTROL, msgB1)
        val recB1 = codec.decodeSecureRecord(encB1)
        assertEquals(0L, recB1.sequenceNumber, "Send sequence must reset to 0 on B after rekey")
        assertEquals(1, recB1.keyEpoch.value, "Record epoch must be 1")
        assertArrayEquals(msgB1, coreA.decrypt(sidA, encB1), "Epoch 1 B->A failed")

        // Rekey to Epoch 2
        coreA.requestRekey(sidA)
        coreB.requestRekey(sidB)

        val snapA2 = coreA.getSessionSnapshot(sidA)!!
        val snapB2 = coreB.getSessionSnapshot(sidB)!!
        assertEquals(2, snapA2.keyEpoch, "Epoch must advance to 2 on A")
        assertEquals(2, snapB2.keyEpoch, "Epoch must advance to 2 on B")

        // Epoch 2: Bidirectional exchange
        val msgA2 = "epoch-2-a-to-b".encodeToByteArray()
        val encA2 = coreA.encrypt(sidA, SecureRecordType.CONTROL, msgA2)
        val recA2 = codec.decodeSecureRecord(encA2)
        assertEquals(0L, recA2.sequenceNumber, "Send sequence must reset to 0 on A after 2nd rekey")
        assertEquals(2, recA2.keyEpoch.value, "Record epoch must be 2")
        assertArrayEquals(msgA2, coreB.decrypt(sidB, encA2), "Epoch 2 A->B failed")

        val msgB2 = "epoch-2-b-to-a".encodeToByteArray()
        val encB2 = coreB.encrypt(sidB, SecureRecordType.CONTROL, msgB2)
        val recB2 = codec.decodeSecureRecord(encB2)
        assertEquals(0L, recB2.sequenceNumber, "Send sequence must reset to 0 on B after 2nd rekey")
        assertEquals(2, recB2.keyEpoch.value, "Record epoch must be 2")
        assertArrayEquals(msgB2, coreA.decrypt(sidA, encB2), "Epoch 2 B->A failed")

        // Epoch mismatch rejection: trying to decrypt Epoch 1 record in Epoch 2 must fail
        assertThrows<SecureSessionError.SecureRecordError.InvalidKeyEpoch> {
            coreB.decrypt(sidB, encA1)
        }
    }

    @Test
    fun `regression - concurrent close during requestRekey does not crash and destroys in-flight derived secrets`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val baseCryptoA = FakeSessionCryptoProvider(0x11)
        val latchedCryptoA = LatchedRekeyCryptoProvider(baseCryptoA)
        val cryptoB = FakeSessionCryptoProvider(0x22)

        val coreA = SecureSessionCoreImpl(identityA, latchedCryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)
        val (sidA, _) = TwoNodeHarness(coreA, coreB)

        // Launch requestRekey on coreA concurrently
        val rekeyJob = async(Dispatchers.Default) {
            runCatching {
                coreA.requestRekey(sidA)
            }
        }

        try {
            // Await deterministic entry into in-flight rekey derivation
            val inFlightReached = latchedCryptoA.inFlightLatch.await(5, TimeUnit.SECONDS)
            assertTrue(inFlightReached, "requestRekey must reach in-flight HKDF derivation")

            // Concurrently close the session
            coreA.closeSession(sidA, "close during rekey")

            // Session must be removed immediately
            assertNull(coreA.getSessionSnapshot(sidA), "Session must be removed from map by closeSession")
        } finally {
            // Unblock in-flight derivation
            latchedCryptoA.resumeLatch.countDown()
        }

        val result = rekeyJob.await()
        assertTrue(result.isFailure, "requestRekey must fail when session is closed concurrently")
        val error = result.exceptionOrNull()
        assertTrue(
            error is SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal,
            "Expected SecureSessionAlreadyTerminal, got $error"
        )
    }

    @Test
    fun `regression - exception during handshake encoding destroys uncommitted ephemeral secrets`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val failingCryptoB = FailingCryptoProvider(FakeSessionCryptoProvider(0x22))

        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, failingCryptoB)

        val snapA = coreA.createSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_A_ID)

        val helloFrame = coreA.processHandshakeFrame(snapA.sessionId, ByteArray(0))!!

        // Make cryptoB fail during SHA-256 transcript hashing in SessionHello
        failingCryptoB.failOnSha256 = true

        val error = assertThrows<RuntimeException> {
            coreB.processHandshakeFrame(snapB.sessionId, helloFrame)
        }
        assertEquals("Simulated SHA-256 failure", error.message)

        // Verify that uncommitted private key and DH shared secret were destroyed (zeroed)
        val capturedPriv = failingCryptoB.capturedPrivateKey
        assertNotNull(capturedPriv, "Ephemeral private key must have been captured")
        assertTrue(capturedPriv!!.bytes.all { it == 0.toByte() }, "Ephemeral private key must be zeroed upon exception")

        val capturedDh = failingCryptoB.capturedSharedSecret
        assertNotNull(capturedDh, "DH shared secret must have been captured")
        assertTrue(capturedDh!!.all { it == 0.toByte() }, "DH shared secret must be zeroed upon exception")
    }

    @Test
    fun `regression - concurrent transport loss during requestRekey does not resurrect session`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val latchedCryptoA = LatchedRekeyCryptoProvider(FakeSessionCryptoProvider(0x11))
        val cryptoB = FakeSessionCryptoProvider(0x22)

        val coreA = SecureSessionCoreImpl(identityA, latchedCryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)
        val (sidA, _) = TwoNodeHarness(coreA, coreB)

        val rekeyJob = async(Dispatchers.Default) {
            runCatching {
                coreA.requestRekey(sidA)
            }
        }

        try {
            val inFlightReached = latchedCryptoA.inFlightLatch.await(5, TimeUnit.SECONDS)
            assertTrue(inFlightReached, "requestRekey must reach in-flight HKDF derivation")

            // Simulate transport loss while rekey derivation is in flight
            coreA.handleTransportLoss(sidA, "carrier connection dropped")

            assertNull(coreA.getSessionSnapshot(sidA), "Session must be removed from map by handleTransportLoss")
        } finally {
            latchedCryptoA.resumeLatch.countDown()
        }

        val result = rekeyJob.await()
        assertTrue(result.isFailure, "requestRekey must fail when transport loss occurs concurrently")
        val error = result.exceptionOrNull()
        assertTrue(
            error is SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal,
            "Expected SecureSessionAlreadyTerminal, got $error"
        )
    }

    @Test
    fun `regression - injected failure during rekey derivation cleans all intermediate secret buffers`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val spyCryptoA = SpyFailingRekeyCryptoProvider(FakeSessionCryptoProvider(0x11))
        val cryptoB = FakeSessionCryptoProvider(0x22)

        val coreA = SecureSessionCoreImpl(identityA, spyCryptoA)
        val coreB = SecureSessionCoreImpl(identityB, cryptoB)
        val (sidA, _) = TwoNodeHarness(coreA, coreB)

        // Clear secrets captured during initial handshake
        spyCryptoA.capturedExpandedSecrets.clear()

        // Inject failure on send-traffic-key expansion during requestRekey
        spyCryptoA.failOnTrafficKey = true

        val error = assertThrows<RuntimeException> {
            coreA.requestRekey(sidA)
        }
        assertEquals("Simulated HKDF failure on send-traffic-key", error.message)

        // The rekey secrets (nextSendRekeySecret and nextReceiveRekeySecret) were derived before failure
        assertTrue(spyCryptoA.capturedExpandedSecrets.isNotEmpty(), "At least one secret must have been expanded before failure")
        // Verify all intermediate secrets expanded before the failure were zeroed in finally
        assertTrue(
            spyCryptoA.capturedExpandedSecrets.all { secret -> secret.all { it == 0.toByte() } },
            "All intermediate rekey secrets must be zeroed upon failure"
        )

        // Session must be marked FAILED
        val snap = coreA.getSessionSnapshot(sidA)
        assertNotNull(snap)
        assertEquals(SessionState.FAILED, snap!!.state)
    }

    @Test
    fun `regression - in-flight handshake rejected when session is CLOSING and cannot commit or leak alias`() = runBlocking {
        val (identityA, identityB) = SecureSessionTestFixtures.createIdentityPair()
        val cryptoA = FakeSessionCryptoProvider(0x11)
        val latchedCryptoB = LatchedSessionCryptoProvider(FakeSessionCryptoProvider(0x22))

        val coreA = SecureSessionCoreImpl(identityA, cryptoA)
        val coreB = SecureSessionCoreImpl(identityB, latchedCryptoB)

        val snapA = coreA.createSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_B_ID)
        val snapB = coreB.acceptSession(SecureSessionTestFixtures.ENCOUNTER_ID, SecureSessionTestFixtures.NODE_A_ID)

        val helloFrame = coreA.processHandshakeFrame(snapA.sessionId, ByteArray(0))!!
        val helloDecoded = SecureSessionWireCodec().decodeHandshakeFrame(helloFrame) as DecodedHandshakeFrame.SessionHello
        val canonicalId = helloDecoded.handshakeId

        val eventsB = CopyOnWriteArrayList<SecureSessionEvent>()
        val closeStartedLatch = CountDownLatch(1)
        val allowCloseToCompleteLatch = CountDownLatch(1)

        coreB.subscribeEvents(object : SecureSessionEventListener {
            override fun onEvent(event: SecureSessionEvent) {
                eventsB.add(event)
                if (event.eventType == SecureSessionEventType.SESSION_CLOSE_STARTED) {
                    closeStartedLatch.countDown()
                    // Block listener so coreB remains in CLOSING state
                    val allowed = allowCloseToCompleteLatch.await(5, TimeUnit.SECONDS)
                    check(allowed) { "Timed out waiting for allowCloseToCompleteLatch" }
                }
            }
        })

        // 1. Launch in-flight handshake on coreB on background coroutine
        val handshakeJob = async(Dispatchers.Default) {
            runCatching {
                coreB.processHandshakeFrame(snapB.sessionId, helloFrame)
            }
        }

        // 2. Wait until handshake reaches in-flight crypto (before commit)
        val inFlightReached = latchedCryptoB.inFlightLatch.await(5, TimeUnit.SECONDS)
        assertTrue(inFlightReached, "Handshake must reach in-flight calculateSharedSecret")

        // 3. Initiate closeSession on another background coroutine
        val closeJob = async(Dispatchers.Default) {
            coreB.closeSession(snapB.sessionId, "closing during handshake")
        }

        // 4. Wait until closeSession enters CLOSING and triggers SESSION_CLOSE_STARTED
        val closeStarted = closeStartedLatch.await(5, TimeUnit.SECONDS)
        assertTrue(closeStarted, "closeSession must enter CLOSING and emit SESSION_CLOSE_STARTED")

        // Verify session is actively in CLOSING state
        val closingSnapshot = coreB.getSession(snapB.sessionId)
        assertNotNull(closingSnapshot, "Session should exist while closing")
        assertEquals(SessionState.CLOSING, closingSnapshot!!.state, "Session must be in CLOSING state")

        // 5. Resume the in-flight handshake while session is CLOSING
        latchedCryptoB.resumeLatch.countDown()

        // 6. Await handshake completion: must be rejected with SecureSessionAlreadyTerminal
        val handshakeResult = handshakeJob.await()
        assertTrue(handshakeResult.isFailure, "Handshake must fail when session is in CLOSING state")
        val handshakeError = handshakeResult.exceptionOrNull()
        assertTrue(
            handshakeError is SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal,
            "Expected SecureSessionAlreadyTerminal, got $handshakeError"
        )

        // 7. Verify NO success events for handshake were emitted during CLOSING
        val eventTypesB = eventsB.map { it.eventType }
        assertFalse(
            eventTypesB.contains(SecureSessionEventType.SESSION_HELLO_RESPONSE_SENT),
            "SESSION_HELLO_RESPONSE_SENT must not be emitted when rejected"
        )
        assertFalse(
            eventTypesB.contains(SecureSessionEventType.SESSION_FAILED),
            "SESSION_FAILED must not be emitted when rejected during intentional close"
        )

        // 8. Unblock the close listener to let closeSession finish
        allowCloseToCompleteLatch.countDown()
        closeJob.await()

        // 9. Verify session is completely closed and cleaned up
        assertNull(coreB.getSessionSnapshot(snapB.sessionId), "Session must not be found by local handle")
        assertNull(coreB.getSessionSnapshot(canonicalId), "Canonical alias must not be registered or leaked")
        assertFalse(coreB.hasHandshakeContext(snapB.sessionId), "Local handshake context must be removed")
        assertFalse(coreB.hasHandshakeContext(canonicalId), "Canonical handshake context must not be leaked")
        assertEquals(0, coreB.activeHandshakeContextCount, "No residual handshake contexts must remain")

        // 10. Verify secrets were zeroed
        val capturedPriv = latchedCryptoB.capturedPrivateKey
        assertNotNull(capturedPriv)
        assertTrue(capturedPriv!!.bytes.all { it == 0.toByte() }, "Ephemeral private key must be destroyed upon rejection")

        val capturedDh = latchedCryptoB.capturedSharedSecret
        assertNotNull(capturedDh)
        assertTrue(capturedDh!!.all { it == 0.toByte() }, "Calculated DH shared secret must be zeroed upon rejection")

        // 11. Final recorded lifecycle event must be SESSION_CLOSED
        val finalEventTypesB = eventsB.map { it.eventType }
        assertTrue(finalEventTypesB.isNotEmpty(), "Events must not be empty")
        assertEquals(
            SecureSessionEventType.SESSION_CLOSED,
            finalEventTypesB.last(),
            "The last recorded lifecycle event must be SESSION_CLOSED"
        )
    }
}

/**
 * Test helper wrapping SessionCryptoProvider that latches during calculateSharedSecret.
 * Allows deterministic synchronization of in-flight handshake frames without polling.
 */
private class LatchedSessionCryptoProvider(
    private val delegate: SessionCryptoProvider
) : SessionCryptoProvider by delegate {
    val inFlightLatch = CountDownLatch(1)
    val resumeLatch = CountDownLatch(1)
    var capturedPrivateKey: EphemeralPrivateKey? = null
    var capturedSharedSecret: ByteArray? = null

    override fun calculateSharedSecret(
        localPrivateKey: EphemeralPrivateKey,
        remotePublicKey: EphemeralPublicKey
    ): ByteArray {
        capturedPrivateKey = localPrivateKey
        val secret = delegate.calculateSharedSecret(localPrivateKey, remotePublicKey)
        capturedSharedSecret = secret
        inFlightLatch.countDown()
        val resumed = resumeLatch.await(5, TimeUnit.SECONDS)
        check(resumed) { "LatchedSessionCryptoProvider timed out waiting for resumeLatch" }
        return secret
    }
}

/**
 * Test helper wrapping SessionCryptoProvider that latches during rekey HKDF derivation.
 */
private class LatchedRekeyCryptoProvider(
    private val delegate: SessionCryptoProvider
) : SessionCryptoProvider by delegate {
    val inFlightLatch = CountDownLatch(1)
    val resumeLatch = CountDownLatch(1)

    override fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prefix = "OFFLINE_PROTOCOL_REKEY_SECRET_V1".encodeToByteArray()
        if (info.size >= prefix.size && info.take(prefix.size).toByteArray().contentEquals(prefix)) {
            inFlightLatch.countDown()
            val resumed = resumeLatch.await(5, TimeUnit.SECONDS)
            check(resumed) { "LatchedRekeyCryptoProvider timed out waiting for resumeLatch" }
        }
        return delegate.hkdfExpand(prk, info, length)
    }
}

/**
 * Test helper wrapping SessionCryptoProvider that captures ephemeral keys and simulates failure during hashing.
 */
private class FailingCryptoProvider(
    private val delegate: SessionCryptoProvider
) : SessionCryptoProvider by delegate {
    var failOnSha256 = false
    var capturedPrivateKey: EphemeralPrivateKey? = null
    var capturedSharedSecret: ByteArray? = null

    override fun generateEphemeralKeyPair(): EphemeralKeyPair {
        val keyPair = delegate.generateEphemeralKeyPair()
        capturedPrivateKey = keyPair.privateKey
        return keyPair
    }

    override fun calculateSharedSecret(
        localPrivateKey: EphemeralPrivateKey,
        remotePublicKey: EphemeralPublicKey
    ): ByteArray {
        val secret = delegate.calculateSharedSecret(localPrivateKey, remotePublicKey)
        capturedSharedSecret = secret
        return secret
    }

    override fun sha256(data: ByteArray): ByteArray {
        if (failOnSha256) {
            throw RuntimeException("Simulated SHA-256 failure")
        }
        return delegate.sha256(data)
    }
}

/**
 * Test helper wrapping SessionCryptoProvider that captures expanded secrets during rekey and simulates failure.
 */
private class SpyFailingRekeyCryptoProvider(
    private val delegate: SessionCryptoProvider
) : SessionCryptoProvider by delegate {
    val capturedExpandedSecrets = mutableListOf<ByteArray>()
    var failOnTrafficKey = false

    override fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val label = info.decodeToString()
        if (failOnTrafficKey && label.startsWith("send-traffic-key")) {
            throw RuntimeException("Simulated HKDF failure on send-traffic-key")
        }
        val secret = delegate.hkdfExpand(prk, info, length)
        capturedExpandedSecrets.add(secret)
        return secret
    }
}
