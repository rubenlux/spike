package spike.discovery.domain

import spike.identity.domain.IdentityDocument
import spike.identity.domain.NodeId

enum class DiscoveryCandidateState {
    DETECTED,
    COMPATIBLE,
    CONNECTING,
    CONTROL_CONNECTED,
    IDENTIFYING,
    IDENTIFIED,
    AUTHENTICATING,
    AUTHENTICATED,
    REJECTED,
    EXPIRED,
    FAILED
}

enum class DiscoveryEventType {
    ADVERTISING_STARTED,
    ADVERTISING_STOPPED,
    SCANNING_STARTED,
    SCANNING_STOPPED,
    EPHEMERAL_ID_CREATED,
    EPHEMERAL_ID_ROTATED,
    ADVERTISEMENT_RECEIVED,
    ADVERTISEMENT_REJECTED,
    CANDIDATE_CREATED,
    CANDIDATE_UPDATED,
    CANDIDATE_COMPATIBLE,
    CANDIDATE_CONTROL_CONNECTED,
    CANDIDATE_IDENTIFIED,
    CANDIDATE_AUTHENTICATED,
    CANDIDATE_REJECTED,
    CANDIDATE_EXPIRED,
    CONTROL_CONNECTION_FAILED,
    PROTOCOL_NEGOTIATION_FAILED,
    IDENTITY_VALIDATION_FAILED,
    AUTHENTICATION_FAILED,
    RECOVERY_STARTED,
    RECOVERY_COMPLETED
}

data class DiscoveryAdvertisementV1(
    val discoveryVersion: DiscoveryVersion,
    val ephemeralDiscoveryId: EphemeralDiscoveryId,
    val advertisementNonce: AdvertisementNonce,
    val capabilityBitmap: CapabilityBitmap,
    val flags: Int
) {
    init {
        require(flags in 0..255) { "Advertisement flags must fit in one byte" }
    }
}

data class RawDiscoveryAdvertisement(
    val protocolServiceId: ProtocolServiceId,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean =
        other is RawDiscoveryAdvertisement &&
            protocolServiceId == other.protocolServiceId &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int =
        31 * protocolServiceId.hashCode() + payload.contentHashCode()
}

data class ProximityObservation(
    val observationId: ObservationId,
    val rawAdvertisement: RawDiscoveryAdvertisement,
    val observedAt: DiscoveryTimestamp,
    val signalStrength: SignalStrength?,
    val transportReference: TransportReference,
    val metadata: Map<String, String>
)

data class DiscoveryCandidate(
    val candidateId: CandidateId,
    val ephemeralDiscoveryId: EphemeralDiscoveryId,
    val discoveryVersion: DiscoveryVersion,
    val capabilities: CapabilityBitmap,
    val firstSeenAt: DiscoveryTimestamp,
    val lastSeenAt: DiscoveryTimestamp,
    val observationCount: Long,
    val latestSignalStrength: SignalStrength?,
    val state: DiscoveryCandidateState,
    val transportReference: TransportReference,
    val controlChannelActive: Boolean,
    val authenticationSessionId: AuthenticationSessionId?,
    val failureReason: String?
)

data class AuthenticatedPeer(
    val nodeId: NodeId,
    val identityDocument: IdentityDocument,
    val candidateId: CandidateId,
    val authenticatedAt: DiscoveryTimestamp,
    val authenticationSessionId: AuthenticationSessionId,
    val capabilities: CapabilityBitmap
)

data class DiscoveryEvent(
    val eventId: String,
    val eventType: DiscoveryEventType,
    val candidateId: CandidateId?,
    val occurredAt: DiscoveryTimestamp,
    val sequenceNumber: Long,
    val metadata: Map<String, String>
)

