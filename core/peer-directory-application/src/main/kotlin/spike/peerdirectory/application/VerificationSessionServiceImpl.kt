package spike.peerdirectory.application

import spike.identity.domain.NodeId
import spike.peerdirectory.contracts.VerificationSessionService
import spike.peerdirectory.domain.*
import spike.peerdirectory.storage.PeerIdentityRepository
import java.security.MessageDigest
import java.util.UUID

class VerificationSessionServiceImpl(
    private val repository: PeerIdentityRepository,
    private val sessionTtlMs: Long = 300_000L // 5 minutes default
) : VerificationSessionService {

    override suspend fun startVerificationSession(
        localNodeId: NodeId,
        remoteNodeId: NodeId,
        method: VerificationMethod,
        encounterId: String?,
        secureSessionId: String?
    ): VerificationSession {
        val now = System.currentTimeMillis()
        val localNonce = UUID.randomUUID().toString().replace("-", "")

        val initialBindingHash = computeSessionBindingHash(
            localNodeId = localNodeId,
            remoteNodeId = remoteNodeId,
            localNonce = localNonce,
            remoteNonce = "",
            method = method
        )

        val session = VerificationSession(
            localNodeId = localNodeId,
            remoteNodeId = remoteNodeId,
            method = method,
            state = VerificationState.CREATED,
            localNonce = localNonce,
            sessionBindingHash = initialBindingHash,
            startedAt = now,
            expiresAt = now + sessionTtlMs,
            encounterId = encounterId,
            secureSessionId = secureSessionId
        )

        repository.saveVerificationSession(session)
        return session
    }

    override suspend fun getVerificationSession(sessionId: VerificationSessionId): VerificationSession? {
        val session = repository.getVerificationSession(sessionId) ?: return null
        val now = System.currentTimeMillis()
        if (session.state != VerificationState.VERIFIED && session.state != VerificationState.FAILED && now > session.expiresAt) {
            val expiredSession = session.copy(
                state = VerificationState.EXPIRED,
                failedAt = now,
                failureReason = "SESSION_EXPIRED"
            )
            repository.saveVerificationSession(expiredSession)
            return expiredSession
        }
        return session
    }

    override suspend fun getQrVerificationPayload(sessionId: VerificationSessionId): QrVerificationPayload {
        val session = getActiveSessionOrThrow(sessionId)
        require(session.method == VerificationMethod.QR) {
            throw LocalPeerIdentityError.VerificationError.VerificationMethodMismatch(VerificationMethod.QR, session.method)
        }

        val fingerprint = ReadableFingerprintGenerator.generateFingerprint(session.remoteNodeId.value.toByteArray(Charsets.UTF_8))

        return QrVerificationPayload(
            protocolVersion = "1.0",
            verificationSessionId = session.verificationSessionId,
            presentingNodeId = session.localNodeId,
            expectedRemoteNodeId = session.remoteNodeId,
            sessionBindingHash = session.sessionBindingHash ?: "",
            identityFingerprint = fingerprint,
            verificationNonce = session.localNonce,
            expiresAt = session.expiresAt
        )
    }

    override suspend fun submitQrVerificationPayload(
        sessionId: VerificationSessionId,
        payload: QrVerificationPayload
    ): VerificationEvidence {
        val session = getActiveSessionOrThrow(sessionId)

        if (repository.isVerificationNonceConsumed(payload.verificationNonce)) {
            throw LocalPeerIdentityError.QrVerificationError.QrReplayDetected(payload.verificationNonce)
        }

        if (payload.expectedRemoteNodeId != session.localNodeId || payload.presentingNodeId != session.remoteNodeId) {
            throw LocalPeerIdentityError.QrVerificationError.QrNodeIdMismatch(session.remoteNodeId, payload.presentingNodeId)
        }

        val now = System.currentTimeMillis()
        if (now > payload.expiresAt) {
            throw LocalPeerIdentityError.QrVerificationError.QrPayloadExpired(sessionId)
        }

        repository.markVerificationNonceConsumed(payload.verificationNonce)

        val updatedSession = session.copy(
            state = VerificationState.VERIFIED,
            completedAt = now,
            remoteNonce = payload.verificationNonce
        )
        repository.saveVerificationSession(updatedSession)

        val transcriptHash = VerificationTranscriptHash(
            computeTranscriptHash(session, payload.sessionBindingHash, VerificationResult.VERIFIED)
        )

        val evidence = VerificationEvidence(
            verificationSessionId = session.verificationSessionId,
            localNodeId = session.localNodeId,
            remoteNodeId = session.remoteNodeId,
            method = VerificationMethod.QR,
            result = VerificationResult.VERIFIED,
            sessionBindingHash = payload.sessionBindingHash,
            transcriptHash = transcriptHash,
            identityFingerprint = payload.identityFingerprint,
            verifiedAt = now
        )

        repository.saveVerificationEvidence(evidence)
        return evidence
    }

    override suspend fun getSas(sessionId: VerificationSessionId): ShortAuthenticationString {
        val session = getActiveSessionOrThrow(sessionId)
        require(session.method == VerificationMethod.SAS) {
            throw LocalPeerIdentityError.VerificationError.VerificationMethodMismatch(VerificationMethod.SAS, session.method)
        }
        val bindingHash = session.sessionBindingHash ?: ""
        return SasGenerator.generateSas(bindingHash)
    }

    override suspend fun confirmSasMatch(sessionId: VerificationSessionId): VerificationEvidence {
        val session = getActiveSessionOrThrow(sessionId)
        require(session.method == VerificationMethod.SAS) {
            throw LocalPeerIdentityError.VerificationError.VerificationMethodMismatch(VerificationMethod.SAS, session.method)
        }

        val now = System.currentTimeMillis()
        val updatedSession = session.copy(
            state = VerificationState.VERIFIED,
            completedAt = now
        )
        repository.saveVerificationSession(updatedSession)

        val fingerprint = ReadableFingerprintGenerator.generateFingerprint(session.remoteNodeId.value.toByteArray(Charsets.UTF_8))
        val transcriptHash = VerificationTranscriptHash(
            computeTranscriptHash(session, session.sessionBindingHash ?: "", VerificationResult.VERIFIED)
        )

        val evidence = VerificationEvidence(
            verificationSessionId = session.verificationSessionId,
            localNodeId = session.localNodeId,
            remoteNodeId = session.remoteNodeId,
            method = VerificationMethod.SAS,
            result = VerificationResult.VERIFIED,
            sessionBindingHash = session.sessionBindingHash ?: "",
            transcriptHash = transcriptHash,
            identityFingerprint = fingerprint,
            verifiedAt = now
        )

        repository.saveVerificationEvidence(evidence)
        return evidence
    }

    override suspend fun rejectSas(sessionId: VerificationSessionId) {
        val session = repository.getVerificationSession(sessionId)
            ?: throw LocalPeerIdentityError.VerificationError.VerificationSessionNotFound(sessionId)

        val now = System.currentTimeMillis()
        val updatedSession = session.copy(
            state = VerificationState.REJECTED,
            failedAt = now,
            failureReason = "SAS_MISMATCH"
        )
        repository.saveVerificationSession(updatedSession)
    }

    override suspend fun confirmManualFingerprint(
        sessionId: VerificationSessionId,
        verifiedFingerprint: ReadableFingerprint
    ): VerificationEvidence {
        val session = getActiveSessionOrThrow(sessionId)
        require(session.method == VerificationMethod.MANUAL_FINGERPRINT) {
            throw LocalPeerIdentityError.VerificationError.VerificationMethodMismatch(VerificationMethod.MANUAL_FINGERPRINT, session.method)
        }

        val now = System.currentTimeMillis()
        val updatedSession = session.copy(
            state = VerificationState.VERIFIED,
            completedAt = now
        )
        repository.saveVerificationSession(updatedSession)

        val transcriptHash = VerificationTranscriptHash(
            computeTranscriptHash(session, session.sessionBindingHash ?: "", VerificationResult.VERIFIED)
        )

        val evidence = VerificationEvidence(
            verificationSessionId = session.verificationSessionId,
            localNodeId = session.localNodeId,
            remoteNodeId = session.remoteNodeId,
            method = VerificationMethod.MANUAL_FINGERPRINT,
            result = VerificationResult.VERIFIED,
            sessionBindingHash = session.sessionBindingHash ?: "",
            transcriptHash = transcriptHash,
            identityFingerprint = verifiedFingerprint,
            verifiedAt = now
        )

        repository.saveVerificationEvidence(evidence)
        return evidence
    }

    override suspend fun cancelVerificationSession(sessionId: VerificationSessionId, reason: String) {
        val session = repository.getVerificationSession(sessionId)
            ?: throw LocalPeerIdentityError.VerificationError.VerificationSessionNotFound(sessionId)

        val now = System.currentTimeMillis()
        val updatedSession = session.copy(
            state = VerificationState.CANCELLED,
            failedAt = now,
            failureReason = reason
        )
        repository.saveVerificationSession(updatedSession)
    }

    private suspend fun getActiveSessionOrThrow(sessionId: VerificationSessionId): VerificationSession {
        val session = getVerificationSession(sessionId)
            ?: throw LocalPeerIdentityError.VerificationError.VerificationSessionNotFound(sessionId)

        if (session.state == VerificationState.EXPIRED) {
            throw LocalPeerIdentityError.VerificationError.VerificationSessionExpired(sessionId)
        }
        if (session.state == VerificationState.VERIFIED || session.state == VerificationState.REJECTED || session.state == VerificationState.CANCELLED) {
            throw LocalPeerIdentityError.VerificationError.VerificationSessionAlreadyCompleted(sessionId)
        }
        return session
    }

    private fun computeSessionBindingHash(
        localNodeId: NodeId,
        remoteNodeId: NodeId,
        localNonce: String,
        remoteNonce: String,
        method: VerificationMethod
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("OFFLINE_PROTOCOL_VERIFICATION_SESSION_V1".toByteArray(Charsets.UTF_8))
        val canonicalNodes = if (localNodeId.value < remoteNodeId.value) {
            "${localNodeId.value}:${remoteNodeId.value}"
        } else {
            "${remoteNodeId.value}:${localNodeId.value}"
        }
        digest.update(canonicalNodes.toByteArray(Charsets.UTF_8))
        digest.update(localNonce.toByteArray(Charsets.UTF_8))
        digest.update(remoteNonce.toByteArray(Charsets.UTF_8))
        digest.update(method.name.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeTranscriptHash(
        session: VerificationSession,
        bindingHash: String,
        result: VerificationResult
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("1.0".toByteArray(Charsets.UTF_8))
        digest.update(session.localNodeId.value.toByteArray(Charsets.UTF_8))
        digest.update(session.remoteNodeId.value.toByteArray(Charsets.UTF_8))
        digest.update(session.method.name.toByteArray(Charsets.UTF_8))
        digest.update(bindingHash.toByteArray(Charsets.UTF_8))
        digest.update(result.name.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
