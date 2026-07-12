package spike.discovery.domain

import spike.identity.contracts.IdentityCommandDisposition
import spike.identity.contracts.IdentityCore
import spike.identity.contracts.VerificationResult
import spike.identity.domain.ChallengeContext
import spike.identity.domain.IdentityDocument
import spike.identity.domain.NodeId

data class MutualAuthenticationEvidence(
    val sessionNonce: ControlSessionNonce,
    val authenticationSessionId: AuthenticationSessionId,
    val remoteIdentityDocument: IdentityDocument,
    val localProofAcceptedByRemote: Boolean,
    val remoteProofAcceptedLocally: Boolean
)

class PeerAuthenticationService(
    private val identityCore: IdentityCore
) {
    fun authenticationContext(
        sessionNonce: ControlSessionNonce,
        localNodeId: NodeId,
        remoteNodeId: NodeId
    ): ChallengeContext =
        ChallengeContext(
            listOf(
                DISCOVERY_AUTH_CONTEXT_DOMAIN_TAG,
                "session=${sessionNonce.bytes.joinToString("") { "%02x".format(it) }}",
                "local=${localNodeId.value}",
                "remote=${remoteNodeId.value}"
            ).joinToString("|")
        )

    suspend fun authenticate(
        candidate: DiscoveryCandidate,
        evidence: MutualAuthenticationEvidence,
        authenticatedAt: DiscoveryTimestamp
    ): AuthenticatedPeer {
        if (!evidence.localProofAcceptedByRemote || !evidence.remoteProofAcceptedLocally) {
            throw MutualAuthenticationIncomplete()
        }

        val validation = identityCore.validateIdentityDocument(evidence.remoteIdentityDocument)
        if (validation != VerificationResult.VALID) throw InvalidRemoteIdentity()

        val stored = identityCore.storeRemoteIdentity(evidence.remoteIdentityDocument)
        if (stored.disposition == IdentityCommandDisposition.REJECTED) {
            throw InvalidRemoteIdentity()
        }

        return AuthenticatedPeer(
            nodeId = evidence.remoteIdentityDocument.nodeId,
            identityDocument = evidence.remoteIdentityDocument,
            candidateId = candidate.candidateId,
            authenticatedAt = authenticatedAt,
            authenticationSessionId = evidence.authenticationSessionId,
            capabilities = candidate.capabilities
        )
    }
}

