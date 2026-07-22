package spike.security.application

import spike.identity.domain.NodeId
import spike.security.domain.*
import spike.security.storage.SecurityRepository

/**
 * Normative Authorization Policy Evaluation Engine per SPEC-009 Section 40.
 */
class PolicyEvaluationEngine(
    private val repository: SecurityRepository,
    private val rateLimiter: RateLimiter,
    private val quotaManager: QuotaManager
) {
    suspend fun evaluate(
        request: AuthorizationRequest,
        nowEpochMs: Long
    ): AuthorizationDecision {
        // Step 1: Strict Input Validation & Authenticated Peer Check
        require(request.requestedUsage >= 0) { "requestedUsage cannot be negative" }

        // Step 2: Peer Security Profile Lookup & BLOCKED Check
        val peerProfile = repository.getPeerProfile(request.peerNodeId)
            ?: PeerSecurityProfile(
                peerNodeId = request.peerNodeId,
                trustState = TrustState.UNKNOWN,
                firstSeenAt = nowEpochMs,
                lastSeenAt = nowEpochMs,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )

        if (peerProfile.trustState == TrustState.BLOCKED) {
            return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_PEER_BLOCKED, peerProfile, nowEpochMs)
        }

        // Step 3: Capability Registry Lookup & UNKNOWN Check
        val capDef = repository.getCapability(request.capabilityId)
            ?: return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_UNKNOWN_CAPABILITY, peerProfile, nowEpochMs)

        // Step 4: Load Policy Snapshot
        val policySnapshot = PolicySnapshot(
            policyId = PolicyId("DEFAULT"),
            policyVersion = PolicyVersion(1L),
            trustVersion = peerProfile.trustVersion,
            revocationVersion = peerProfile.revocationVersion,
            createdAt = nowEpochMs
        )

        // Step 5: Load Matching DENY Grants Check
        val matchingGrants = repository.findMatchingGrants(request.peerNodeId, request.capabilityId)
        val denyGrants = matchingGrants.filter { it.effect == GrantEffect.DENY && it.state == GrantState.ACTIVE && !it.isExpiredAt(nowEpochMs) }
        if (denyGrants.isNotEmpty()) {
            return buildDecision(
                request = request,
                effect = AuthorizationEffect.DENY,
                reason = AuthorizationReason.DENIED_EXPLICIT_GRANT,
                peerProfile = peerProfile,
                nowEpochMs = nowEpochMs,
                policySnapshot = policySnapshot,
                matchedDenyGrants = denyGrants.map { it.grantId }
            )
        }

        // Step 6: Load Matching ALLOW Grants Check
        val allowGrants = matchingGrants.filter { it.effect == GrantEffect.ALLOW && it.state != GrantState.REVOKED && it.state != GrantState.DISABLED }
        if (allowGrants.isEmpty()) {
            return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_NO_MATCHING_GRANT, peerProfile, nowEpochMs, policySnapshot)
        }

        val nonExpiredAllowGrants = allowGrants.filter { !it.isExpiredAt(nowEpochMs) }
        if (nonExpiredAllowGrants.isEmpty()) {
            return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_GRANT_EXPIRED, peerProfile, nowEpochMs, policySnapshot)
        }

        val availableAllowGrants = nonExpiredAllowGrants.filter { !it.isExhausted() }
        if (availableAllowGrants.isEmpty()) {
            return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_GRANT_EXHAUSTED, peerProfile, nowEpochMs, policySnapshot)
        }

        // Step 7: Evaluate Trust Requirements
        if (capDef.requiresTrustedPeer && peerProfile.trustState != TrustState.TRUSTED) {
            return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_PEER_NOT_TRUSTED, peerProfile, nowEpochMs, policySnapshot)
        }

        // Step 8: Evaluate Constraints across matching ALLOW grants
        var selectedGrant: CapabilityGrant? = null
        val failedConstraintDetails = mutableListOf<String>()

        for (grant in availableAllowGrants) {
            val constraintReason = evaluateConstraints(grant.constraints, request, nowEpochMs)
            if (constraintReason == null) {
                selectedGrant = grant
                break
            } else {
                failedConstraintDetails.add("${grant.grantId.value}: $constraintReason")
            }
        }

        if (selectedGrant == null) {
            val primaryReason = failedConstraintDetails.firstOrNull()?.let { parseConstraintReason(it) }
                ?: AuthorizationReason.DENIED_POLICY_CONFLICT
            return buildDecision(
                request = request,
                effect = AuthorizationEffect.DENY,
                reason = primaryReason,
                peerProfile = peerProfile,
                nowEpochMs = nowEpochMs,
                policySnapshot = policySnapshot,
                evaluatedConstraints = failedConstraintDetails
            )
        }

        // Step 9: Evaluate Rate Limits
        selectedGrant.constraints.findConstraint<RateLimitConstraint>()?.let { rateLimit ->
            val rateLimitReason = rateLimiter.checkAndReserveRateLimit(
                peerNodeId = request.peerNodeId,
                capabilityId = request.capabilityId,
                constraint = rateLimit,
                requestedUsage = request.requestedUsage,
                nowEpochMs = nowEpochMs
            )
            if (rateLimitReason != null) {
                return buildDecision(request, AuthorizationEffect.DENY, rateLimitReason, peerProfile, nowEpochMs, policySnapshot)
            }
        }

        // Step 10: Evaluate Quotas
        selectedGrant.constraints.findConstraint<MaximumStorageBytesConstraint>()?.let { storageQuota ->
            val quotaReason = quotaManager.checkAndReserveQuota(
                peerNodeId = request.peerNodeId,
                capabilityId = request.capabilityId,
                dimension = UsageDimension.DURABLE_OBJECT_BYTES,
                requestedUsage = request.requestedUsage,
                limit = storageQuota.maxBytes,
                nowEpochMs = nowEpochMs
            )
            if (quotaReason != null) {
                return buildDecision(request, AuthorizationEffect.DENY, quotaReason, peerProfile, nowEpochMs, policySnapshot)
            }
        }

        // Step 11: Evaluate Concurrency
        selectedGrant.constraints.findConstraint<MaximumConcurrentOperationsConstraint>()?.let { concurrencyConstraint ->
            val activeLeases = repository.listActiveLeasesForPeer(request.peerNodeId)
                .filter { it.capabilityId == request.capabilityId }
            if (activeLeases.size >= concurrencyConstraint.maxConcurrent) {
                return buildDecision(request, AuthorizationEffect.DENY, AuthorizationReason.DENIED_CONCURRENCY_LIMIT, peerProfile, nowEpochMs, policySnapshot)
            }
        }

        // Step 12: Create Decision ALLOW
        return buildDecision(
            request = request,
            effect = AuthorizationEffect.ALLOW,
            reason = AuthorizationReason.ALLOWED_BY_GRANT,
            peerProfile = peerProfile,
            nowEpochMs = nowEpochMs,
            policySnapshot = policySnapshot,
            matchedAllowGrants = listOf(selectedGrant.grantId)
        )
    }

    private fun evaluateConstraints(
        constraints: ConstraintSet,
        request: AuthorizationRequest,
        nowEpochMs: Long
    ): AuthorizationReason? {
        constraints.findConstraint<MessageTypeConstraint>()?.let {
            val msgType = request.context.messageType
            if (msgType == null || msgType !in it.allowedTypes) return AuthorizationReason.DENIED_MESSAGE_TYPE
        }

        constraints.findConstraint<MaximumPayloadBytesConstraint>()?.let {
            val payloadBytes = request.context.payloadBytes ?: request.requestedUsage
            if (payloadBytes > it.maxBytes) return AuthorizationReason.DENIED_PAYLOAD_SIZE
        }

        constraints.findConstraint<MaximumObjectBytesConstraint>()?.let {
            val objectBytes = request.context.objectBytes ?: request.requestedUsage
            if (objectBytes > it.maxBytes) return AuthorizationReason.DENIED_OBJECT_SIZE
        }

        constraints.findConstraint<MediaTypeConstraint>()?.let {
            val mediaType = request.context.mediaType
            if (mediaType == null || mediaType !in it.allowedMediaTypes) return AuthorizationReason.DENIED_MEDIA_TYPE
        }

        constraints.findConstraint<TimeWindowConstraint>()?.let {
            if (nowEpochMs < it.startTimeEpochMs || nowEpochMs > it.endTimeEpochMs) return AuthorizationReason.DENIED_TIME_WINDOW
        }

        constraints.findConstraint<ValidUntilConstraint>()?.let {
            if (nowEpochMs > it.validUntilEpochMs) return AuthorizationReason.DENIED_GRANT_EXPIRED
        }

        constraints.findConstraint<EncounterConstraint>()?.let {
            if (request.context.encounterId != it.encounterId) return AuthorizationReason.DENIED_INVALID_CONTEXT
        }

        constraints.findConstraint<ApplicationNamespaceConstraint>()?.let {
            val ns = request.context.applicationNamespace
            if (ns == null || ns !in it.allowedNamespaces) return AuthorizationReason.DENIED_APPLICATION_NAMESPACE
        }

        constraints.findConstraint<ResourceIdentifierConstraint>()?.let {
            if (request.resourceId != it.resourceId) return AuthorizationReason.DENIED_RESOURCE_SCOPE
        }

        return null
    }

    private fun parseConstraintReason(detail: String): AuthorizationReason {
        return when {
            "DENIED_MESSAGE_TYPE" in detail -> AuthorizationReason.DENIED_MESSAGE_TYPE
            "DENIED_PAYLOAD_SIZE" in detail -> AuthorizationReason.DENIED_PAYLOAD_SIZE
            "DENIED_OBJECT_SIZE" in detail -> AuthorizationReason.DENIED_OBJECT_SIZE
            "DENIED_MEDIA_TYPE" in detail -> AuthorizationReason.DENIED_MEDIA_TYPE
            "DENIED_TIME_WINDOW" in detail -> AuthorizationReason.DENIED_TIME_WINDOW
            "DENIED_APPLICATION_NAMESPACE" in detail -> AuthorizationReason.DENIED_APPLICATION_NAMESPACE
            "DENIED_RESOURCE_SCOPE" in detail -> AuthorizationReason.DENIED_RESOURCE_SCOPE
            else -> AuthorizationReason.DENIED_POLICY_CONFLICT
        }
    }

    private fun buildDecision(
        request: AuthorizationRequest,
        effect: AuthorizationEffect,
        reason: AuthorizationReason,
        peerProfile: PeerSecurityProfile,
        nowEpochMs: Long,
        policySnapshot: PolicySnapshot = PolicySnapshot(createdAt = nowEpochMs),
        matchedAllowGrants: List<GrantId> = emptyList(),
        matchedDenyGrants: List<GrantId> = emptyList(),
        evaluatedConstraints: List<String> = emptyList()
    ): AuthorizationDecision {
        return AuthorizationDecision(
            requestId = request.requestId,
            peerNodeId = request.peerNodeId,
            capabilityId = request.capabilityId,
            effect = effect,
            reasonCode = reason,
            matchedGrantIds = matchedAllowGrants,
            matchedDenyGrantIds = matchedDenyGrants,
            evaluatedConstraints = evaluatedConstraints,
            policyId = policySnapshot.policyId,
            policyVersion = policySnapshot.policyVersion,
            trustVersion = peerProfile.trustVersion,
            revocationVersion = peerProfile.revocationVersion,
            decidedAt = nowEpochMs
        )
    }
}
