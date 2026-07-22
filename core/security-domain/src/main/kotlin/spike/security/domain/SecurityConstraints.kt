package spike.security.domain

/**
 * Sealed hierarchy of grant constraints supported by SPEC-009.
 */
sealed interface Constraint {
    /** Unique constraint type key for canonical encoding and validation. */
    val typeKey: String
}

data class MessageTypeConstraint(val allowedTypes: Set<String>) : Constraint {
    override val typeKey: String = "MessageTypeConstraint"
}

data class MaximumPayloadBytesConstraint(val maxBytes: Long) : Constraint {
    init { require(maxBytes > 0) { "maxBytes must be positive" } }
    override val typeKey: String = "MaximumPayloadBytesConstraint"
}

data class MaximumObjectBytesConstraint(val maxBytes: Long) : Constraint {
    init { require(maxBytes > 0) { "maxBytes must be positive" } }
    override val typeKey: String = "MaximumObjectBytesConstraint"
}

data class MediaTypeConstraint(val allowedMediaTypes: Set<String>) : Constraint {
    override val typeKey: String = "MediaTypeConstraint"
}

data class MaximumStorageBytesConstraint(val maxBytes: Long) : Constraint {
    init { require(maxBytes > 0) { "maxBytes must be positive" } }
    override val typeKey: String = "MaximumStorageBytesConstraint"
}

data class MaximumOperationsConstraint(val maxOperations: Long) : Constraint {
    init { require(maxOperations > 0) { "maxOperations must be positive" } }
    override val typeKey: String = "MaximumOperationsConstraint"
}

data class MaximumConcurrentOperationsConstraint(val maxConcurrent: Int) : Constraint {
    init { require(maxConcurrent > 0) { "maxConcurrent must be positive" } }
    override val typeKey: String = "MaximumConcurrentOperationsConstraint"
}

data class RateLimitConstraint(
    val dimension: UsageDimension,
    val limit: Long,
    val windowSeconds: Long
) : Constraint {
    init {
        require(limit > 0) { "RateLimit limit must be positive" }
        require(windowSeconds > 0) { "RateLimit windowSeconds must be positive" }
    }
    override val typeKey: String = "RateLimitConstraint"
}

data class TimeWindowConstraint(
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long
) : Constraint {
    init {
        require(endTimeEpochMs >= startTimeEpochMs) { "endTimeEpochMs must be >= startTimeEpochMs" }
    }
    override val typeKey: String = "TimeWindowConstraint"
}

data class ValidUntilConstraint(val validUntilEpochMs: Long) : Constraint {
    override val typeKey: String = "ValidUntilConstraint"
}

data class EncounterConstraint(val encounterId: String) : Constraint {
    init { require(encounterId.isNotBlank()) { "encounterId cannot be blank" } }
    override val typeKey: String = "EncounterConstraint"
}

data class ApplicationNamespaceConstraint(val allowedNamespaces: Set<String>) : Constraint {
    override val typeKey: String = "ApplicationNamespaceConstraint"
}

data class ResourceIdentifierConstraint(val resourceId: String) : Constraint {
    init { require(resourceId.isNotBlank()) { "resourceId cannot be blank" } }
    override val typeKey: String = "ResourceIdentifierConstraint"
}

/** Value Object representing a collection of constraints on a grant. */
data class ConstraintSet(val constraints: List<Constraint> = emptyList()) {
    fun hasConstraintType(typeKey: String): Boolean = constraints.any { it.typeKey == typeKey }

    inline fun <reified T : Constraint> findConstraint(): T? = constraints.filterIsInstance<T>().firstOrNull()
}
