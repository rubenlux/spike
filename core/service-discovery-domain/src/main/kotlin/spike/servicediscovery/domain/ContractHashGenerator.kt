package spike.servicediscovery.domain

import java.security.MessageDigest

object ContractHashGenerator {
    private const val DOMAIN_TAG = "OFFLINE_SERVICE_CONTRACT_V1"

    fun generateHash(
        contractId: ContractId,
        version: ContractVersion,
        schemaFormat: String,
        methods: List<ServiceMethodDefinition>
    ): ContractHash {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN_TAG.toByteArray(Charsets.UTF_8))
        digest.update(contractId.value.toByteArray(Charsets.UTF_8))
        digest.update(version.value.toByteArray(Charsets.UTF_8))
        digest.update(schemaFormat.toByteArray(Charsets.UTF_8))

        val sortedMethods = methods.sortedBy { it.methodId.value }
        for (m in sortedMethods) {
            digest.update(m.methodId.value.toByteArray(Charsets.UTF_8))
            digest.update(m.semantics.name.toByteArray(Charsets.UTF_8))
            digest.update(m.requiredCapabilityId.toByteArray(Charsets.UTF_8))
        }

        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return ContractHash(hex)
    }
}
