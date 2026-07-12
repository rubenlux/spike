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

        val session = SecureSession(
            sessionId = sessionId,
            encounterId = encounterId,
            localNodeId = localNodeId,
            remoteNodeId = remoteNodeId,
            role = role,
            state = SessionState.NEW
        )

        val handshakeId = HandshakeId(UUID.randomUUID().toString())
        val context = HandshakeContext(
            handshakeId = handshakeId,
            state = HandshakeState.NEW
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

        val session = SecureSession(
            sessionId = sessionId,
            encounterId = encounterId,
            localNodeId = localNodeId,
            remoteNodeId = remoteNodeId,
            role = role,
            state = SessionState.NEW
        )

        val handshakeId = HandshakeId(UUID.randomUUID().toString())
        val context = HandshakeContext(
            handshakeId = handshakeId,
            state = HandshakeState.NEW
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

        if (session.state == SessionState.FAILED || ctx.state == HandshakeState.FAILED) {
            throw SecureSessionError.SessionStateError.SecureSessionAlreadyTerminal("Session is in failed state")
        }

        try {
            if (frame.isEmpty()) {
                // Initial trigger for Initiator
                if (session.role != SessionRole.INITIATOR) {
                    throw SecureSessionError.RoleError.InvalidSessionRole("Only initiator can trigger initial frame")
                }
                if (ctx.state != HandshakeState.NEW) {
                    throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Handshake already started")
                }

                session.state = SessionState.HANDSHAKING
                ctx.state = HandshakeState.NEGOTIATING
                emitEvent(SecureSessionEventType.SESSION_NEGOTIATION_STARTED, session.sessionId)

                val keyPair = cryptoProvider.generateEphemeralKeyPair()
                ctx.localEphemeralKeyPair = keyPair
                ctx.state = HandshakeState.EPHEMERAL_KEY_GENERATED
                emitEvent(SecureSessionEventType.EPHEMERAL_KEY_GENERATED, session.sessionId)

                val nonceBytes = cryptoProvider.secureRandomBytes(16)
                ctx.localNonce = SessionNonce(nonceBytes)

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

                ctx.state = HandshakeState.HELLO_SENT
                emitEvent(SecureSessionEventType.SESSION_HELLO_SENT, session.sessionId)
                return helloBytes
            }

            val decoded = wireCodec.decodeHandshakeFrame(frame)

            when (decoded) {
                is DecodedHandshakeFrame.SessionHello -> {
                    if (session.role != SessionRole.RESPONDER) {
                        throw SecureSessionError.RoleError.RoleConflict("Received SESSION_HELLO but role is not Responder")
                    }
                    if (ctx.state != HandshakeState.NEW && ctx.state != HandshakeState.NEGOTIATING) {
                        throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Unexpected SESSION_HELLO in state ${ctx.state}")
                    }

                    emitEvent(SecureSessionEventType.SESSION_HELLO_RECEIVED, session.sessionId)
                    session.state = SessionState.HANDSHAKING
                    ctx.state = HandshakeState.NEGOTIATING
                    emitEvent(SecureSessionEventType.SESSION_NEGOTIATION_STARTED, session.sessionId)

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

                    val activeCtx = ctx.copy(handshakeId = HandshakeId(decoded.handshakeId))
                    handshakeContexts[sessionId] = activeCtx

                    activeCtx.remoteNonce = SessionNonce(decoded.nonce)
                    activeCtx.remoteEphemeralPublicKey = EphemeralPublicKey(decoded.ephemeralPublicKey)

                    val keyPair = cryptoProvider.generateEphemeralKeyPair()
                    activeCtx.localEphemeralKeyPair = keyPair
                    activeCtx.state = HandshakeState.EPHEMERAL_KEY_GENERATED
                    emitEvent(SecureSessionEventType.EPHEMERAL_KEY_GENERATED, session.sessionId)

                    val nonceBytes = cryptoProvider.secureRandomBytes(16)
                    activeCtx.localNonce = SessionNonce(nonceBytes)

                    // Compute DH shared secret
                    val dh = cryptoProvider.calculateSharedSecret(keyPair.privateKey, activeCtx.remoteEphemeralPublicKey!!)
                    if (dh.all { it == 0.toByte() }) {
                        throw SecureSessionError.EphemeralKeyError.InvalidSharedSecret("Shared secret cannot be all zeroes")
                    }
                    activeCtx.dhSharedSecret = dh
                    emitEvent(SecureSessionEventType.DH_SHARED_SECRET_CREATED, session.sessionId)

                    // Compute transcript hash
                    val canonicalTranscript = buildCanonicalTranscript(
                        version = 1,
                        suite = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                        encounterId = session.encounterId,
                        handshakeId = decoded.handshakeId,
                        initiatorNodeId = decoded.initiatorNodeId,
                        responderNodeId = decoded.responderNodeId,
                        initiatorNonce = decoded.nonce,
                        responderNonce = nonceBytes,
                        initiatorEphemPubKey = decoded.ephemeralPublicKey,
                        responderEphemPubKey = keyPair.publicKey.bytes,
                        capabilities = decoded.capabilities
                    )
                    activeCtx.transcriptHash = cryptoProvider.sha256("OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1".encodeToByteArray() + canonicalTranscript)
                    session.transcriptHash = activeCtx.transcriptHash
                    emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_CREATED, session.sessionId)
                    emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_HASHED, session.sessionId)

                    val responseBytes = wireCodec.encodeSessionHelloResponse(
                        version = 1,
                        selectedSuite = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                        encounterId = session.encounterId,
                        handshakeId = activeCtx.handshakeId.value,
                        initiatorNodeId = decoded.initiatorNodeId,
                        responderNodeId = decoded.responderNodeId,
                        initiatorNonce = decoded.nonce,
                        responderNonce = nonceBytes,
                        initiatorEphemeralPublicKey = decoded.ephemeralPublicKey,
                        responderEphemeralPublicKey = keyPair.publicKey.bytes
                    )

                    activeCtx.state = HandshakeState.HELLO_RECEIVED
                    emitEvent(SecureSessionEventType.SESSION_HELLO_RESPONSE_SENT, session.sessionId)
                    return responseBytes
                }

                is DecodedHandshakeFrame.SessionHelloResponse -> {
                    if (session.role != SessionRole.INITIATOR) {
                        throw SecureSessionError.RoleError.RoleConflict("Received hello response but role is not Initiator")
                    }
                    if (ctx.state != HandshakeState.HELLO_SENT) {
                        throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Unexpected hello response in state ${ctx.state}")
                    }

                    emitEvent(SecureSessionEventType.SESSION_HELLO_RESPONSE_RECEIVED, session.sessionId)

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
                    if (!decoded.initiatorNonce.contentEquals(ctx.localNonce!!.bytes)) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("Initiator nonce mismatch")
                    }
                    if (!decoded.initiatorEphemeralPublicKey.contentEquals(ctx.localEphemeralKeyPair!!.publicKey.bytes)) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("Initiator ephemeral public key mismatch")
                    }

                    ctx.remoteNonce = SessionNonce(decoded.responderNonce)
                    ctx.remoteEphemeralPublicKey = EphemeralPublicKey(decoded.responderEphemeralPublicKey)

                    // Compute DH shared secret
                    val dh = cryptoProvider.calculateSharedSecret(ctx.localEphemeralKeyPair!!.privateKey, ctx.remoteEphemeralPublicKey!!)
                    if (dh.all { it == 0.toByte() }) {
                        throw SecureSessionError.EphemeralKeyError.InvalidSharedSecret("Shared secret cannot be all zeroes")
                    }
                    ctx.dhSharedSecret = dh
                    emitEvent(SecureSessionEventType.DH_SHARED_SECRET_CREATED, session.sessionId)

                    // Compute transcript hash
                    val canonicalTranscript = buildCanonicalTranscript(
                        version = 1,
                        suite = CipherSuiteId.OFFLINE_SESSION_SUITE_V1.value,
                        encounterId = session.encounterId,
                        handshakeId = decoded.handshakeId,
                        initiatorNodeId = decoded.initiatorNodeId,
                        responderNodeId = decoded.responderNodeId,
                        initiatorNonce = ctx.localNonce!!.bytes,
                        responderNonce = decoded.responderNonce,
                        initiatorEphemPubKey = ctx.localEphemeralKeyPair!!.publicKey.bytes,
                        responderEphemPubKey = decoded.responderEphemeralPublicKey,
                        capabilities = emptyMap()
                    )
                    ctx.transcriptHash = cryptoProvider.sha256("OFFLINE_PROTOCOL_SECURE_SESSION_TRANSCRIPT_V1".encodeToByteArray() + canonicalTranscript)
                    session.transcriptHash = ctx.transcriptHash
                    emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_CREATED, session.sessionId)
                    emitEvent(SecureSessionEventType.HANDSHAKE_TRANSCRIPT_HASHED, session.sessionId)

                    session.state = SessionState.AUTHENTICATING

                    // Sign transcript
                    val authPayload = "OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1".encodeToByteArray() +
                            SessionRole.INITIATOR.name.encodeToByteArray() +
                            session.localNodeId.encodeToByteArray() +
                            session.remoteNodeId.encodeToByteArray() +
                            ctx.transcriptHash!!

                    val sigResult = identityCore.sign(spike.identity.domain.DomainTag.SECURE_SESSION_AUTH_V1, authPayload)
                    if (sigResult.disposition != IdentityCommandDisposition.APPLIED) {
                        throw SecureSessionError.TranscriptError.HandshakeBindingFailure("Failed to sign transcript")
                    }

                    val signatureBytes = sigResult.value!!.bytes
                    ctx.localAuthSignature = signatureBytes
                    emitEvent(SecureSessionEventType.LOCAL_HANDSHAKE_AUTH_CREATED, session.sessionId)

                    val authBytes = wireCodec.encodeHandshakeFrame(
                        wireCodec.encodeHandshakeAuth(
                            handshakeId = ctx.handshakeId.value,
                            signerNodeId = session.localNodeId,
                            signerRoleOrdinal = SessionRole.INITIATOR.ordinal,
                            transcriptHash = ctx.transcriptHash!!,
                            signature = signatureBytes
                        )
                    )

                    ctx.state = HandshakeState.LOCAL_AUTH_SENT
                    return authBytes
                }

                is DecodedHandshakeFrame.HandshakeAuth -> {
                    if (ctx.transcriptHash == null) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("Transcript hash not set")
                    }
                    if (!decoded.transcriptHash.contentEquals(ctx.transcriptHash!!)) {
                        throw SecureSessionError.TranscriptError.TranscriptHashMismatch("Transcript hash mismatch")
                    }
                    if (decoded.handshakeId != ctx.handshakeId.value) {
                        throw SecureSessionError.TranscriptError.TranscriptMismatch("HandshakeId mismatch")
                    }

                    val expectedRole = if (session.role == SessionRole.INITIATOR) SessionRole.RESPONDER else SessionRole.INITIATOR
                    if (decoded.signerRoleOrdinal != expectedRole.ordinal) {
                        throw SecureSessionError.HandshakeAuthenticationError.HandshakeRoleMismatch("Unexpected role ordinal")
                    }
                    if (decoded.signerNodeId != session.remoteNodeId) {
                        throw SecureSessionError.HandshakeAuthenticationError.HandshakeNodeMismatch("Unexpected signer NodeId")
                    }

                    emitEvent(SecureSessionEventType.REMOTE_HANDSHAKE_AUTH_RECEIVED, session.sessionId)

                    // Verify signature
                    val authPayload = "OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1".encodeToByteArray() +
                            expectedRole.name.encodeToByteArray() +
                            session.remoteNodeId.encodeToByteArray() +
                            session.localNodeId.encodeToByteArray() +
                            ctx.transcriptHash!!

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

                    ctx.remoteAuthSignature = decoded.signature
                    emitEvent(SecureSessionEventType.REMOTE_HANDSHAKE_AUTH_VERIFIED, session.sessionId)

                    session.state = SessionState.DERIVING_KEYS

                    // Key Derivation
                    val earlySecret = cryptoProvider.hkdfExtract(ctx.transcriptHash!!, ctx.dhSharedSecret!!)
                    val initNodeId = if (session.role == SessionRole.INITIATOR) session.localNodeId else session.remoteNodeId
                    val respNodeId = if (session.role == SessionRole.INITIATOR) session.remoteNodeId else session.localNodeId
                    val masterInfo = "OFFLINE_PROTOCOL_SESSION_MASTER_V1".encodeToByteArray() +
                            ctx.transcriptHash!! +
                            initNodeId.encodeToByteArray() +
                            respNodeId.encodeToByteArray()
                    val sessionMasterSecret = cryptoProvider.hkdfExpand(earlySecret, masterInfo, 32)

                    val i2rKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "initiator-to-responder-traffic-key".encodeToByteArray() + ctx.transcriptHash!!, 32)
                    val r2iKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "responder-to-initiator-traffic-key".encodeToByteArray() + ctx.transcriptHash!!, 32)
                    val i2rNonce = cryptoProvider.hkdfExpand(sessionMasterSecret, "initiator-nonce-base".encodeToByteArray() + ctx.transcriptHash!!, 12)
                    val r2iNonce = cryptoProvider.hkdfExpand(sessionMasterSecret, "responder-nonce-base".encodeToByteArray() + ctx.transcriptHash!!, 12)
                    val i2rRekey = cryptoProvider.hkdfExpand(sessionMasterSecret, "initiator-rekey-secret".encodeToByteArray() + ctx.transcriptHash!!, 32)
                    val r2iRekey = cryptoProvider.hkdfExpand(sessionMasterSecret, "responder-rekey-secret".encodeToByteArray() + ctx.transcriptHash!!, 32)
                    val confirmationKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "session-confirmation-key".encodeToByteArray() + ctx.transcriptHash!!, 32)

                    val keyMaterial = if (session.role == SessionRole.INITIATOR) {
                        SessionKeyMaterial(i2rKey, r2iKey, i2rNonce, r2iNonce, i2rRekey, r2iRekey,
                            cryptoProvider.hkdfExpand(sessionMasterSecret, "session-exporter-secret".encodeToByteArray() + ctx.transcriptHash!!, 32))
                    } else {
                        SessionKeyMaterial(r2iKey, i2rKey, r2iNonce, i2rNonce, r2iRekey, i2rRekey,
                            cryptoProvider.hkdfExpand(sessionMasterSecret, "session-exporter-secret".encodeToByteArray() + ctx.transcriptHash!!, 32))
                    }
                    session.keyMaterial = keyMaterial
                    ctx.state = HandshakeState.KEYS_DERIVED
                    emitEvent(SecureSessionEventType.SESSION_KEYS_DERIVED, session.sessionId)

                    session.state = SessionState.CONFIRMING_KEYS

                    if (session.role == SessionRole.RESPONDER && ctx.localAuthSignature == null) {
                        // Responder needs to sign and respond with HANDSHAKE_AUTH first
                        val respAuthPayload = "OFFLINE_PROTOCOL_SECURE_SESSION_AUTH_V1".encodeToByteArray() +
                                SessionRole.RESPONDER.name.encodeToByteArray() +
                                session.localNodeId.encodeToByteArray() +
                                session.remoteNodeId.encodeToByteArray() +
                                ctx.transcriptHash!!

                        val sigResult = identityCore.sign(spike.identity.domain.DomainTag.SECURE_SESSION_AUTH_V1, respAuthPayload)
                        val signatureBytes = sigResult.value!!.bytes
                        ctx.localAuthSignature = signatureBytes
                        emitEvent(SecureSessionEventType.LOCAL_HANDSHAKE_AUTH_CREATED, session.sessionId)

                        val responseAuthBytes = wireCodec.encodeHandshakeFrame(
                            wireCodec.encodeHandshakeAuth(
                                handshakeId = ctx.handshakeId.value,
                                signerNodeId = session.localNodeId,
                                signerRoleOrdinal = SessionRole.RESPONDER.ordinal,
                                transcriptHash = ctx.transcriptHash!!,
                                signature = signatureBytes
                            )
                        )
                        ctx.state = HandshakeState.LOCAL_AUTH_SENT
                        return responseAuthBytes
                    }

                    // Otherwise, we are either:
                    // 1. Initiator receiving Responder's HANDSHAKE_AUTH -> generate and send KEY_CONFIRMATION
                    // 2. Responder receiving Initiator's KEY_CONFIRMATION (Wait! In this block we process HandshakeAuth, not KeyConfirmation)
                    if (session.role == SessionRole.INITIATOR) {
                        val confTagPayload = "OFFLINE_PROTOCOL_KEY_CONFIRMATION_V1".encodeToByteArray() +
                                ctx.transcriptHash!! +
                                session.role!!.name.encodeToByteArray()
                        val localConfTag = cryptoProvider.hmacSha256(confirmationKey, confTagPayload)

                        val confirmationBytes = wireCodec.encodeHandshakeFrame(
                            wireCodec.encodeKeyConfirmation(
                                handshakeId = ctx.handshakeId.value,
                                signerRoleOrdinal = session.role!!.ordinal,
                                confirmationTag = localConfTag
                            )
                        )
                        ctx.state = HandshakeState.KEY_CONFIRMING
                        emitEvent(SecureSessionEventType.KEY_CONFIRMATION_SENT, session.sessionId)
                        return confirmationBytes
                    }

                    return null
                }

                is DecodedHandshakeFrame.KeyConfirmation -> {
                    if (session.state != SessionState.CONFIRMING_KEYS && ctx.state != HandshakeState.KEYS_DERIVED && ctx.state != HandshakeState.LOCAL_AUTH_SENT && ctx.state != HandshakeState.KEY_CONFIRMING) {
                        throw SecureSessionError.SessionStateError.InvalidSecureSessionStateTransition("Unexpected confirmation in state ${session.state}")
                    }

                    emitEvent(SecureSessionEventType.KEY_CONFIRMATION_RECEIVED, session.sessionId)

                    val expectedRole = if (session.role == SessionRole.INITIATOR) SessionRole.RESPONDER else SessionRole.INITIATOR
                    if (decoded.signerRoleOrdinal != expectedRole.ordinal) {
                        throw SecureSessionError.HandshakeAuthenticationError.HandshakeRoleMismatch("Unexpected confirmation role ordinal")
                    }

                    // Verify confirmation tag
                    val masterInfo = "OFFLINE_PROTOCOL_SESSION_MASTER_V1".encodeToByteArray() +
                            ctx.transcriptHash!! +
                            (if (session.role == SessionRole.INITIATOR) session.localNodeId else session.remoteNodeId).encodeToByteArray() +
                            (if (session.role == SessionRole.INITIATOR) session.remoteNodeId else session.localNodeId).encodeToByteArray()
                    val earlySecret = cryptoProvider.hkdfExtract(ctx.transcriptHash!!, ctx.dhSharedSecret!!)
                    val sessionMasterSecret = cryptoProvider.hkdfExpand(earlySecret, masterInfo, 32)
                    val confirmationKey = cryptoProvider.hkdfExpand(sessionMasterSecret, "session-confirmation-key".encodeToByteArray() + ctx.transcriptHash!!, 32)

                    val expectedConfTagPayload = "OFFLINE_PROTOCOL_KEY_CONFIRMATION_V1".encodeToByteArray() +
                            ctx.transcriptHash!! +
                            expectedRole.name.encodeToByteArray()
                    val expectedConfTag = cryptoProvider.hmacSha256(confirmationKey, expectedConfTagPayload)

                    if (!decoded.confirmationTag.contentEquals(expectedConfTag)) {
                        emitEvent(SecureSessionEventType.KEY_CONFIRMATION_VERIFIED, session.sessionId, mapOf("status" to "failed"))
                        throw SecureSessionError.KeyDerivationError.KeyConfirmationFailure("Confirmation tag verification failed")
                    }

                    emitEvent(SecureSessionEventType.KEY_CONFIRMATION_VERIFIED, session.sessionId, mapOf("status" to "success"))

                    if (session.role == SessionRole.RESPONDER) {
                        // Responder verified Initiator's tag. Now responds with own tag, and establishes session.
                        val confTagPayload = "OFFLINE_PROTOCOL_KEY_CONFIRMATION_V1".encodeToByteArray() +
                                ctx.transcriptHash!! +
                                session.role!!.name.encodeToByteArray()
                        val localConfTag = cryptoProvider.hmacSha256(confirmationKey, confTagPayload)

                        val responseConfBytes = wireCodec.encodeHandshakeFrame(
                            wireCodec.encodeKeyConfirmation(
                                handshakeId = ctx.handshakeId.value,
                                signerRoleOrdinal = session.role!!.ordinal,
                                confirmationTag = localConfTag
                            )
                        )

                        session.state = SessionState.ESTABLISHED
                        session.establishedAt = System.currentTimeMillis()
                        ctx.state = HandshakeState.ESTABLISHED
                        emitEvent(SecureSessionEventType.SESSION_ESTABLISHED, session.sessionId)

                        // Cleanup handshake context
                        ctx.destroy()
                        handshakeContexts.remove(sessionId)

                        return responseConfBytes
                    } else {
                        // Initiator verified Responder's tag. Handshake is complete!
                        session.state = SessionState.ESTABLISHED
                        session.establishedAt = System.currentTimeMillis()
                        ctx.state = HandshakeState.ESTABLISHED
                        emitEvent(SecureSessionEventType.SESSION_ESTABLISHED, session.sessionId)

                        // Cleanup handshake context
                        ctx.destroy()
                        handshakeContexts.remove(sessionId)

                        return null
                    }
                }
            }
        } catch (e: Exception) {
            session.state = SessionState.FAILED
            ctx.state = HandshakeState.FAILED
            session.failureReason = e.message
            emitEvent(SecureSessionEventType.SESSION_FAILED, session.sessionId, mapOf("error" to (e.message ?: "Unknown error")))
            ctx.destroy()
            handshakeContexts.remove(sessionId)
            throw e
        }
    }

    override suspend fun getSession(sessionId: String): SecureSessionSnapshot? {
        return sessions[sessionId]?.toSnapshot()
    }

    override suspend fun getSessionSnapshot(sessionId: String): SecureSessionSnapshot? {
        return sessions[sessionId]?.toSnapshot()
    }

    override suspend fun encrypt(sessionId: String, recordType: SecureRecordType, plaintext: ByteArray): ByteArray {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

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

        val record = SecureRecord(
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

        emitEvent(SecureSessionEventType.SECURE_RECORD_ENCRYPTED, session.sessionId)

        return wireCodec.encodeSecureRecord(record)
    }

    override suspend fun decrypt(sessionId: String, secureRecordBytes: ByteArray): ByteArray {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        if (session.state != SessionState.ESTABLISHED && session.state != SessionState.REKEYING) {
            throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("Session not established (state=${session.state})")
        }

        val record = try {
            wireCodec.decodeSecureRecord(secureRecordBytes)
        } catch (e: Exception) {
            emitEvent(SecureSessionEventType.SECURE_RECORD_REJECTED, session.sessionId, mapOf("error" to "Malformed secure record"))
            throw SecureSessionError.SecureRecordError.MalformedSecureRecord("Malformed secure record", e)
        }

        if (record.sessionId.value != session.sessionId.value) {
            throw SecureSessionError.SecureRecordError.SessionIdMismatch("Session ID mismatch")
        }

        if (record.keyEpoch.value != session.currentEpoch.value) {
            throw SecureSessionError.SecureRecordError.InvalidKeyEpoch("Key epoch mismatch: expected ${session.currentEpoch.value}, got ${record.keyEpoch.value}")
        }

        val expectedSeqVal = session.expectedReceiveSequence.value
        if (record.sequenceNumber < expectedSeqVal) {
            emitEvent(SecureSessionEventType.REPLAY_REJECTED, session.sessionId)
            throw SecureSessionError.SecureRecordError.ReplayDetected("Replayed sequence number ${record.sequenceNumber} (expected $expectedSeqVal)")
        }
        if (record.sequenceNumber > expectedSeqVal) {
            emitEvent(SecureSessionEventType.SEQUENCE_VIOLATION, session.sessionId)
            throw SecureSessionError.SecureRecordError.OutOfOrderRecord("Out-of-order sequence number ${record.sequenceNumber} (expected $expectedSeqVal)")
        }

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
        val plaintext = try {
            cryptoProvider.aeadDecrypt(keyMaterial.receiveKey, nonce, combinedInput, aad)
        } catch (e: Exception) {
            emitEvent(SecureSessionEventType.SECURE_RECORD_REJECTED, session.sessionId, mapOf("error" to "AEAD decryption failed"))
            throw SecureSessionError.SecureRecordError.AeadAuthenticationFailure("Decryption authentication failed", e)
        }

        session.expectedReceiveSequence = session.expectedReceiveSequence.next()
        session.recordsReceived++
        session.bytesReceived += secureRecordBytes.size
        session.lastActivityAt = System.currentTimeMillis()

        emitEvent(SecureSessionEventType.SECURE_RECORD_DECRYPTED, session.sessionId)

        return plaintext
    }

    override suspend fun requestRekey(sessionId: String) {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        if (session.state != SessionState.ESTABLISHED) {
            throw SecureSessionError.SessionStateError.SecureSessionNotEstablished("Session not established")
        }

        session.state = SessionState.REKEYING
        emitEvent(SecureSessionEventType.REKEY_STARTED, session.sessionId)

        val nextEpoch = session.currentEpoch.next()
        val keyMaterial = session.keyMaterial!!
        val transcriptHash = session.transcriptHash!!

        // Derive new keys
        val info = "OFFLINE_PROTOCOL_REKEY_SECRET_V1".encodeToByteArray() +
                transcriptHash +
                pad32(nextEpoch.value)

        val nextRekeySecret = cryptoProvider.hkdfExpand(keyMaterial.sendRekeySecret, info, 32)
        val nextSendKey = cryptoProvider.hkdfExpand(nextRekeySecret, "send-traffic-key".encodeToByteArray() + transcriptHash, 32)
        val nextReceiveKey = cryptoProvider.hkdfExpand(nextRekeySecret, "receive-traffic-key".encodeToByteArray() + transcriptHash, 32)
        val nextSendNonceBase = cryptoProvider.hkdfExpand(nextRekeySecret, "send-nonce-base".encodeToByteArray() + transcriptHash, 12)
        val nextReceiveNonceBase = cryptoProvider.hkdfExpand(nextRekeySecret, "receive-nonce-base".encodeToByteArray() + transcriptHash, 12)

        val newKeyMaterial = SessionKeyMaterial(
            sendKey = nextSendKey,
            receiveKey = nextReceiveKey,
            sendNonceBase = nextSendNonceBase,
            receiveNonceBase = nextReceiveNonceBase,
            sendRekeySecret = nextRekeySecret,
            receiveRekeySecret = keyMaterial.receiveRekeySecret,
            exporterSecret = keyMaterial.exporterSecret
        )

        // Rotate epoch
        session.keyMaterial?.destroy()
        session.keyMaterial = newKeyMaterial
        session.currentEpoch = nextEpoch
        session.sendSequence = SendSequence(0L)
        session.expectedReceiveSequence = ReceiveSequence(0L)

        session.state = SessionState.ESTABLISHED
        emitEvent(SecureSessionEventType.REKEY_COMPLETED, session.sessionId)
    }

    override suspend fun closeSession(sessionId: String, reason: String) {
        val session = sessions[sessionId]
            ?: throw SecureSessionError.SessionStateError.SecureSessionNotFound("Session $sessionId not found")

        session.state = SessionState.CLOSING
        emitEvent(SecureSessionEventType.SESSION_CLOSE_STARTED, session.sessionId)

        session.destroy()
        session.state = SessionState.CLOSED
        session.closedAt = System.currentTimeMillis()
        emitEvent(SecureSessionEventType.SESSION_CLOSED, session.sessionId)
        sessions.remove(sessionId)
    }

    override suspend fun handleTransportLoss(sessionId: String, reason: String) {
        val session = sessions[sessionId] ?: return
        session.state = SessionState.FAILED
        session.failureReason = "Transport lost: $reason"
        emitEvent(SecureSessionEventType.SESSION_FAILED, session.sessionId, mapOf("error" to "Transport lost: $reason"))
        session.destroy()
        sessions.remove(sessionId)
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
