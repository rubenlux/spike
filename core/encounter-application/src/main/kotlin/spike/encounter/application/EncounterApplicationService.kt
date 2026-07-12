package spike.encounter.application

import java.util.UUID
import spike.discovery.contracts.EncounterRepository
import spike.discovery.domain.AuthenticatedPeer
import spike.discovery.domain.DiscoveryTimestamp
import spike.discovery.domain.MonotonicClock
import spike.encounter.domain.DiscoveryTransportType
import spike.encounter.domain.Encounter
import spike.encounter.domain.EncounterAlreadyActive
import spike.encounter.domain.EncounterId
import spike.encounter.domain.EncounterLifecycleService
import spike.encounter.domain.EncounterState
import spike.identity.domain.NodeId

class EncounterApplicationService(
    private val encounterRepository: EncounterRepository,
    private val clock: MonotonicClock,
    private val lifecycleService: EncounterLifecycleService = EncounterLifecycleService()
) {
    suspend fun createActiveEncounter(
        localNodeId: NodeId,
        authenticatedPeer: AuthenticatedPeer,
        transportType: DiscoveryTransportType
    ): Encounter {
        val duplicate = encounterRepository.activeEncounters()
            .firstOrNull {
                it.remoteNodeId == authenticatedPeer.nodeId &&
                    it.state in setOf(EncounterState.CREATING, EncounterState.ACTIVE, EncounterState.DEGRADED)
            }
        if (duplicate != null) throw EncounterAlreadyActive(authenticatedPeer.nodeId.value)

        val created = lifecycleService.create(
            encounterId = EncounterId(UUID.randomUUID().toString()),
            localNodeId = localNodeId,
            authenticatedPeer = authenticatedPeer,
            startedAt = DiscoveryTimestamp(clock.nowMillis()),
            transportType = transportType
        )
        val active = lifecycleService.activate(created)
        encounterRepository.save(active)
        return active
    }

    suspend fun closeEncounter(encounter: Encounter): Encounter {
        val closed = lifecycleService.close(encounter, DiscoveryTimestamp(clock.nowMillis()))
        encounterRepository.save(closed)
        return closed
    }
}

