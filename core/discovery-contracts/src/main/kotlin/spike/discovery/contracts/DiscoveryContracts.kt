package spike.discovery.contracts

import spike.discovery.domain.CandidateId
import spike.discovery.domain.DiscoveryCandidate
import spike.discovery.domain.DiscoveryControlFrame
import spike.discovery.domain.DiscoveryEvent
import spike.discovery.domain.EphemeralDiscoveryId
import spike.discovery.domain.ProximityObservation
import spike.discovery.domain.RawDiscoveryAdvertisement
import spike.discovery.domain.TransportReference
import spike.encounter.domain.Encounter
import spike.encounter.domain.EncounterEvent
import spike.encounter.domain.EncounterId
import spike.identity.domain.IdentityDocument

enum class DiscoveryCommandDisposition {
    APPLIED,
    ALREADY_APPLIED,
    REJECTED
}

data class DiscoveryCommandResult<T>(
    val disposition: DiscoveryCommandDisposition,
    val value: T?,
    val errorCode: String?
)

fun interface AdvertisementProvider {
    suspend fun currentAdvertisement(): RawDiscoveryAdvertisement
}

fun interface ObservationConsumer {
    suspend fun onObservation(observation: ProximityObservation)
}

interface DiscoveryAdvertiser {
    suspend fun start(advertisementProvider: AdvertisementProvider)
    suspend fun updateAdvertisement()
    suspend fun stop()
}

interface DiscoveryScanner {
    suspend fun start(observationConsumer: ObservationConsumer)
    suspend fun stop()
}

enum class ControlChannelState {
    NEW,
    OPEN,
    CLOSED,
    FAILED
}

fun interface ControlChannelEventListener {
    fun onEvent(event: String)
}

interface ControlChannel {
    suspend fun open()
    suspend fun send(frame: DiscoveryControlFrame)
    suspend fun receive(): DiscoveryControlFrame?
    suspend fun close()
    fun getState(): ControlChannelState
    fun subscribeEvents(listener: ControlChannelEventListener)
}

interface ControlChannelFactory {
    suspend fun open(transportReference: TransportReference): ControlChannel
    suspend fun close(channel: ControlChannel)
}

interface DiscoveryCandidateRepository {
    suspend fun save(candidate: DiscoveryCandidate)
    suspend fun get(candidateId: CandidateId): DiscoveryCandidate?
    suspend fun findByEphemeralDiscoveryId(ephemeralDiscoveryId: EphemeralDiscoveryId): DiscoveryCandidate?
    suspend fun activeCandidates(): List<DiscoveryCandidate>
}

interface EncounterRepository {
    suspend fun save(encounter: Encounter)
    suspend fun get(encounterId: EncounterId): Encounter?
    suspend fun activeEncounters(): List<Encounter>
}

interface DiscoveryEventRepository {
    suspend fun append(event: DiscoveryEvent)
}

interface EncounterEventRepository {
    suspend fun append(event: EncounterEvent)
}

interface DiscoveryMetrics {
    fun increment(name: String, tags: Map<String, String> = emptyMap())
    fun timing(name: String, millis: Long, tags: Map<String, String> = emptyMap())
}

object NoopDiscoveryMetrics : DiscoveryMetrics {
    override fun increment(name: String, tags: Map<String, String>) = Unit
    override fun timing(name: String, millis: Long, tags: Map<String, String>) = Unit
}

fun interface DiscoveryEventListener {
    fun onEvent(event: DiscoveryEvent)
}

fun interface EncounterEventListener {
    fun onEvent(event: EncounterEvent)
}

interface DiscoveryCore {
    suspend fun startAdvertising(): DiscoveryCommandResult<Unit>
    suspend fun stopAdvertising(): DiscoveryCommandResult<Unit>
    suspend fun startScanning(): DiscoveryCommandResult<Unit>
    suspend fun stopScanning(): DiscoveryCommandResult<Unit>
    suspend fun getLocalAdvertisement(): RawDiscoveryAdvertisement
    suspend fun acceptObservation(observation: ProximityObservation): DiscoveryCommandResult<DiscoveryCandidate>
    suspend fun getCandidate(candidateId: CandidateId): DiscoveryCandidate?
    suspend fun getActiveCandidates(): List<DiscoveryCandidate>
    suspend fun openControlChannel(candidateId: CandidateId): DiscoveryCommandResult<ControlChannel>
    suspend fun authenticateCandidate(
        candidateId: CandidateId,
        remoteIdentityDocument: IdentityDocument
    ): DiscoveryCommandResult<DiscoveryCandidate>
    suspend fun rejectCandidate(candidateId: CandidateId, reason: String): DiscoveryCommandResult<DiscoveryCandidate>
    suspend fun expireCandidate(candidateId: CandidateId): DiscoveryCommandResult<DiscoveryCandidate>
    suspend fun getEncounter(encounterId: EncounterId): Encounter?
    suspend fun getActiveEncounters(): List<Encounter>
    suspend fun markContactObserved(encounterId: EncounterId, observation: ProximityObservation): DiscoveryCommandResult<Encounter>
    suspend fun markContactLost(encounterId: EncounterId): DiscoveryCommandResult<Encounter>
    suspend fun closeEncounter(encounterId: EncounterId, reason: String): DiscoveryCommandResult<Encounter>
    suspend fun recover(): DiscoveryCommandResult<Unit>
    fun subscribeDiscoveryEvents(listener: DiscoveryEventListener)
    fun subscribeEncounterEvents(listener: EncounterEventListener)
}

