package spike.secure.session.application

import spike.crypto.EphemeralKeyPair
import spike.crypto.EphemeralPublicKey
import spike.crypto.SessionCryptoProvider
import spike.identity.contracts.IdentityCore
import spike.identity.contracts.IdentityCommandDisposition
import spike.identity.contracts.VerificationResult
import spike.identity.domain.NodeId
import spike.identity.domain.SignatureBytes
import spike.identity.domain.SignatureAlgorithm
import spike.secure.session.contracts.SecureSessionCore
import spike.secure.session.contracts.SecureSessionEventListener
import spike.secure.session.domain.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SecureSessionCoreImpl(
    private val identityCore: IdentityCore,
    private val cryptoProvider: SessionCryptoProvider
) : SecureSessionCore {

    private val sessions = ConcurrentHashMap<String, SecureSession>()
    private val handshakeContexts = ConcurrentHashMap<String, HandshakeContext>()
    private val listeners = CopyOnWriteArrayList<SecureSessionEventListener>()
    private val wireCodec = SecureSessionWireCodec()

    private fun emitEvent(eventType: SecureSessionEventType, sessionId: SecureSessionId, metadata: Map<String, String> = emptyMap()) {
        val event = SecureSessionEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            sessionId = sessionId,
            occurredAtEpochMillis = System.currentTimeMillis(),
            metadata = metadata
        )
        listeners.forEach { it.onEvent(event) }
    }

    override suspend fun createSession(encounterId: String, remoteNodeId: String): SecureSessionSnapshot {
        val sessionIdVal = UUID.randomUUID().toString()
        val sessionId = SecureSessionId(sessionIdVal)

        val localNodeId = identityCore.getLocalNodeId()?.value
            ?: throw SecureSessionError.HandshakeAuthenticationError.InvalidHandshakeIdentity("Local NodeId not available")

        val role = determineRole(localNodeId, remoteNodeId)

        val handshakeId = HandshakeId(sessionIdVal)
        val context = HandshakeContext(
            handshakeId = handshakeId,
            state = HandshakeState.NEW
        )

        val session = SecureSession(
            sessionId = sessionId,
            encounterId = encounterId,
            localNodeId = localNodeId,
            remoteNodeId = remoteNodeId,
            role = role,
            state = SessionState.NEW,
            handshakeContext = context
        )

        sessions[sessionIdVal] = session
        handshakeContexts[sessionIdVal] = context

        emitEvent(SecureSessionEventType.SESSION_CREATION_STARTED, sessionId)
        emitEvent(SecureSessionEventType.SESSION_ROLE_RESOLVED, sessionId, mapOf("role" to role.name))

        return session.toSnapshot()
    }

    override suspend fun acceptSession(encounterId: String, remoteNodeId: String): SecureSessionSnapshot {
        val sessionIdVal = UUID.randomUUID().toString()
        val sessionId = SecureSessionId(sessionIdVal)

        val localNodeId = identityCore.getLocalNodeId()?.value
            ?: throw SecureSessionError.HandshakeAuthenticationError.InvalidHandshakeIdentity("Local NodeId not available")

        val role = determineRole(localNodeId, remoteNodeId)

        val handshakeId = HandshakeId(sessionIdVal)
        val context = HandshakeContext(
            handshakeId = handshakeId,
            state = HandshakeState.NEW
        )

        val session = SecureSession(
            sessionId = sessionId,
            encounterId = encounterId,
            localNodeId = localNodeId,
            remoteNodeId = remoteNodeId,
            role = role,
            state = SessionState.NEW,
            handshakeContext = context
        )

        sessions[sessionIdVal] = session
        handshakeContexts[sessionIdVal] = context

        emitEvent(SecureSessionEventType.SESSION_CREATION_STARTED, sessionId)
        emitEvent(SecureSessionEventType.SESSION_ROLE_RESOLVED, sessionId, mapOf("role" to role.name))

        return session.toSnapshot()
    }

    override suspend fun processHandshakeFrame(sessionId: String, frame: ByteArray): ByteArray? {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")
        val ctx = handshakeContexts[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Handshake context for $sessionId not found")

        synchronized(session) {
            if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in terminal state (${session.state})")
            }
        }

        try {
            if (frame.isEmpty()) {
                // Initial trigger for Initiator
                if (session.role != SessionRole.INITIATOR) {
                    throw SecureSessionError.RoleError.InvalidSessionRole("Only initiator can trigger initial frame")
                }
                synchronized(session) {
                    if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                        throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in terminal state (${session.state})")
                    }
                    if (ctx.state != HandshakeState.NEW) {
                        throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Handshake already started")
                    }
                }

                var keyPairCommitted = false
                val keyPair = cryptoProvider.generateEphemeralKeyPair()
                try {
                    val nonceBytes = cryptoProvider.secureRandomBytes(16)

                    val helloBytes = wireCodec.encodeSessionHello(
                        version = 1,
                        supportedSuites = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                        encounterId = session.encounterId,
                        handshakeId = ctx.handshakeId.value,
                        initiatorNodeId = session.localNodeId,
                        responderNodeId = session.remoteNodeId,
                        nonce = nonceBytes,
                        ephemeralPublicKey = keyPair.publicKey.bytes,
                        capabilities = emptyMap()
                    )

                    synchronized(session) {
                        if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                            throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session $sessionId terminated while generating hello frame")
                        }
                        session.state = SessionState.HANDSHAKING
                        ctx.state = HandshakeState.HELLO_SENT
                        ctx.localEphemeralKeyPair = keyPair
                        ctx.localNonce = SessionNonce(nonceBytes)
                        keyPairCommitted = true
                    }

                    emitEvent(SecureSessionEventType.SESSION_NEGOTIATION_STARTED, session.sessionId)
                    emitEvent(SecureSessionEventType.EPHEMERAL_KEY_GENERATED, session.sessionId)
                    emitEvent(SecureSessionEventType.SESSION_HELLO_SENT, session.sessionId)
                    return helloBytes
                } finally {
                    if (!keyPairCommitted) {
                        keyPair.privateKey.destroy()
                    }
                }
            }

            val decoded = wireCodec.decodeHandshakeFrame(frame)

            when (decoded) {
                is DecodedHandshakeFrame.SessionHello -> {
                    if (session.role != SessionRole.RESPONDER) {
                        throw SecureSessionError.RoleError.RoleConflict("Received SESSION_HELLO but role is not Responder")
                    }
                    synchronized(session) {
                        if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                            throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in terminal state (${session.state})")
                        }
                        if (ctx.state != HandshakeState.NEW && ctx.state != HandshakeState.NEGOTIATING) {
                            throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Unexpected SESSION_HELLO in state ${ctx.state}")
                        }
                    }

                    if (decoded.version != 1) {
                        throw SecureSessionError.NegotiationError.UnsupportedSessionVersion("Version ${decoded.version} not supported")
                    }
                    if (decoded.supportedSuites != CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value) {
                        throw SecureSessionError.NegotiationError.NoCompatibleSecureSessionSuite("Suite ${decoded.supportedSuites} not compatible")
                    }
                    if (decoded.encounterId != session.encounterId) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("EncounterId mismatch")
                    }
                    if (decoded.initiatorNodeId != session.remoteNodeId || decoded.responderNodeId != session.localNodeId) {
                        throw SecureSessionError.HandshakeAuthenticationError.HandshakeNodeMismatch("NodeId mismatch in hello")
                    }

                    val canonicalId = decoded.handshakeId
                    val existing = sessions[canonicalId]
                    if (existing != null && existing !== session) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch(
                            "Canonical session ID conflict: $canonicalId already bound to another active session"
                        )
                    }

                    var keyPairCommitted = false
                    var dhCommitted = false
                    val keyPair = cryptoProvider.generateEphemeralKeyPair()
                    var dh: ByteArray? = null
                    try {
                        val nonceBytes = cryptoProvider.secureRandomBytes(16)
                        val remoteEphemPubKey = EphemeralPublicKey(decoded.ephemeralPublicKey)

                        // Compute DH shared secret outside lock
                        val calculatedDh = cryptoProvider.calculateSharedSecret(keyPair.privateKey, remoteEphemPubKey)
                        dh = calculatedDh
                        if (calculatedDh.all { it == 0.toByte() }) {
                            throw SecureSessionError.EphemeralKeyError.InvalidSharedSecret("Shared secret cannot be all zeroes")
                        }

                        // Compute transcript hash outside lock
                        val canonicalTranscript = buildCanonicalTranscript(
                            version = 1,
                            suite = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                            encounterId = session.encounterId,
                            handshakeId = canonicalId,
                            initiatorNodeId = decoded.initiatorNodeId,
                            responderNodeId = decoded.responderNodeId,
                            initiatorNonce = decoded.nonce,
                            responderNonce = nonceBytes,
                            initiatorEphemPubKey = decoded.ephemeralPublicKey,
                            responderEphemPubKey = keyPair.publicKey.bytes,
                            capabilities = decoded.capabilities
                        )
                        val transcriptHash = cryptoProvider.sha256("OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1".encodeToByteArray() + canonicalTranscript)

                        val responseBytes = wireCodec.encodeSessionHelloResponse(
                            version = 1,
                            selectedSuite = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                            encounterId = session.encounterId,
                            handshakeId = canonicalId,
                            initiatorNodeId = decoded.initiatorNodeId,
                            responderNodeId = decoded.responderNodeId,
                            initiatorNonce = decoded.nonce,
                            responderNonce = nonceBytes,
                            initiatorEphemeralPublicKey = decoded.ephemeralPublicKey,
                            responderEphemeralPublicKey = keyPair.publicKey.bytes
                        )

                        // Atomically register canonical alias under concurrent access, and commit mutations
                        synchronized(session) {
                            if (session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || session.state == SessionState.FAILED || ctx.state == HandshakeState.FAILED) {
                                throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal(
                                    "Session $sessionId terminated while handshake was in progress"
                                )
                            }

                            if (sessionId != canonicalId) {
                                val previousSession = sessions.putIfAbsent(canonicalId, session)
                                if (previousSession != null && previousSession !== session) {
                                    throw SecureSessionError.TranscriptError.TranscriptMismatch(
                                        "Canonical session ID conflict: $canonicalId already bound to another active session"
                                    )
                                }

                                val previousCtx = handshakeContexts.putIfAbsent(canonicalId, ctx)
                                if (previousCtx != null && previousCtx !== ctx) {
                                    sessions.remove(canonicalId, session)
                                    throw SecureSessionError.TranscriptError.TranscriptMismatch(
                                        "Canonical session ID conflict: $canonicalId already bound to another active handshake context"
                                    )
                                }
                            }

                            session.sessionId = SecureSessionId(canonicalId)
                            session.state = SessionState.HANDSHAKING
                            session.transcriptHash = transcriptHash

                            ctx.handshakeId = HandshakeId(canonicalId)
                            ctx.remoteNonce = SessionNonce(decoded.nonce)
                            ctx.remoteEphemeralPublicKey = remoteEphemPubKey
                            ctx.localEphemeralKeyPair = keyPair
                            ctx.localNonce = SessionNonce(nonceBytes)
                            ctx.dhSharedSecret = calculatedDh
                            ctx.transcriptHash = transcriptHash
                            ctx.state = HandshakeState.HELLO_RECEIVED
                            keyPairCommitted = true
                            dhCommitted = true
                        }

                        emitEvent(SecureSessionEventType.SESSION_HELLO_RECEIVED, session.sessionId)
                        emitEvent(SecureSessionEventType.SESSION_NEGOTIATION_STARTED, session.sessionId)
                        emitEvent(SecureSessionEventType.EPHEMERAL_KEY_GENERATED, session.sessionId)
                        emitEvent(SecureSessionEventType.DH_SHARED_SECRET_CREATED, session.sessionId)
                        emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_CREATED, session.sessionId)
                        emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_HASHED, session.sessionId)
                        emitEvent(SecureSessionEventType.SESSION_HELLO_RESPONSE_SENT, session.sessionId)
                        return responseBytes
                    } finally {
                        if (!keyPairCommitted) {
                            keyPair.privateKey.destroy()
                        }
                        if (!dhCommitted) {
                            dh?.fill(0)
                        }
                    }
                }

                is DecodedHandshakeFrame.SessionHelloResponse -> {
                    if (session.role != SessionRole.INITIATOR) {
                        throw SecureSessionError.RoleError.RoleConflict("Received hello response but role is not Initiator")
                    }
                    val localNonce: ByteArray
                    val localEphemeralKeyPair: EphemeralKeyPair
                    synchronized(session) {
                        if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                            throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in terminal state (${session.state})")
                        }
                        if (ctx.state != HandshakeState.HELLO_SENT) {
                            throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Unexpected hello response in state ${ctx.state}")
                        }
                        localNonce = ctx.localNonce?.bytes
                            ?: throw SecureSessionError.TranscriptError.TranscriptMismatch("Local nonce missing")
                        localEphemeralKeyPair = ctx.localEphemeralKeyPair
                            ?: throw SecureSessionError.TranscriptError.TranscriptMismatch("Local ephemeral key missing")
                    }

                    if (decoded.version != 1) {
                        throw SecureSessionError.NegotiationError.UnsupportedSessionVersion("Version ${decoded.version} not supported")
                    }
                    if (decoded.selectedSuite != CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value) {
                        throw SecureSessionError.NegotiationError.NoCompatibleSecureSessionSuite("Suite ${decoded.selectedSuite} not compatible")
                    }
                    if (decoded.encounterId != session.encounterId) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("EncounterId mismatch")
                    }
                    if (decoded.initiatorNodeId != session.localNodeId || decoded.responderNodeId != session.remoteNodeId) {
                        throw SecureSessionError.HandshakeAuthenticationError.HandshakeNodeMismatch("NodeId mismatch in response")
                    }
                    if (!decoded.initiatorNonce.contentEquals(localNonce)) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("Initiator nonce mismatch")
                    }
                    if (!decoded.initiatorEphemeralPublicKey.contentEquals(localEphemeralKeyPair.publicKey.bytes)) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("Initiator ephemeral public key mismatch")
                    }

                    val remoteNonce = SessionNonce(decoded.responderNonce)
                    val remoteEphemPubKey = EphemeralPublicKey(decoded.responderEphemeralPublicKey)

                    var dhCommitted = false
                    var dh: ByteArray? = null
                    try {
                        // Compute DH shared secret outside lock
                        val calculatedDh = cryptoProvider.calculateSharedSecret(localEphemeralKeyPair.privateKey, remoteEphemPubKey)
                        dh = calculatedDh
                        if (calculatedDh.all { it == 0.toByte() }) {
                            throw SecureSessionError.EphemeralKeyError.InvalidSharedSecret("Shared secret cannot be all zeroes")
                        }

                        // Compute transcript hash outside lock
                        val canonicalTranscript = buildCanonicalTranscript(
                            version = 1,
                            suite = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                            encounterId = session.encounterId,
                            handshakeId = decoded.handshakeId,
                            initiatorNodeId = decoded.initiatorNodeId,
                            responderNodeId = decoded.responderNodeId,
                            initiatorNonce = localNonce,
                            responderNonce = decoded.responderNonce,
                            initiatorEphemPubKey = localEphemeralKeyPair.publicKey.bytes,
                            responderEphemPubKey = decoded.responderEphemeralPublicKey,
                            capabilities = emptyMap()
                        )
                        val transcriptHash = cryptoProvider.sha256("OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1".encodeToByteArray() + canonicalTranscript)

                        // Sign transcript outside lock
                        val authPayload = "OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1".encodeToByteArray() +
                                SessionRole.INITIATOR.name.encodeToByteArray() +
                                session.localNodeId.encodeToByteArray() +
                                session.remoteNodeId.encodeToByteArray() +
                                transcriptHash

                        val sigResult = identityCore.sign(spike.identity.domain.DomainTag.SECURE_SESSION_AUTH_V1, authPayload)
                        if (sigResult.disposition != IdentityCommandDisposition.APPLIED) {
                            throw SecureSessionError.TranscriptError.HandshakeBindingFailure("Failed to sign transcript")
                        }

                        val signatureBytes = sigResult.value!!.bytes

                        val authBytes = wireCodec.encodeHandshakeFrame(
                            wireCodec.encodeHandshakeAuth(
                                handshakeId = ctx.handshakeId.value,
                                signerNodeId = session.localNodeId,
                                signerRoleOrdinal = SessionRole.INITIATOR.ordinal,
                                transcriptHash = transcriptHash,
                                signature = signatureBytes
                            )
                        )

                        synchronized(session) {
                            if (session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || session.state == SessionState.FAILED || ctx.state == HandshakeState.FAILED) {
                                throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal(
                                    "Session $sessionId terminated while processing hello response"
                                )
                            }

                            session.state = SessionState.AUTHENTICATING
                            session.transcriptHash = transcriptHash

                            ctx.remoteNonce = remoteNonce
                            ctx.remoteEphemeralPublicKey = remoteEphemPubKey
                            ctx.dhSharedSecret = calculatedDh
                            ctx.transcriptHash = transcriptHash
                            ctx.localAuthSignature = signatureBytes
                            ctx.state = HandshakeState.LOCAL_AUTH_SENT
                            dhCommitted = true
                        }

                        emitEvent(SecureSessionEventType.SESSION_HELLO_RESPONSE_RECEIVED, session.sessionId)
                        emitEvent(SecureSessionEventType.DH_SHARED_SECRET_CREATED, session.sessionId)
                        emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_CREATED, session.sessionId)
                        emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_HASHED, session.sessionId)
                        emitEvent(SecureSessionEventType.LOCAL_HANDSHAKE_AUTH_CREATED, session.sessionId)
                        return authBytes
                    } finally {
                        if (!dhCommitted) {
                            dh?.fill(0)
                        }
                    }
                }

                is DecodedHandshakeFrame.HandshakeAuth -> {
                    val transcriptHashSnapshot: ByteArray
                    val dhSharedSecretSnapshot: ByteArray
                    synchronized(session) {
                        if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                            throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in terminal state (${session.state})")
                        }
                        if (ctx.transcriptHash == null) {
                            throw SecureSessionError.TranscriptError.TranscriptMismatch("Transcript hash not set")
                        }
                        if (!decoded.transcriptHash.contentEquals(ctx.transcriptHash!!)) {
                            throw SecureSessionError.TranscriptError.TranscriptHashMismatch("Transcript hash mismatch")
                        }
                        if (decoded.handshakeId != ctx.handshakeId.value) {
                            throw SecureSessionError.TranscriptError.TranscriptMismatch("HandshakeId mismatch")
                        }
                        if (ctx.dhSharedSecret == null) {
                            throw SecureSessionError.TranscriptError.TranscriptMismatch("DH shared secret not set")
                        }
                        transcriptHashSnapshot = ctx.transcriptHash!!.copyOf()
                        dhSharedSecretSnapshot = ctx.dhSharedSecret!!.copyOf()
                    }

                    var keyMaterialCommitted = false
                    var keyMaterial: SessionKeyMaterial? = null
                    var earlySecret: ByteArray? = null
                    var sessionMasterSecret: ByteArray? = null
                    var i2rKey: ByteArray? = null
                    var r2iKey: ByteArray? = null
                    var i2rNonce: ByteArray? = null
                    var r2iNonce: ByteArray? = null
                    var i2rRekey: ByteArray? = null
                    var r2iRekey: ByteArray? = null
                    var confirmationKey: ByteArray? = null
                    var exporterSecret: ByteArray? = null

                    try {
                        val expectedRole = if (session.role == SessionRole.INITIATOR) SessionRole.RESPONDER else SessionRole.INITIATOR
                        if (decoded.signerRoleOrdinal != expectedRole.ordinal) {
                            throw SecureSessionError.HandshakeAuthenticationError.HandshakeRoleMismatch("Unexpected role ordinal")
                        }
                        if (decoded.signerNodeId != session.remoteNodeId) {
                            throw SecureSessionError.HandshakeAuthenticationError.HandshakeNodeMismatch("Unexpected signer NodeId")
                        }

                        // Verify signature outside lock
                        val authPayload = "OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1".encodeToByteArray() +
                                expectedRole.name.encodeToByteArray() +
                                session.remoteNodeId.encodeToByteArray() +
                                session.localNodeId.encodeToByteArray() +
                                transcriptHashSnapshot

                        val remoteIdentity = identityCore.getRemoteIdentity(NodeId(session.remoteNodeId))
                            ?: throw SecureSessionError.HandshakeAuthenticationError.InvalidHandshakeIdentity("Remote identity document not found")

                        val verifyResult = identityCore.verify(
                            remoteIdentity.identityDocument,
                            spike.identity.domain.DomainTag.SECURE_SESSION_AUTH_V1,
                            authPayload,
                            SignatureBytes(SignatureAlgorithm.ED25519, decoded.signature)
                        )

                        if (verifyResult != VerificationResult.VALID) {
                            emitEvent(SecureSessionEventType.REMOTE_HANDSHAKE_AUTH_REJECTED, session.sessionId)
                            throw SecureSessionError.HandshakeAuthenticationError.InvalidHandshakeSignature("Remote signature verification failed")
                        }

                        // Key Derivation outside lock
                        earlySecret = cryptoProvider.hkdfExtract(transcriptHashSnapshot, dhSharedSecretSnapshot)
                        val initNodeId = if (session.role == SessionRole.INITIATOR) session.localNodeId else session.remoteNodeId
                        val respNodeId = if (session.role == SessionRole.INITIATOR) session.remoteNodeId else session.localNodeId
                        val masterInfo = "OFFLINE_PROTOCOL_SESSION_MASTER_V1".encodeToByteArray() +
                                transcriptHashSnapshot +
                                initNodeId.encodeToByteArray() +
                                respNodeId.encodeToByteArray()
                        sessionMasterSecret = cryptoProvider.hkdfExpand(earlySecret, masterInfo, 32)
                        earlySecret.fill(0)
                        earlySecret = null

                        i2rKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "initiator-to-responder-traffic-key".encodeToByteArray() + transcriptHashSnapshot, 32)
                        r2iKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "responder-to-initiator-traffic-key".encodeToByteArray() + transcriptHashSnapshot, 32)
                        i2rNonce = cryptoProvider.hkdfExpand(sessionMasterSecret, "initiator-nonce-base".encodeToByteArray() + transcriptHashSnapshot, 12)
                        r2iNonce = cryptoProvider.hkdfExpand(sessionMasterSecret, "responder-nonce-base".encodeToByteArray() + transcriptHashSnapshot, 12)
                        i2rRekey = cryptoProvider.hkdfExpand(sessionMasterSecret, "initiator-rekey-secret".encodeToByteArray() + transcriptHashSnapshot, 32)
                        r2iRekey = cryptoProvider.hkdfExpand(sessionMasterSecret, "responder-rekey-secret".encodeToByteArray() + transcriptHashSnapshot, 32)
                        confirmationKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "session-confirmation-key".encodeToByteArray() + transcriptHashSnapshot, 32)
                        exporterSecret = cryptoProvider.hkdfExpand(sessionMasterSecret, "session-exporter-secret".encodeToByteArray() + transcriptHashSnapshot, 32)
                        sessionMasterSecret.fill(0)
                        sessionMasterSecret = null

                        val derivedKeyMaterial = if (session.role == SessionRole.INITIATOR) {
                            SessionKeyMaterial(
                                sendKey = i2rKey,
                                receiveKey = r2iKey,
                                sendNonceBase = i2rNonce,
                                receiveNonceBase = r2iNonce,
                                sendRekeySecret = i2rRekey,
                                receiveRekeySecret = r2iRekey,
                                exporterSecret = exporterSecret
                            )
                        } else {
                            SessionKeyMaterial(
                                sendKey = r2iKey,
                                receiveKey = i2rKey,
                                sendNonceBase = r2iNonce,
                                receiveNonceBase = i2rNonce,
                                sendRekeySecret = r2iRekey,
                                receiveRekeySecret = i2rRekey,
                                exporterSecret = exporterSecret
                            )
                        }
                        keyMaterial = derivedKeyMaterial

                        val isResponderNeedingSign = (session.role == SessionRole.RESPONDER && ctx.localAuthSignature == null)
                        val responseAuthBytes: ByteArray?
                        val signatureBytes: ByteArray?
                        val confirmationBytes: ByteArray?

                        if (isResponderNeedingSign) {
                            val respAuthPayload = "OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1".encodeToByteArray() +
                                    SessionRole.RESPONDER.name.encodeToByteArray() +
                                    session.localNodeId.encodeToByteArray() +
                                    session.remoteNodeId.encodeToByteArray() +
                                    transcriptHashSnapshot

                            val sigResult = identityCore.sign(spike.identity.domain.DomainTag.SECURE_SESSION_AUTH_V1, respAuthPayload)
                            signatureBytes = sigResult.value!!.bytes

                            responseAuthBytes = wireCodec.encodeHandshakeFrame(
                                wireCodec.encodeHandshakeAuth(
                                    handshakeId = ctx.handshakeId.value,
                                    signerNodeId = session.localNodeId,
                                    signerRoleOrdinal = SessionRole.RESPONDER.ordinal,
                                    transcriptHash = transcriptHashSnapshot,
                                    signature = signatureBytes
                                )
                            )
                            confirmationBytes = null
                            confirmationKey.fill(0)
                            confirmationKey = null
                        } else if (session.role == SessionRole.INITIATOR) {
                            val confTagPayload = "OFFLINE_PROTOCOL_KEY_CONFIRMATION_V1".encodeToByteArray() +
                                    transcriptHashSnapshot +
                                    session.role!!.name.encodeToByteArray()
                            val localConfTag = cryptoProvider.hmacSha256(confirmationKey, confTagPayload)
                            confirmationKey.fill(0)
                            confirmationKey = null

                            confirmationBytes = wireCodec.encodeHandshakeFrame(
                                wireCodec.encodeKeyConfirmation(
                                    handshakeId = ctx.handshakeId.value,
                                    signerRoleOrdinal = session.role!!.ordinal,
                                    confirmationTag = localConfTag
                                )
                            )
                            responseAuthBytes = null
                            signatureBytes = null
                        } else {
                            confirmationKey.fill(0)
                            confirmationKey = null
                            responseAuthBytes = null
                            signatureBytes = null
                            confirmationBytes = null
                        }

                        synchronized(session) {
                            if (session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || session.state == SessionState.FAILED || ctx.state == HandshakeState.FAILED) {
                                throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal(
                                    "Session $sessionId terminated while deriving keys"
                                )
                            }

                            ctx.remoteAuthSignature = decoded.signature
                            session.keyMaterial?.destroy()
                            session.keyMaterial = derivedKeyMaterial
                            session.state = SessionState.CONFIRMING_KEYS
                            keyMaterialCommitted = true

                            if (isResponderNeedingSign) {
                                ctx.localAuthSignature = signatureBytes
                                ctx.state = HandshakeState.LOCAL_AUTH_SENT
                            } else if (session.role == SessionRole.INITIATOR) {
                                ctx.state = HandshakeState.KEY_CONFIRMING
                            } else {
                                ctx.state = HandshakeState.KEYS_DERIVED
                            }
                        }

                        emitEvent(SecureSessionEventType.REMOTE_HANDSHAKE_AUTH_RECEIVED, session.sessionId)
                        emitEvent(SecureSessionEventType.REMOTE_HANDSHAKE_AUTH_VERIFIED, session.sessionId)
                        emitEvent(SecureSessionEventType.SESSION_KEYS_DERIVED, session.sessionId)

                        if (isResponderNeedingSign) {
                            emitEvent(SecureSessionEventType.LOCAL_HANDSHAKE_AUTH_CREATED, session.sessionId)
                            return responseAuthBytes
                        } else if (session.role == SessionRole.INITIATOR) {
                            emitEvent(SecureSessionEventType.KEY_CONFIRMATION_SENT, session.sessionId)
                            return confirmationBytes
                        }
                        return null
                    } finally {
                        dhSharedSecretSnapshot.fill(0)
                        transcriptHashSnapshot.fill(0)
                        earlySecret?.fill(0)
                        sessionMasterSecret?.fill(0)
                        confirmationKey?.fill(0)
                        if (!keyMaterialCommitted) {
                            keyMaterial?.destroy()
                            if (keyMaterial == null) {
                                i2rKey?.fill(0)
                                r2iKey?.fill(0)
                                i2rNonce?.fill(0)
                                r2iNonce?.fill(0)
                                i2rRekey?.fill(0)
                                r2iRekey?.fill(0)
                                exporterSecret?.fill(0)
                            }
                        }
                    }
                }

                is DecodedHandshakeFrame.KeyConfirmation -> {
                    val transcriptHashSnapshot: ByteArray
                    val dhSharedSecretSnapshot: ByteArray
                    synchronized(session) {
                        if (session.state == SessionState.FAILED || session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || ctx.state == HandshakeState.FAILED) {
                            throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in terminal state (${session.state})")
                        }
                        if (session.state != SessionState.CONFIRMING_KEYS && ctx.state != HandshakeState.KEYS_DERIVED && ctx.state != HandshakeState.LOCAL_AUTH_SENT && ctx.state != HandshakeState.KEY_CONFIRMING) {
                            throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Unexpected confirmation in state ${session.state}")
                        }
                        val th = ctx.transcriptHash
                            ?: throw SecureSessionError.TranscriptError.TranscriptMismatch("Transcript hash not set")
                        val dh = ctx.dhSharedSecret
                            ?: throw SecureSessionError.TranscriptError.TranscriptMismatch("DH shared secret not set")
                        transcriptHashSnapshot = th.copyOf()
                        dhSharedSecretSnapshot = dh.copyOf()
                    }

                    var earlySecret: ByteArray? = null
                    var sessionMasterSecret: ByteArray? = null
                    var confirmationKey: ByteArray? = null

                    try {
                        val expectedRole = if (session.role == SessionRole.INITIATOR) SessionRole.RESPONDER else SessionRole.INITIATOR
                        if (decoded.signerRoleOrdinal != expectedRole.ordinal) {
                            throw SecureSessionError.HandshakeAuthenticationError.HandshakeRoleMismatch("Unexpected confirmation role ordinal")
                        }

                        // Verify confirmation tag outside lock
                        val masterInfo = "OFFLINE_PROTOCOL_SESSION_MASTER_V1".encodeToByteArray() +
                                transcriptHashSnapshot +
                                (if (session.role == SessionRole.INITIATOR) session.localNodeId else session.remoteNodeId).encodeToByteArray() +
                                (if (session.role == SessionRole.INITIATOR) session.remoteNodeId else session.localNodeId).encodeToByteArray()
                        earlySecret = cryptoProvider.hkdfExtract(transcriptHashSnapshot, dhSharedSecretSnapshot)
                        sessionMasterSecret = cryptoProvider.hkdfExpand(earlySecret, masterInfo, 32)
                        earlySecret.fill(0)
                        earlySecret = null

                        confirmationKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "session-confirmation-key".encodeToByteArray() + transcriptHashSnapshot, 32)
                        sessionMasterSecret.fill(0)
                        sessionMasterSecret = null

                        val expectedConfTagPayload = "OFFLINE_PROTOCOL_KEY_CONFIRMATION_V1".encodeToByteArray() +
                                transcriptHashSnapshot +
                                expectedRole.name.encodeToByteArray()
                        val expectedConfTag = cryptoProvider.hmacSha256(confirmationKey, expectedConfTagPayload)

                        if (!decoded.confirmationTag.contentEquals(expectedConfTag)) {
                            emitEvent(SecureSessionEventType.KEY_CONFIRMATION_VERIFIED, session.sessionId, mapOf("status" to "failed"))
                            throw SecureSessionError.KeyDerivationError.KeyConfirmationFailure("Confirmation tag verification failed")
                        }

                        val responseConfBytes: ByteArray? = if (session.role == SessionRole.RESPONDER) {
                            val confTagPayload = "OFFLINE_PROTOCOL_KEY_CONFIRMATION_V1".encodeToByteArray() +
                                    transcriptHashSnapshot +
                                    session.role!!.name.encodeToByteArray()
                            val localConfTag = cryptoProvider.hmacSha256(confirmationKey, confTagPayload)

                            wireCodec.encodeHandshakeFrame(
                                wireCodec.encodeKeyConfirmation(
                                    handshakeId = ctx.handshakeId.value,
                                    signerRoleOrdinal = session.role!!.ordinal,
                                    confirmationTag = localConfTag
                                )
                            )
                        } else null

                        confirmationKey.fill(0)
                        confirmationKey = null

                        synchronized(session) {
                            if (session.state == SessionState.CLOSED || session.state == SessionState.CLOSING || session.state == SessionState.FAILED || ctx.state == HandshakeState.FAILED) {
                                throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal(
                                    "Session $sessionId terminated before establishment"
                                )
                            }
                            session.state = SessionState.ESTABLISHED
                            session.establishedAt = System.currentTimeMillis()
                            ctx.state = HandshakeState.ESTABLISHED

                            val targetCtx = session.handshakeContext ?: ctx
                            targetCtx.destroy()
                            handshakeContexts.entries.removeIf { it.value === targetCtx }
                            session.handshakeContext = null
                        }

                        emitEvent(SecureSessionEventType.KEY_CONFIRMATION_RECEIVED, session.sessionId)
                        emitEvent(SecureSessionEventType.KEY_CONFIRMATION_VERIFIED, session.sessionId, mapOf("status" to "success"))
                        emitEvent(SecureSessionEventType.SESSION_ESTABLISHED, session.sessionId)

                        return responseConfBytes
                    } finally {
                        dhSharedSecretSnapshot.fill(0)
                        transcriptHashSnapshot.fill(0)
                        earlySecret?.fill(0)
                        sessionMasterSecret?.fill(0)
                        confirmationKey?.fill(0)
                    }
                }
            }
        } catch (e: Exception) {
            val eventSessionId: SecureSessionId
            var shouldEmitFailed = false
            synchronized(session) {
                ctx.state = HandshakeState.FAILED
                val targetCtx = session.handshakeContext ?: ctx
                targetCtx.destroy()
                handshakeContexts.entries.removeIf { it.value === targetCtx }
                session.handshakeContext = null

                if (session.state == SessionState.CLOSED || session.state == SessionState.FAILED || session.state == SessionState.CLOSING) {
                    if (session.state != SessionState.CLOSING) {
                        sessions.entries.removeIf { it.value === session }
                    }
                } else {
                    session.state = SessionState.FAILED
                    session.failureReason = e.message
                    session.sessionId = SecureSessionId(sessionId)
                    sessions.entries.removeIf { it.value === session && it.key != sessionId }
                    shouldEmitFailed = true
                }
                eventSessionId = session.sessionId
            }
            if (shouldEmitFailed) {
                emitEvent(SecureSessionEventType.SESSION_FAILED, eventSessionId, mapOf("error" to (e.message ?: "Unknown error")))
            }
            throw e
        }
    }

    override suspend fun getSession(sessionId: String): SecureSessionSnapshot? {
        val session = sessions[sessionId] ?: return null
        return synchronized(session) { session.toSnapshot() }
    }

    override suspend fun getSessionSnapshot(sessionId: String): SecureSessionSnapshot? {
        val session = sessions[sessionId] ?: return null
        return synchronized(session) { session.toSnapshot() }
    }

    override suspend fun encrypt(sessionId: String, recordType: SecureRecordType, plaintext: ByteArray): ByteArray {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        val record: SecureRecord
        val eventSessionId: SecureSessionId
        synchronized(session) {
            if (session.state != SessionState.ESTABLISHED && session.state != SessionState.REKEYING) {
                throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("Session not established (state=${session.state})")
            }

            val keyMaterial = session.keyMaterial
                ?: throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("No active key material")

            val seqVal = session.sendSequence.value
            val nonce = constructNonce(keyMaterial.sendNonceBase, seqVal)
            val keyEpochVal = session.currentEpoch.value

            val aad = buildRecordAAD(
                recordVersion = 1,
                keyEpochVal = keyEpochVal,
                sequenceNumber = seqVal,
                recordTypeOrdinal = recordType.ordinal,
                sessionIdVal = session.sessionId.value,
                ciphertextLength = plaintext.size
            )

            val combined = cryptoProvider.aeadEncrypt(keyMaterial.sendKey, nonce, plaintext, aad)
            val ciphertext = combined.copyOfRange(0, combined.size - 16)
            val tag = combined.copyOfRange(combined.size - 16, combined.size)

            record = SecureRecord(
                recordVersion = 1,
                sessionId = session.sessionId,
                keyEpoch = session.currentEpoch,
                sequenceNumber = seqVal,
                recordType = recordType,
                ciphertextLength = ciphertext.size,
                ciphertext = ciphertext,
                authenticationTag = tag
            )

            session.sendSequence = session.sendSequence.next()
            session.recordsSent++
            session.bytesSent += combined.size
            session.lastActivityAt = System.currentTimeMillis()
            eventSessionId = session.sessionId
        }

        emitEvent(SecureSessionEventType.SECURE_RECORD_ENCRYPTED, eventSessionId)

        return wireCodec.encodeSecureRecord(record)
    }

    override suspend fun decrypt(sessionId: String, secureRecordBytes: ByteArray): ByteArray {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        synchronized(session) {
            if (session.state != SessionState.ESTABLISHED && session.state != SessionState.REKEYING) {
                throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("Session not established (state=${session.state})")
            }
        }

        val record = try {
            wireCodec.decodeSecureRecord(secureRecordBytes)
        } catch (e: Exception) {
            emitEvent(SecureSessionEventType.SECURE_RECORD_REJECTED, session.sessionId, mapOf("error" to "Malformed secure record"))
            throw SecureSessionError.SecureRecordError.MalformedSecureRecord("Malformed secure record", e)
        }

        var errorToThrow: Throwable? = null
        var rejectEventType: SecureSessionEventType? = null
        var rejectMetadata: Map<String, String> = emptyMap()
        var plaintextResult: ByteArray? = null
        val eventSessionId: SecureSessionId

        synchronized(session) {
            eventSessionId = session.sessionId

            if (session.state != SessionState.ESTABLISHED && session.state != SessionState.REKEYING) {
                throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("Session not established (state=${session.state})")
            }

            if (record.sessionId.value != session.sessionId.value) {
                throw SecureSessionError.SecureRecordError.SessionIdMismatch("Session ID mismatch")
            }

            if (record.keyEpoch.value != session.currentEpoch.value) {
                throw SecureSessionError.SecureRecordError.InvalidKeyEpoch("Key epoch mismatch: expected ${session.currentEpoch.value}, got ${record.keyEpoch.value}")
            }

            val expectedSeqVal = session.expectedReceiveSequence.value
            if (record.sequenceNumber < expectedSeqVal) {
                rejectEventType = SecureSessionEventType.REPLAY_REJECTED
                errorToThrow = SecureSessionError.SecureRecordError.ReplayDetected("Replayed sequence number ${record.sequenceNumber} (expected $expectedSeqVal)")
            } else if (record.sequenceNumber > expectedSeqVal) {
                rejectEventType = SecureSessionEventType.SEQUENCE_VIOLATION
                errorToThrow = SecureSessionError.SecureRecordError.OutOfOrderRecord("Out-of-order sequence number ${record.sequenceNumber} (expected $expectedSeqVal)")
            } else {
                val keyMaterial = session.keyMaterial
                    ?: throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("No active key material")

                val nonce = constructNonce(keyMaterial.receiveNonceBase, expectedSeqVal)
                val aad = buildRecordAAD(
                    recordVersion = record.recordVersion,
                    keyEpochVal = record.keyEpoch.value,
                    sequenceNumber = record.sequenceNumber,
                    recordTypeOrdinal = record.recordType.ordinal,
                    sessionIdVal = record.sessionId.value,
                    ciphertextLength = record.ciphertextLength
                )

                val combinedInput = record.ciphertext + record.authenticationTag
                try {
                    plaintextResult = cryptoProvider.aeadDecrypt(keyMaterial.receiveKey, nonce, combinedInput, aad)
                    session.expectedReceiveSequence = session.expectedReceiveSequence.next()
                    session.recordsReceived++
                    session.bytesReceived += secureRecordBytes.size
                    session.lastActivityAt = System.currentTimeMillis()
                } catch (e: Exception) {
                    rejectEventType = SecureSessionEventType.SECURE_RECORD_REJECTED
                    rejectMetadata = mapOf("error" to "AEAD decryption failed")
                    errorToThrow = SecureSessionError.SecureRecordError.AeadAuthenticationFailure("Decryption authentication failed", e)
                }
            }
        }

        val eventType = rejectEventType
        if (eventType != null) {
            emitEvent(eventType, eventSessionId, rejectMetadata)
            throw errorToThrow!!
        }

        emitEvent(SecureSessionEventType.SECURE_RECORD_DECRYPTED, eventSessionId)

        return plaintextResult!!
    }

    override suspend fun requestRekey(sessionId: String) {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        val sendRekeySecretCopy: ByteArray
        val receiveRekeySecretCopy: ByteArray
        val exporterSecretCopy: ByteArray
        val transcriptHashCopy: ByteArray
        val nextEpoch: KeyEpoch
        val eventSessionId: SecureSessionId

        synchronized(session) {
            if (session.state != SessionState.ESTABLISHED) {
                throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("Session not established")
            }
            val km = session.keyMaterial
                ?: throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("No active key material")
            val th = session.transcriptHash
                ?: throw SecureSessionError.TranscriptError.TranscriptMismatch("Transcript hash missing")

            session.state = SessionState.REKEYING
            sendRekeySecretCopy = km.sendRekeySecret.copyOf()
            receiveRekeySecretCopy = km.receiveRekeySecret.copyOf()
            exporterSecretCopy = km.exporterSecret.copyOf()
            transcriptHashCopy = th.copyOf()
            nextEpoch = session.currentEpoch.next()
            eventSessionId = session.sessionId
        }

        emitEvent(SecureSessionEventType.REKEY_STARTED, eventSessionId)

        var newKeyMaterialCommitted = false
        var newKeyMaterial: SessionKeyMaterial? = null
        var nextSendRekeySecret: ByteArray? = null
        var nextReceiveRekeySecret: ByteArray? = null
        var nextSendKey: ByteArray? = null
        var nextReceiveKey: ByteArray? = null
        var nextSendNonceBase: ByteArray? = null
        var nextReceiveNonceBase: ByteArray? = null

        try {
            // Derive new keys outside lock
            val info = "OFFLINE_PROTOCOL_REKEY_SECRET_V1".encodeToByteArray() +
                    transcriptHashCopy +
                    pad32(nextEpoch.value)

            nextSendRekeySecret = cryptoProvider.hkdfExpand(sendRekeySecretCopy, info, 32)
            nextReceiveRekeySecret = cryptoProvider.hkdfExpand(receiveRekeySecretCopy, info, 32)

            nextSendKey = cryptoProvider.hkdfExpand(nextSendRekeySecret, "send-traffic-key".encodeToByteArray() + transcriptHashCopy, 32)
            nextReceiveKey = cryptoProvider.hkdfExpand(nextReceiveRekeySecret, "send-traffic-key".encodeToByteArray() + transcriptHashCopy, 32)
            nextSendNonceBase = cryptoProvider.hkdfExpand(nextSendRekeySecret, "send-nonce-base".encodeToByteArray() + transcriptHashCopy, 12)
            nextReceiveNonceBase = cryptoProvider.hkdfExpand(nextReceiveRekeySecret, "send-nonce-base".encodeToByteArray() + transcriptHashCopy, 12)

            val derivedKeyMaterial = SessionKeyMaterial(
                sendKey = nextSendKey,
                receiveKey = nextReceiveKey,
                sendNonceBase = nextSendNonceBase,
                receiveNonceBase = nextReceiveNonceBase,
                sendRekeySecret = nextSendRekeySecret,
                receiveRekeySecret = nextReceiveRekeySecret,
                exporterSecret = exporterSecretCopy.copyOf()
            )
            newKeyMaterial = derivedKeyMaterial

            synchronized(session) {
                if (session.state != SessionState.REKEYING) {
                    throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal(
                        "Session $sessionId terminated or state invalidated during rekey (state is ${session.state})"
                    )
                }

                // Rotate epoch and commit
                session.keyMaterial?.destroy()
                session.keyMaterial = derivedKeyMaterial
                session.currentEpoch = nextEpoch
                session.sendSequence = SendSequence(0L)
                session.expectedReceiveSequence = ReceiveSequence(0L)
                session.state = SessionState.ESTABLISHED
                newKeyMaterialCommitted = true
            }

            emitEvent(SecureSessionEventType.REKEY_COMPLETED, eventSessionId)
        } catch (e: Exception) {
            synchronized(session) {
                if (session.state == SessionState.REKEYING) {
                    session.state = SessionState.FAILED
                    session.failureReason = e.message
                }
            }
            throw e
        } finally {
            sendRekeySecretCopy.fill(0)
            receiveRekeySecretCopy.fill(0)
            exporterSecretCopy.fill(0)
            transcriptHashCopy.fill(0)
            if (!newKeyMaterialCommitted) {
                newKeyMaterial?.destroy()
                if (newKeyMaterial == null) {
                    nextSendRekeySecret?.fill(0)
                    nextReceiveRekeySecret?.fill(0)
                    nextSendKey?.fill(0)
                    nextReceiveKey?.fill(0)
                    nextSendNonceBase?.fill(0)
                    nextReceiveNonceBase?.fill(0)
                }
            }
        }
    }

    override suspend fun closeSession(sessionId: String, reason: String) {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        val eventSessionId: SecureSessionId
        synchronized(session) {
            if (session.state == SessionState.CLOSED || session.state == SessionState.CLOSING) {
                return
            }
            session.state = SessionState.CLOSING
            eventSessionId = session.sessionId
        }

        emitEvent(SecureSessionEventType.SESSION_CLOSE_STARTED, eventSessionId)

        var emitClosed = false
        synchronized(session) {
            if (session.state != SessionState.FAILED) {
                session.destroy()
                session.state = SessionState.CLOSED
                session.closedAt = System.currentTimeMillis()
                emitClosed = true
            }
            sessions.entries.removeIf { it.value === session }

            val targetCtx = session.handshakeContext
                ?: handshakeContexts[sessionId]
                ?: handshakeContexts[session.sessionId.value]
            if (targetCtx != null) {
                targetCtx.destroy()
                handshakeContexts.entries.removeIf { it.value === targetCtx }
                session.handshakeContext = null
            }
        }

        if (emitClosed) {
            emitEvent(SecureSessionEventType.SESSION_CLOSED, eventSessionId)
        }
    }

    override suspend fun handleTransportLoss(sessionId: String, reason: String) {
        val session = sessions[sessionId] ?: return
        val eventSessionId: SecureSessionId
        val failureMsg = "Transport lost: $reason"
        synchronized(session) {
            if (session.state == SessionState.CLOSED || session.state == SessionState.FAILED || session.state == SessionState.CLOSING) {
                if (session.state != SessionState.CLOSING) {
                    sessions.entries.removeIf { it.value === session }
                }
                val targetCtx = session.handshakeContext
                    ?: handshakeContexts[sessionId]
                    ?: handshakeContexts[session.sessionId.value]
                if (targetCtx != null) {
                    targetCtx.destroy()
                    handshakeContexts.entries.removeIf { it.value === targetCtx }
                    session.handshakeContext = null
                }
                return
            }
            session.state = SessionState.FAILED
            session.failureReason = failureMsg
            session.destroy()
            sessions.entries.removeIf { it.value === session }

            val targetCtx = session.handshakeContext
                ?: handshakeContexts[sessionId]
                ?: handshakeContexts[session.sessionId.value]
            if (targetCtx != null) {
                targetCtx.destroy()
                handshakeContexts.entries.removeIf { it.value === targetCtx }
                session.handshakeContext = null
            }

            eventSessionId = session.sessionId
        }

        emitEvent(SecureSessionEventType.SESSION_FAILED, eventSessionId, mapOf("error" to failureMsg))
    }

    override suspend fun recover() {
        // Clear all active volatile sessions on startup recovery
        sessions.forEach { (_, session) ->
            session.destroy()
        }
        sessions.clear()
        handshakeContexts.clear()
    }

    override fun subscribeEvents(listener: SecureSessionEventListener) {
        listeners.add(listener)
    }

    fun hasHandshakeContext(sessionId: String): Boolean = handshakeContexts.containsKey(sessionId)
    fun getHandshakeContext(sessionId: String): HandshakeContext? = handshakeContexts[sessionId]
    val activeHandshakeContextCount: Int get() = handshakeContexts.size

    private fun determineRole(local: String, remote: String): SessionRole {
        val comp = local.compareTo(remote)
        if (comp == 0) throw SecureSessionError.RoleError.InvalidSessionRole("Cannot establish session with self")
        return if (comp < 0) SessionRole.INITIATOR else SessionRole.RESPONDER
    }

    private fun buildCanonicalTranscript(
        version: Int,
        suite: String,
        encounterId: String,
        handshakeId: String,
        initiatorNodeId: String,
        responderNodeId: String,
        initiatorNonce: ByteArray,
        responderNonce: ByteArray,
        initiatorEphemPubKey: ByteArray,
        responderEphemPubKey: ByteArray,
        capabilities: Map<String, String>
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.write("OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1".encodeToByteArray())
        dos.writeShort(version)
        dos.writeUTF(suite)
        dos.writeUTF(encounterId)
        dos.writeUTF(handshakeId)
        dos.writeUTF(initiatorNodeId)
        dos.writeUTF(responderNodeId)
        dos.write(initiatorNonce)
        dos.write(responderNonce)
        dos.write(initiatorEphemPubKey)
        dos.write(responderEphemPubKey)
        dos.writeUTF(SecureSessionWireCodec.mapToString(capabilities))
        dos.flush()
        return baos.toByteArray()
    }

    private fun constructNonce(nonceBase: ByteArray, seq: Long): ByteArray {
        val nonce = nonceBase.copyOf()
        val seqBytes = ByteArray(8)
        for (i in 0..7) {
            seqBytes[i] = (seq shr (56 - i * 8)).toByte()
        }
        // XOR the last 8 bytes of nonce base with sequence number
        for (i in 0..7) {
            nonce[4 + i] = (nonce[4 + i].toInt() xor seqBytes[i].toInt()).toByte()
        }
        return nonce
    }

    private fun buildRecordAAD(
        recordVersion: Int,
        keyEpochVal: Int,
        sequenceNumber: Long,
        recordTypeOrdinal: Int,
        sessionIdVal: String,
        ciphertextLength: Int
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(recordVersion)
        dos.writeInt(keyEpochVal)
        dos.writeLong(sequenceNumber)
        dos.writeByte(recordTypeOrdinal)
        val sessionBytes = sessionIdVal.encodeToByteArray()
        dos.writeShort(sessionBytes.size)
        dos.write(sessionBytes)
        dos.writeInt(ciphertextLength)
        dos.flush()
        return baos.toByteArray()
    }

    private fun pad32(value: Int): ByteArray {
        val bytes = ByteArray(32)
        bytes[28] = (value shr 24).toByte()
        bytes[29] = (value shr 16).toByte()
        bytes[30] = (value shr 8).toByte()
        bytes[31] = value.toByte()
        return bytes
    }

    private fun SecureSession.toSnapshot() = SecureSessionSnapshot(
        sessionId = sessionId.value,
        encounterId = encounterId,
        localNodeId = localNodeId,
        remoteNodeId = remoteNodeId,
        role = role,
        protocolVersion = 1,
        suiteId = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
        state = state,
        keyEpoch = currentEpoch.value,
        recordsSent = recordsSent,
        recordsReceived = recordsReceived,
        bytesSent = bytesSent,
        bytesReceived = bytesReceived,
        createdAt = createdAt,
        establishedAt = establishedAt,
        lastActivityAt = lastActivityAt,
        closedAt = closedAt,
        failureReason = failureReason
    )

    private fun SecureSessionWireCodec.encodeHandshakeFrame(frameBytes: ByteArray): ByteArray {
        // Encapsulate handshake frame bytes in standard transport frame format
        return frameBytes
    }
}
