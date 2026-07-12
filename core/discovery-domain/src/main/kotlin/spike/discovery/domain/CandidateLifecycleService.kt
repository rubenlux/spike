package spike.discovery.domain

class CandidateStateMachine {
    private val terminal = setOf(
        DiscoveryCandidateState.AUTHENTICATED,
        DiscoveryCandidateState.REJECTED,
        DiscoveryCandidateState.EXPIRED,
        DiscoveryCandidateState.FAILED
    )

    private val allowed = mapOf(
        DiscoveryCandidateState.DETECTED to setOf(
            DiscoveryCandidateState.COMPATIBLE,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.EXPIRED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.COMPATIBLE to setOf(
            DiscoveryCandidateState.CONNECTING,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.EXPIRED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.CONNECTING to setOf(
            DiscoveryCandidateState.CONTROL_CONNECTED,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.EXPIRED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.CONTROL_CONNECTED to setOf(
            DiscoveryCandidateState.IDENTIFYING,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.IDENTIFYING to setOf(
            DiscoveryCandidateState.IDENTIFIED,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.IDENTIFIED to setOf(
            DiscoveryCandidateState.AUTHENTICATING,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.AUTHENTICATING to setOf(
            DiscoveryCandidateState.AUTHENTICATED,
            DiscoveryCandidateState.REJECTED,
            DiscoveryCandidateState.FAILED
        ),
        DiscoveryCandidateState.AUTHENTICATED to emptySet(),
        DiscoveryCandidateState.REJECTED to emptySet(),
        DiscoveryCandidateState.EXPIRED to emptySet(),
        DiscoveryCandidateState.FAILED to emptySet()
    )

    fun validateTransition(from: DiscoveryCandidateState, to: DiscoveryCandidateState) {
        if (from == to) return
        if (to !in allowed.getValue(from)) {
            throw InvalidCandidateStateTransition(from, to)
        }
    }

    fun isTerminal(state: DiscoveryCandidateState): Boolean = state in terminal
}

class CandidateLifecycleService(
    private val stateMachine: CandidateStateMachine = CandidateStateMachine()
) {
    fun createCompatibleCandidate(
        candidateId: CandidateId,
        advertisement: DiscoveryAdvertisementV1,
        observation: ProximityObservation
    ): DiscoveryCandidate =
        DiscoveryCandidate(
            candidateId = candidateId,
            ephemeralDiscoveryId = advertisement.ephemeralDiscoveryId,
            discoveryVersion = advertisement.discoveryVersion,
            capabilities = advertisement.capabilityBitmap,
            firstSeenAt = observation.observedAt,
            lastSeenAt = observation.observedAt,
            observationCount = 1,
            latestSignalStrength = observation.signalStrength,
            state = DiscoveryCandidateState.COMPATIBLE,
            transportReference = observation.transportReference,
            controlChannelActive = false,
            authenticationSessionId = null,
            failureReason = null
        )

    fun applyObservation(
        candidate: DiscoveryCandidate,
        observation: ProximityObservation
    ): DiscoveryCandidate {
        if (stateMachine.isTerminal(candidate.state)) return candidate
        return candidate.copy(
            lastSeenAt = observation.observedAt,
            observationCount = candidate.observationCount + 1,
            latestSignalStrength = observation.signalStrength ?: candidate.latestSignalStrength,
            transportReference = observation.transportReference
        )
    }

    fun transition(
        candidate: DiscoveryCandidate,
        to: DiscoveryCandidateState,
        failureReason: String? = null,
        authenticationSessionId: AuthenticationSessionId? = candidate.authenticationSessionId,
        controlChannelActive: Boolean = candidate.controlChannelActive
    ): DiscoveryCandidate {
        stateMachine.validateTransition(candidate.state, to)
        return candidate.copy(
            state = to,
            failureReason = failureReason,
            authenticationSessionId = authenticationSessionId,
            controlChannelActive = controlChannelActive
        )
    }

    fun expireIfStale(
        candidate: DiscoveryCandidate,
        now: DiscoveryTimestamp,
        timeoutMillis: Long
    ): DiscoveryCandidate {
        if (stateMachine.isTerminal(candidate.state) || candidate.controlChannelActive) return candidate
        val staleFor = now.monotonicMillis - candidate.lastSeenAt.monotonicMillis
        return if (staleFor >= timeoutMillis) {
            transition(candidate, DiscoveryCandidateState.EXPIRED)
        } else {
            candidate
        }
    }
}

