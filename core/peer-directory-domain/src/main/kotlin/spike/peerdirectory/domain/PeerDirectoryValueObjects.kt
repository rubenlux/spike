package spike.peerdirectory.domain

import java.util.UUID

@JvmInline
value class HumanIdentityId(val value: String) {
    init {
        require(value.isNotBlank()) { "HumanIdentityId cannot be blank" }
    }
    companion object {
        fun generate(): HumanIdentityId = HumanIdentityId("human_${UUID.randomUUID()}")
    }
}

@JvmInline
value class BindingId(val value: String) {
    init {
        require(value.isNotBlank()) { "BindingId cannot be blank" }
    }
    companion object {
        fun generate(): BindingId = BindingId("binding_${UUID.randomUUID()}")
    }
}

@JvmInline
value class VerificationSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "VerificationSessionId cannot be blank" }
    }
    companion object {
        fun generate(): VerificationSessionId = VerificationSessionId("vsession_${UUID.randomUUID()}")
    }
}

@JvmInline
value class VerificationEvidenceId(val value: String) {
    init {
        require(value.isNotBlank()) { "VerificationEvidenceId cannot be blank" }
    }
    companion object {
        fun generate(): VerificationEvidenceId = VerificationEvidenceId("vevidence_${UUID.randomUUID()}")
    }
}

@JvmInline
value class ReplacementOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReplacementOperationId cannot be blank" }
    }
    companion object {
        fun generate(): ReplacementOperationId = ReplacementOperationId("replace_op_${UUID.randomUUID()}")
    }
}

@JvmInline
value class TrustMigrationPlanId(val value: String) {
    init {
        require(value.isNotBlank()) { "TrustMigrationPlanId cannot be blank" }
    }
    companion object {
        fun generate(): TrustMigrationPlanId = TrustMigrationPlanId("migration_plan_${UUID.randomUUID()}")
    }
}

@JvmInline
value class HistoryEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "HistoryEntryId cannot be blank" }
    }
    companion object {
        fun generate(): HistoryEntryId = HistoryEntryId("history_entry_${UUID.randomUUID()}")
    }
}

@JvmInline
value class LocalAlias(val value: String) {
    init {
        require(value.isNotBlank()) { "LocalAlias cannot be blank" }
    }
}

@JvmInline
value class VerificationTranscriptHash(val value: String) {
    init {
        require(value.isNotBlank()) { "VerificationTranscriptHash cannot be blank" }
    }
}

data class ReadableFingerprint(
    val raw: String,
    val formatted: String
) {
    init {
        require(raw.isNotBlank()) { "raw fingerprint cannot be blank" }
        require(formatted.isNotBlank()) { "formatted fingerprint cannot be blank" }
    }
}

data class ShortAuthenticationString(
    val numericCode: String,
    val words: List<String>
) {
    init {
        require(numericCode.length == 6) { "numericCode must be 6 decimal digits" }
        require(words.size == 3) { "words list must contain exactly 3 words" }
    }
}

enum class VerificationMethod {
    QR,
    SAS,
    MANUAL_FINGERPRINT,
    NFC,
    EXTERNAL_TRUSTED_CHANNEL
}

enum class VerificationState {
    CREATED,
    WAITING_REMOTE,
    READY,
    USER_CONFIRMATION_REQUIRED,
    VERIFIED,
    REJECTED,
    FAILED,
    EXPIRED,
    CANCELLED
}

enum class VerificationResult {
    VERIFIED,
    MISMATCH,
    REJECTED,
    FAILED
}

enum class HumanIdentityState {
    ACTIVE,
    ARCHIVED,
    REVOKED
}

enum class BindingState {
    PENDING,
    VERIFIED,
    TRUSTED_UNVERIFIED,
    REVOKED,
    REPLACED,
    ARCHIVED
}

enum class IdentityChangeReason {
    NEW_NODE_ID,
    ALIAS_MISMATCH,
    FINGERPRINT_MISMATCH,
    SUSPECTED_MITM,
    MANUAL_REVOCATION,
    DEVICE_REPLACEMENT
}

enum class TrustEstablishmentMode {
    VERIFIED_QR,
    VERIFIED_SAS,
    VERIFIED_MANUAL_FINGERPRINT,
    UNVERIFIED_TRUST_OVERRIDE
}

enum class DeviceReplacementState {
    CREATED,
    NEW_DEVICE_VERIFICATION_REQUIRED,
    VERIFIED,
    TRUST_MIGRATION_REVIEW,
    CONFIRMED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    FAILED
}

enum class OldDevicePolicy {
    KEEP_OBSERVED,
    REMOVE_TRUST,
    RESTRICT,
    BLOCK
}
