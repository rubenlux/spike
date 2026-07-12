package spike.discovery.testing

import java.util.ArrayDeque
import spike.discovery.contracts.AdvertisementProvider
import spike.discovery.contracts.ControlChannel
import spike.discovery.contracts.ControlChannelEventListener
import spike.discovery.contracts.ControlChannelFactory
import spike.discovery.contracts.ControlChannelState
import spike.discovery.contracts.DiscoveryAdvertiser
import spike.discovery.contracts.DiscoveryCandidateRepository
import spike.discovery.contracts.DiscoveryEventRepository
import spike.discovery.contracts.DiscoveryScanner
import spike.discovery.contracts.EncounterEventRepository
import spike.discovery.contracts.EncounterRepository
import spike.discovery.contracts.ObservationConsumer
import spike.discovery.domain.CandidateId
import spike.discovery.domain.DiscoveryCandidate
import spike.discovery.domain.DiscoveryCandidateState
import spike.discovery.domain.DiscoveryControlFrame
import spike.discovery.domain.DiscoveryEvent
import spike.discovery.domain.EphemeralDiscoveryId
import spike.discovery.domain.MonotonicClock
import spike.discovery.domain.ProximityObservation
import spike.discovery.domain.TransportReference
import spike.encounter.domain.Encounter
import spike.encounter.domain.EncounterEvent
import spike.encounter.domain.EncounterId
import spike.encounter.domain.EncounterState

class MutableMonotonicClock(startMillis: Long = 0) : MonotonicClock {
    private var current = startMillis
    override fun nowMillis(): Long = current
    fun advanceBy(millis: Long) {
        require(millis >= 0) { "Cannot move monotonic clock backwards" }
        current += millis
    }
}

class InMemoryDiscoveryCandidateRepository : DiscoveryCandidateRepository {
    private val candidates = linkedMapOf<CandidateId, DiscoveryCandidate>()

    override suspend fun save(candidate: DiscoveryCandidate) {
        candidates[candidate.candidateId] = candidate
    }

    override suspend fun get(candidateId: CandidateId): DiscoveryCandidate? =
        candidates[candidateId]

    override suspend fun findByEphemeralDiscoveryId(ephemeralDiscoveryId: EphemeralDiscoveryId): DiscoveryCandidate? =
        candidates.values.firstOrNull { it.ephemeralDiscoveryId == ephemeralDiscoveryId }

    override suspend fun activeCandidates(): List<DiscoveryCandidate> =
        candidates.values.filter {
            it.state !in setOf(
                DiscoveryCandidateState.REJECTED,
                DiscoveryCandidateState.EXPIRED,
                DiscoveryCandidateState.FAILED
            )
        }
}

class InMemoryEncounterRepository : EncounterRepository {
    private val encounters = linkedMapOf<EncounterId, Encounter>()

    override suspend fun save(encounter: Encounter) {
        encounters[encounter.encounterId] = encounter
    }

    override suspend fun get(encounterId: EncounterId): Encounter? =
        encounters[encounterId]

    override suspend fun activeEncounters(): List<Encounter> =
        encounters.values.filter { it.state in setOf(EncounterState.CREATING, EncounterState.ACTIVE, EncounterState.DEGRADED) }
}

class InMemoryDiscoveryEventRepository : DiscoveryEventRepository {
    val events = mutableListOf<DiscoveryEvent>()
    override suspend fun append(event: DiscoveryEvent) {
        events += event
    }
}

class InMemoryEncounterEventRepository : EncounterEventRepository {
    val events = mutableListOf<EncounterEvent>()
    override suspend fun append(event: EncounterEvent) {
        events += event
    }
}

class FakeDiscoveryTransport : DiscoveryAdvertiser, DiscoveryScanner {
    private var provider: AdvertisementProvider? = null
    private var consumer: ObservationConsumer? = null
    var advertising = false
        private set
    var scanning = false
        private set

    override suspend fun start(advertisementProvider: AdvertisementProvider) {
        provider = advertisementProvider
        advertising = true
    }

    override suspend fun updateAdvertisement() {
        provider?.currentAdvertisement()
    }

    override suspend fun stop() {
        advertising = false
        scanning = false
    }

    override suspend fun start(observationConsumer: ObservationConsumer) {
        consumer = observationConsumer
        scanning = true
    }

    suspend fun emit(observation: ProximityObservation) {
        require(scanning) { "Scanner is not active" }
        consumer?.onObservation(observation)
    }
}

class FakeControlChannelFactory : ControlChannelFactory {
    val channels = mutableListOf<FakeControlChannel>()

    override suspend fun open(transportReference: TransportReference): ControlChannel =
        FakeControlChannel().also {
            it.open()
            channels += it
        }

    override suspend fun close(channel: ControlChannel) {
        channel.close()
    }
}

class FakeControlChannel : ControlChannel {
    private val inbound = ArrayDeque<DiscoveryControlFrame>()
    private val outbound = mutableListOf<DiscoveryControlFrame>()
    private val listeners = mutableListOf<ControlChannelEventListener>()
    private var state = ControlChannelState.NEW

    override suspend fun open() {
        state = ControlChannelState.OPEN
        listeners.forEach { it.onEvent("open") }
    }

    override suspend fun send(frame: DiscoveryControlFrame) {
        require(state == ControlChannelState.OPEN) { "Channel is not open" }
        outbound += frame
    }

    override suspend fun receive(): DiscoveryControlFrame? {
        require(state == ControlChannelState.OPEN) { "Channel is not open" }
        return inbound.pollFirst()
    }

    override suspend fun close() {
        state = ControlChannelState.CLOSED
        listeners.forEach { it.onEvent("closed") }
    }

    override fun getState(): ControlChannelState = state

    override fun subscribeEvents(listener: ControlChannelEventListener) {
        listeners += listener
    }

    fun enqueueInbound(frame: DiscoveryControlFrame) {
        inbound.addLast(frame)
    }

    fun sentFrames(): List<DiscoveryControlFrame> = outbound.toList()
}
