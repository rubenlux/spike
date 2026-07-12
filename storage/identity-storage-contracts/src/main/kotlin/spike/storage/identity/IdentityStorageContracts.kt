package spike.storage.identity

import spike.identity.domain.Challenge
import spike.identity.domain.ChallengeId
import spike.identity.domain.ChallengeState
import spike.identity.domain.IdentityEvent
import spike.identity.domain.LocalNodeIdentity
import spike.identity.domain.NodeId
import spike.identity.domain.RemoteNodeIdentity

interface IdentityTransactionManager {
    suspend fun <T> transaction(block: suspend () -> T): T
}

interface LocalIdentityRepository {
    suspend fun getLocalIdentity(): LocalNodeIdentity?

    suspend fun saveLocalIdentity(identity: LocalNodeIdentity)

    suspend fun updateLocalIdentity(identity: LocalNodeIdentity)
}

interface RemoteIdentityRepository {
    suspend fun saveRemoteIdentity(identity: RemoteNodeIdentity)

    suspend fun getRemoteIdentity(nodeId: NodeId): RemoteNodeIdentity?
}

interface ChallengeRepository {
    suspend fun saveChallenge(challenge: Challenge)

    suspend fun getChallenge(challengeId: ChallengeId): Challenge?

    suspend fun updateChallengeState(challengeId: ChallengeId, state: ChallengeState)

    suspend fun expireChallenges(nowEpochMillis: Long)
}

interface IdentityEventRepository {
    suspend fun appendEvent(event: IdentityEvent)

    suspend fun nextSequence(localNodeId: NodeId?): Long

    suspend fun listEvents(localNodeId: NodeId?): List<IdentityEvent>
}
