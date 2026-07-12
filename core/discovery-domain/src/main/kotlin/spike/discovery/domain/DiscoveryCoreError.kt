package spike.discovery.domain

sealed class DiscoveryError(
    val code: String,
    message: String
) : Exception(message)

sealed class AdvertisementError(code: String, message: String) : DiscoveryError(code, message)
class AdvertisementTooLarge(size: Int, maxSize: Int) :
    AdvertisementError("AdvertisementTooLarge", "Advertisement is $size bytes; maximum is $maxSize")
class MalformedAdvertisement(message: String) : AdvertisementError("MalformedAdvertisement", message)
class UnsupportedDiscoveryVersion(version: Int) :
    AdvertisementError("UnsupportedDiscoveryVersion", "Unsupported discovery version: $version")
class InvalidEphemeralDiscoveryId(message: String) : AdvertisementError("InvalidEphemeralDiscoveryId", message)
class IncompatibleCapabilities :
    AdvertisementError("IncompatibleCapabilities", "Advertisement does not include required capabilities")
class UnexpectedProtocolServiceId(serviceId: ProtocolServiceId) :
    AdvertisementError("UnexpectedProtocolServiceId", "Unexpected protocol service id: ${serviceId.value}")

sealed class CandidateError(code: String, message: String) : DiscoveryError(code, message)
class CandidateNotFound(candidateId: CandidateId) :
    CandidateError("CandidateNotFound", "Candidate not found: ${candidateId.value}")
class CandidateExpired(candidateId: CandidateId) :
    CandidateError("CandidateExpired", "Candidate expired: ${candidateId.value}")
class CandidateRejected(candidateId: CandidateId) :
    CandidateError("CandidateRejected", "Candidate rejected: ${candidateId.value}")
class InvalidCandidateStateTransition(from: DiscoveryCandidateState, to: DiscoveryCandidateState) :
    CandidateError("InvalidCandidateStateTransition", "Invalid candidate state transition: $from -> $to")
class CandidateTransportUnavailable(candidateId: CandidateId) :
    CandidateError("CandidateTransportUnavailable", "Candidate transport unavailable: ${candidateId.value}")

sealed class ControlChannelError(code: String, message: String) : DiscoveryError(code, message)
class ControlConnectionFailure(message: String) : ControlChannelError("ControlConnectionFailure", message)
class ControlChannelClosed : ControlChannelError("ControlChannelClosed", "Control channel is closed")
class ControlFrameTooLarge(size: Int, maxSize: Int) :
    ControlChannelError("ControlFrameTooLarge", "Control frame is $size bytes; maximum is $maxSize")
class MalformedControlFrame(message: String) : ControlChannelError("MalformedControlFrame", message)
class InvalidSequenceNumber(sequenceNumber: Long, lastAccepted: Long) :
    ControlChannelError("InvalidSequenceNumber", "Sequence $sequenceNumber is not greater than $lastAccepted")
class SessionNonceMismatch : ControlChannelError("SessionNonceMismatch", "Control frame session nonce mismatch")

sealed class ProtocolNegotiationError(code: String, message: String) : DiscoveryError(code, message)
class NoCompatibleDiscoveryVersion : ProtocolNegotiationError(
    "NoCompatibleDiscoveryVersion",
    "No compatible discovery protocol version"
)
class NoCompatibleIdentityVersion : ProtocolNegotiationError(
    "NoCompatibleIdentityVersion",
    "No compatible identity version"
)
class NoCompatibleProofVersion : ProtocolNegotiationError(
    "NoCompatibleProofVersion",
    "No compatible proof version"
)

sealed class PeerAuthenticationError(code: String, message: String) : DiscoveryError(code, message)
class InvalidRemoteIdentity :
    PeerAuthenticationError("InvalidRemoteIdentity", "Remote identity is invalid")
class IdentityNodeMismatch :
    PeerAuthenticationError("IdentityNodeMismatch", "Identity document does not match expected node")
class ChallengeCreationFailure(message: String) :
    PeerAuthenticationError("ChallengeCreationFailure", message)
class InvalidRemoteProof :
    PeerAuthenticationError("InvalidRemoteProof", "Remote proof is invalid")
class ProofReplayDetected :
    PeerAuthenticationError("ProofReplayDetected", "Remote proof replay detected")
class ProofContextMismatch :
    PeerAuthenticationError("ProofContextMismatch", "Proof context mismatch")
class MutualAuthenticationIncomplete :
    PeerAuthenticationError("MutualAuthenticationIncomplete", "Mutual authentication is incomplete")

sealed class DiscoveryRecoveryError(code: String, message: String) : DiscoveryError(code, message)
class CandidateRecoveryFailure(message: String) :
    DiscoveryRecoveryError("CandidateRecoveryFailure", message)
class EncounterRecoveryFailure(message: String) :
    DiscoveryRecoveryError("EncounterRecoveryFailure", message)
class DiscoveryStateInconsistency(message: String) :
    DiscoveryRecoveryError("DiscoveryStateInconsistency", message)

