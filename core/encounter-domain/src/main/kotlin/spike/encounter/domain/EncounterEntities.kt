package spike.encounter.domain

import spike.discovery.domain.AuthenticationSessionId
import spike.discovery.domain.CandidateId
import spike.discovery.domain.DiscoveryTimestamp
import spike.discovery.domain.SignalStrength
import spike.discovery.domain.TransportReference
import spike.identity.domain.NodeId

data class EncounterId(val value: String) {
    init {
        require(value.isNotBlank()) { "EncounterId is required" }
        require(value.length <= 128) { "EncounterId is too long" }
    }
}

enum class EncounterState {
    CREATING,
    ACTIVE,
    DEGRADED,
    CONTACT_LOST,
    CLOSED,
    FAILED
}

enum class DiscoveryTransportType {
    BLE,
    FAKE,
    UNKNOWN
}

enum class EncounterEventType {
    ENCOUNTER_CREATED,
    ENCOUNTER_ACTIVE,
    CONTACT_OBSERVED,
    ENCOUNTER_DEGRADED,
    ENCOUNTER_RECOVERED,
    CONTACT_LOST,
    ENCOUNTER_CLOSED,
    ENCOUNTER_FAILED,
    DUPLICATE_ENCOUNTER_MERGED
}

data class Encounter(
    val encounterId: EncounterId,
    val localNodeId: NodeId,
    val remoteNodeId: NodeId,
    val candidateId: CandidateId,
    val startedAt: DiscoveryTimestamp,
    val lastContactAt: DiscoveryTimestamp,
    val endedAt: DiscoveryTimestamp?,
    val state: EncounterState,
    val discoveryTransport: DiscoveryTransportType,
    val authenticationSessionId: AuthenticationSessionId,
    val observationCount: Long,
    val latestSignalStrength: SignalStrength?,
    val transportReference: TransportReference?
)

data class EncounterEvent(
    val eventId: String,
    val encounterId: EncounterId,
    val eventType: EncounterEventType,
    val occurredAt: DiscoveryTimestamp,
    val sequenceNumber: Long,
    val metadata: Map<String, String>
)

