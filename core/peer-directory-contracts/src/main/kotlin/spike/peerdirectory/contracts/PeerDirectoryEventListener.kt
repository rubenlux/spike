package spike.peerdirectory.contracts

import spike.identity.domain.NodeId
import spike.peerdirectory.domain.*

interface PeerDirectoryEventListener {
    fun onPeerObserved(entry: PeerDirectoryEntry) {}
    fun onLocalAliasChanged(peerNodeId: NodeId, oldAlias: LocalAlias?, newAlias: LocalAlias?) {}
    fun onVerificationStarted(session: VerificationSession) {}
    fun onVerificationCompleted(evidence: VerificationEvidence) {}
    fun onVerificationFailed(sessionId: VerificationSessionId, reason: String) {}
    fun onTrustEstablished(peerNodeId: NodeId, mode: TrustEstablishmentMode) {}
    fun onBindingCreated(binding: HumanNodeBinding) {}
    fun onBindingRevoked(binding: HumanNodeBinding, reason: String) {}
    fun onIdentityChangeDetected(peerNodeId: NodeId, reason: IdentityChangeReason) {}
    fun onDeviceReplacementStateChanged(operation: DeviceReplacementOperation) {}
}
