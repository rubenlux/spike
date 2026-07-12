package spike.encounter.domain

import spike.discovery.domain.AuthenticatedPeer
import spike.discovery.domain.DiscoveryTimestamp
import spike.discovery.domain.ProximityObservation
import spike.identity.domain.NodeId

class EncounterStateMachine {
    private val terminal = setOf(EncounterState.CLOSED, EncounterState.FAILED)

    private val allowed = mapOf(
        EncounterState.CREATING to setOf(EncounterState.ACTIVE, EncounterState.FAILED),
        EncounterState.ACTIVE to setOf(
            EncounterState.DEGRADED,
            EncounterState.CONTACT_LOST,
            EncounterState.CLOSED,
            EncounterState.FAILED
        ),
        EncounterState.DEGRADED to setOf(
            EncounterState.ACTIVE,
            EncounterState.CONTACT_LOST,
            EncounterState.CLOSED,
            EncounterState.FAILED
        ),
        EncounterState.CONTACT_LOST to setOf(EncounterState.CLOSED, EncounterState.FAILED),
        EncounterState.CLOSED to emptySet(),
        EncounterState.FAILED to emptySet()
    )

    fun validateTransition(from: EncounterState, to: EncounterState) {
        if (from == to) return
        if (to !in allowed.getValue(from)) throw InvalidEncounterStateTransition(from, to)
    }

    fun isTerminal(state: EncounterState): Boolean = state in terminal
}

class EncounterLifecycleService(
    private val stateMachine: EncounterStateMachine = EncounterStateMachine()
) {
    fun create(
        encounterId: EncounterId,
        localNodeId: NodeId,
        authenticatedPeer: AuthenticatedPeer,
        startedAt: DiscoveryTimestamp,
        transportType: DiscoveryTransportType
    ): Encounter =
        Encounter(
            encounterId = encounterId,
            localNodeId = localNodeId,
            remoteNodeId = authenticatedPeer.nodeId,
            candidateId = authenticatedPeer.candidateId,
            startedAt = startedAt,
            lastContactAt = startedAt,
            endedAt = null,
            state = EncounterState.CREATING,
            discoveryTransport = transportType,
            authenticationSessionId = authenticatedPeer.authenticationSessionId,
            observationCount = 0,
            latestSignalStrength = null,
            transportReference = null
        )

    fun activate(encounter: Encounter): Encounter =
        transition(encounter, EncounterState.ACTIVE)

    fun degrade(encounter: Encounter): Encounter =
        transition(encounter, EncounterState.DEGRADED)

    fun recordContact(encounter: Encounter, observation: ProximityObservation): Encounter {
        if (stateMachine.isTerminal(encounter.state)) return encounter
        val recovered = if (encounter.state == EncounterState.DEGRADED) EncounterState.ACTIVE else encounter.state
        return encounter.copy(
            state = recovered,
            lastContactAt = observation.observedAt,
            observationCount = encounter.observationCount + 1,
            latestSignalStrength = observation.signalStrength ?: encounter.latestSignalStrength,
            transportReference = observation.transportReference
        )
    }

    fun markContactLost(encounter: Encounter, at: DiscoveryTimestamp): Encounter =
        transition(encounter.copy(lastContactAt = at), EncounterState.CONTACT_LOST)

    fun close(encounter: Encounter, at: DiscoveryTimestamp): Encounter =
        transition(encounter.copy(endedAt = at), EncounterState.CLOSED)

    fun fail(encounter: Encounter, at: DiscoveryTimestamp): Encounter =
        transition(encounter.copy(endedAt = at), EncounterState.FAILED)

    fun resolveDuplicateActive(first: Encounter, second: Encounter): Pair<Encounter, Encounter> {
        if (first.remoteNodeId != second.remoteNodeId) {
            throw DuplicateEncounterConflict("${first.remoteNodeId.value},${second.remoteNodeId.value}")
        }
        val winner = listOf(first, second).minWith(compareBy<Encounter> { it.startedAt.monotonicMillis }.thenBy { it.encounterId.value })
        val loser = if (winner.encounterId == first.encounterId) second else first
        return winner to close(loser, loser.lastContactAt)
    }

    private fun transition(encounter: Encounter, to: EncounterState): Encounter {
        stateMachine.validateTransition(encounter.state, to)
        return encounter.copy(state = to)
    }
}
