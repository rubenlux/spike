package spike.discovery.application

import java.util.UUID
import spike.discovery.contracts.ControlChannel
import spike.discovery.contracts.ControlChannelFactory
import spike.discovery.contracts.DiscoveryAdvertiser
import spike.discovery.contracts.DiscoveryCandidateRepository
import spike.discovery.contracts.DiscoveryCommandDisposition
import spike.discovery.contracts.DiscoveryCommandResult
import spike.discovery.contracts.DiscoveryCore
import spike.discovery.contracts.DiscoveryEventListener
import spike.discovery.contracts.DiscoveryEventRepository
import spike.discovery.contracts.DiscoveryMetrics
import spike.discovery.contracts.DiscoveryScanner
import spike.discovery.contracts.EncounterEventListener
import spike.discovery.contracts.EncounterEventRepository
import spike.discovery.contracts.EncounterRepository
import spike.discovery.contracts.NoopDiscoveryMetrics
import spike.discovery.domain.AdvertisementService
import spike.discovery.domain.CandidateId
import spike.discovery.domain.CandidateLifecycleService
import spike.discovery.domain.CandidateNotFound
import spike.discovery.domain.ControlConnectionFailure
import spike.discovery.domain.DiscoveryCandidate
import spike.discovery.domain.DiscoveryCandidateState
import spike.discovery.domain.DiscoveryEvent
import spike.discovery.domain.DiscoveryEventType
import spike.discovery.domain.DiscoveryIdGenerator
import spike.discovery.domain.DiscoveryTimestamp
import spike.discovery.domain.EphemeralIdService
import spike.discovery.domain.MonotonicClock
import spike.discovery.domain.ProximityObservation
import spike.discovery.domain.RawDiscoveryAdvertisement
import spike.encounter.domain.Encounter
import spike.encounter.domain.EncounterEvent
import spike.encounter.domain.EncounterEventType
import spike.encounter.domain.EncounterId
import spike.encounter.domain.EncounterLifecycleService
import spike.encounter.domain.EncounterState
import spike.identity.contracts.IdentityCore
import spike.identity.contracts.VerificationResult
import spike.identity.domain.IdentityDocument

data class DiscoveryRuntimeConfig(
    val candidateExpirationTimeoutMillis: Long = 2 * 60 * 1000L,
    val contactLossGracePeriodMillis: Long = 30 * 1000L
)

class DiscoveryCoreService(
    private val advertiser: DiscoveryAdvertiser?,
    private val scanner: DiscoveryScanner?,
    private val controlChannelFactory: ControlChannelFactory?,
    private val identityCore: IdentityCore,
    private val advertisementService: AdvertisementService,
    private val ephemeralIdService: EphemeralIdService,
    private val candidateRepository: DiscoveryCandidateRepository,
    private val encounterRepository: EncounterRepository,
    private val discoveryEventRepository: DiscoveryEventRepository,
    private val encounterEventRepository: EncounterEventRepository,
    private val clock: MonotonicClock,
    private val candidateLifecycleService: CandidateLifecycleService = CandidateLifecycleService(),
    private val encounterLifecycleService: EncounterLifecycleService = EncounterLifecycleService(),
    private val metrics: DiscoveryMetrics = NoopDiscoveryMetrics,
    private val config: DiscoveryRuntimeConfig = DiscoveryRuntimeConfig()
) : DiscoveryCore {
    private val discoveryListeners = mutableListOf<DiscoveryEventListener>()
    private val encounterListeners = mutableListOf<EncounterEventListener>()
    private var sequenceNumber = 0L

    override suspend fun startAdvertising(): DiscoveryCommandResult<Unit> =
        applied {
            advertiser?.start { getLocalAdvertisement() }
            publishDiscovery(DiscoveryEventType.ADVERTISING_STARTED)
        }

    override suspend fun stopAdvertising(): DiscoveryCommandResult<Unit> =
        applied {
            advertiser?.stop()
            publishDiscovery(DiscoveryEventType.ADVERTISING_STOPPED)
        }

    override suspend fun startScanning(): DiscoveryCommandResult<Unit> =
        applied {
            scanner?.start { acceptObservation(it) }
            publishDiscovery(DiscoveryEventType.SCANNING_STARTED)
        }

    override suspend fun stopScanning(): DiscoveryCommandResult<Unit> =
        applied {
            scanner?.stop()
            publishDiscovery(DiscoveryEventType.SCANNING_STOPPED)
        }

    override suspend fun getLocalAdvertisement(): RawDiscoveryAdvertisement {
        val identity = ephemeralIdService.currentIdentity()
        return advertisementService.encode(advertisementService.createAdvertisement(identity.ephemeralDiscoveryId))
    }

    override suspend fun acceptObservation(
        observation: ProximityObservation
    ): DiscoveryCommandResult<DiscoveryCandidate> =
        try {
            publishDiscovery(DiscoveryEventType.ADVERTISEMENT_RECEIVED, metadata = mapOf("transport" to observation.transportReference.value))
            val advertisement = advertisementService.validate(observation.rawAdvertisement)
            val existing = candidateRepository.findByEphemeralDiscoveryId(advertisement.ephemeralDiscoveryId)
            val candidate = if (existing == null) {
                metrics.increment("discovery_candidates_created_total")
                candidateLifecycleService.createCompatibleCandidate(
                    candidateId = DiscoveryIdGenerator.candidateId(),
                    advertisement = advertisement,
                    observation = observation
                )
            } else {
                candidateLifecycleService.applyObservation(existing, observation)
            }
            candidateRepository.save(candidate)
            metrics.timing(
                "discovery_time_to_candidate_ms",
                now().monotonicMillis - observation.observedAt.monotonicMillis
            )
            publishDiscovery(
                if (existing == null) DiscoveryEventType.CANDIDATE_CREATED else DiscoveryEventType.CANDIDATE_UPDATED,
                candidate.candidateId
            )
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, candidate, null)
        } catch (error: Exception) {
            metrics.increment("discovery_advertisements_rejected_total", mapOf("error" to (error.message ?: "unknown")))
            publishDiscovery(DiscoveryEventType.ADVERTISEMENT_REJECTED, metadata = mapOf("error" to (error.message ?: "unknown")))
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }

    override suspend fun getCandidate(candidateId: CandidateId): DiscoveryCandidate? =
        candidateRepository.get(candidateId)

    override suspend fun getActiveCandidates(): List<DiscoveryCandidate> =
        candidateRepository.activeCandidates()

    override suspend fun openControlChannel(candidateId: CandidateId): DiscoveryCommandResult<ControlChannel> =
        try {
            val candidate = candidateRepository.get(candidateId) ?: throw CandidateNotFound(candidateId)
            val factory = controlChannelFactory ?: throw ControlConnectionFailure("No control channel factory configured")
            val connecting = candidateLifecycleService.transition(candidate, DiscoveryCandidateState.CONNECTING)
            candidateRepository.save(connecting)
            val channel = factory.open(candidate.transportReference)
            val connected = candidateLifecycleService.transition(
                connecting,
                DiscoveryCandidateState.CONTROL_CONNECTED,
                controlChannelActive = true
            )
            candidateRepository.save(connected)
            publishDiscovery(DiscoveryEventType.CANDIDATE_CONTROL_CONNECTED, candidateId)
            metrics.increment("discovery_control_connections_total")
            metrics.timing(
                "discovery_time_to_control_connected_ms",
                now().monotonicMillis - connected.firstSeenAt.monotonicMillis
            )
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, channel, null)
        } catch (error: Exception) {
            metrics.increment("discovery_control_connection_failures_total")
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }

    override suspend fun authenticateCandidate(
        candidateId: CandidateId,
        remoteIdentityDocument: IdentityDocument
    ): DiscoveryCommandResult<DiscoveryCandidate> {
        return try {
            val candidate = candidateRepository.get(candidateId) ?: throw CandidateNotFound(candidateId)
            val identifying = candidateLifecycleService.transition(candidate, DiscoveryCandidateState.IDENTIFYING)
            candidateRepository.save(identifying)
            val validation = identityCore.validateIdentityDocument(remoteIdentityDocument)
            if (validation != VerificationResult.VALID) {
                val rejected = candidateLifecycleService.transition(
                    identifying,
                    DiscoveryCandidateState.REJECTED,
                    failureReason = validation.name
                )
                candidateRepository.save(rejected)
                metrics.increment("discovery_identity_validation_failures_total")
                return DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, rejected, validation.name)
            }

            val identified = candidateLifecycleService.transition(identifying, DiscoveryCandidateState.IDENTIFIED)
            candidateRepository.save(identified)
            publishDiscovery(DiscoveryEventType.CANDIDATE_IDENTIFIED, candidateId)

            DiscoveryCommandResult(
                DiscoveryCommandDisposition.REJECTED,
                identified,
                "MutualAuthenticationEvidenceRequired"
            )
        } catch (error: Exception) {
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }
    }

    override suspend fun rejectCandidate(
        candidateId: CandidateId,
        reason: String
    ): DiscoveryCommandResult<DiscoveryCandidate> =
        transitionCandidate(candidateId, DiscoveryCandidateState.REJECTED, reason)

    override suspend fun expireCandidate(candidateId: CandidateId): DiscoveryCommandResult<DiscoveryCandidate> =
        transitionCandidate(candidateId, DiscoveryCandidateState.EXPIRED, null)

    override suspend fun getEncounter(encounterId: EncounterId): Encounter? =
        encounterRepository.get(encounterId)

    override suspend fun getActiveEncounters(): List<Encounter> =
        encounterRepository.activeEncounters()

    override suspend fun markContactObserved(
        encounterId: EncounterId,
        observation: ProximityObservation
    ): DiscoveryCommandResult<Encounter> {
        return try {
            val encounter = encounterRepository.get(encounterId)
                ?: return DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, "EncounterNotFound")
            val updated = encounterLifecycleService.recordContact(encounter, observation)
            encounterRepository.save(updated)
            publishEncounter(EncounterEventType.CONTACT_OBSERVED, encounterId)
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, updated, null)
        } catch (error: Exception) {
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }
    }

    override suspend fun markContactLost(encounterId: EncounterId): DiscoveryCommandResult<Encounter> {
        return try {
            val encounter = encounterRepository.get(encounterId)
                ?: return DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, "EncounterNotFound")
            val now = now()
            val staleForMillis = now.monotonicMillis - encounter.lastContactAt.monotonicMillis
            val updated = if (staleForMillis < config.contactLossGracePeriodMillis) {
                when (encounter.state) {
                    EncounterState.ACTIVE -> encounterLifecycleService.degrade(encounter)
                    EncounterState.DEGRADED -> encounter
                    else -> encounterLifecycleService.markContactLost(encounter, now)
                }
            } else {
                encounterLifecycleService.markContactLost(encounter, now)
            }
            encounterRepository.save(updated)
            publishEncounter(
                if (updated.state == EncounterState.DEGRADED) EncounterEventType.ENCOUNTER_DEGRADED else EncounterEventType.CONTACT_LOST,
                encounterId
            )
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, updated, null)
        } catch (error: Exception) {
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }
    }

    override suspend fun closeEncounter(encounterId: EncounterId, reason: String): DiscoveryCommandResult<Encounter> {
        return try {
            val encounter = encounterRepository.get(encounterId)
                ?: return DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, "EncounterNotFound")
            val updated = encounterLifecycleService.close(encounter, now())
            encounterRepository.save(updated)
            publishEncounter(EncounterEventType.ENCOUNTER_CLOSED, encounterId, mapOf("reason" to reason))
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, updated, null)
        } catch (error: Exception) {
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }
    }

    override suspend fun recover(): DiscoveryCommandResult<Unit> =
        applied {
            publishDiscovery(DiscoveryEventType.RECOVERY_STARTED)
            getActiveCandidates().forEach { candidate ->
                val expired = candidateLifecycleService.expireIfStale(candidate, now(), 0)
                candidateRepository.save(expired.copy(controlChannelActive = false))
            }
            getActiveEncounters().forEach { encounter ->
                val updated = when (encounter.state) {
                    EncounterState.CREATING -> encounterLifecycleService.fail(encounter, now())
                    EncounterState.ACTIVE,
                    EncounterState.DEGRADED -> encounterLifecycleService.markContactLost(encounter, now())
                    else -> encounter
                }
                encounterRepository.save(updated)
                publishEncounter(
                    if (updated.state == EncounterState.FAILED) EncounterEventType.ENCOUNTER_FAILED else EncounterEventType.CONTACT_LOST,
                    encounter.encounterId
                )
            }
            publishDiscovery(DiscoveryEventType.RECOVERY_COMPLETED)
        }

    override fun subscribeDiscoveryEvents(listener: DiscoveryEventListener) {
        discoveryListeners += listener
    }

    override fun subscribeEncounterEvents(listener: EncounterEventListener) {
        encounterListeners += listener
    }

    private suspend fun transitionCandidate(
        candidateId: CandidateId,
        state: DiscoveryCandidateState,
        reason: String?
    ): DiscoveryCommandResult<DiscoveryCandidate> =
        try {
            val candidate = candidateRepository.get(candidateId) ?: throw CandidateNotFound(candidateId)
            val updated = candidateLifecycleService.transition(candidate, state, reason)
            candidateRepository.save(updated)
            publishDiscovery(
                when (state) {
                    DiscoveryCandidateState.REJECTED -> DiscoveryEventType.CANDIDATE_REJECTED
                    DiscoveryCandidateState.EXPIRED -> DiscoveryEventType.CANDIDATE_EXPIRED
                    else -> DiscoveryEventType.CANDIDATE_UPDATED
                },
                candidateId
            )
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, updated, null)
        } catch (error: Exception) {
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }

    private suspend fun applied(block: suspend () -> Unit): DiscoveryCommandResult<Unit> =
        try {
            block()
            DiscoveryCommandResult(DiscoveryCommandDisposition.APPLIED, Unit, null)
        } catch (error: Exception) {
            DiscoveryCommandResult(DiscoveryCommandDisposition.REJECTED, null, error.javaClass.simpleName)
        }

    private fun now(): DiscoveryTimestamp = DiscoveryTimestamp(clock.nowMillis())

    private suspend fun publishDiscovery(
        eventType: DiscoveryEventType,
        candidateId: CandidateId? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val event = DiscoveryEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            candidateId = candidateId,
            occurredAt = now(),
            sequenceNumber = ++sequenceNumber,
            metadata = metadata
        )
        discoveryEventRepository.append(event)
        discoveryListeners.forEach { it.onEvent(event) }
    }

    private suspend fun publishEncounter(
        eventType: EncounterEventType,
        encounterId: EncounterId,
        metadata: Map<String, String> = emptyMap()
    ) {
        val event = EncounterEvent(
            eventId = UUID.randomUUID().toString(),
            encounterId = encounterId,
            eventType = eventType,
            occurredAt = now(),
            sequenceNumber = ++sequenceNumber,
            metadata = metadata
        )
        encounterEventRepository.append(event)
        encounterListeners.forEach { it.onEvent(event) }
    }
}
