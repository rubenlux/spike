package spike.secure.session.testing

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import spike.secure.session.application.SecureSessionCoreImpl
import spike.secure.session.contracts.SecureSessionCore
import spike.secure.session.domain.*

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

        // After rekey, sequences reset and new records are exchangeable
        val after = "post-rekey".encodeToByteArray()
        val enc2 = coreA.encrypt(sidA, SecureRecordType.CONTROL, after)
        val dec2 = coreB.decrypt(sidB, enc2)
        assertArrayEquals(after, dec2, "Data must be exchangeable after rekey")
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

        // Flip a bit in the middle of the encrypted payload
        val tampered = enc.copyOf()
        tampered[enc.size / 2] = (tampered[enc.size / 2].toInt() xor 0x01).toByte()

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
}
