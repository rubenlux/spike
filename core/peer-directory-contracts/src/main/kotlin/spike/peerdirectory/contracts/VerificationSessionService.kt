package spike.peerdirectory.contracts

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*

/**
 * Pure Contract Interface for Verification Ceremonies (QR, SAS, Manual) per SPEC-010 Sections 34-54.
 */
interface VerificationSessionService {
    suspend fun startVerificationSession(
        localNodeId: NodeId,
        remoteNodeId: NodeId,
        method: VerificationMethod,
        encounterId: String? = null,
        secureSessionId: String? = null
    ): VerificationSession

    suspend fun getVerificationSession(sessionId: VerificationSessionId): VerificationSession?

    suspend fun getQrVerificationPayload(sessionId: VerificationSessionId): QrVerificationPayload

    suspend fun submitQrVerificationPayload(
        sessionId: VerificationSessionId,
        payload: QrVerificationPayload
    ): VerificationEvidence

    suspend fun getSas(sessionId: VerificationSessionId): ShortAuthenticationString

    suspend fun confirmSasMatch(sessionId: VerificationSessionId): VerificationEvidence

    suspend fun rejectSas(sessionId: VerificationSessionId)

    suspend fun confirmManualFingerprint(
        sessionId: VerificationSessionId,
        verifiedFingerprint: ReadableFingerprint
    ): VerificationEvidence

    suspend fun cancelVerificationSession(sessionId: VerificationSessionId, reason: String)
}
