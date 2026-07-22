package spike.servicediscovery.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ContractHashGeneratorTest {

    @Test
    fun `generateHash computes deterministic SHA-256 hex string`() {
        val contractId = ContractId("app.mesh.file-index.contract")
        val version = ContractVersion("1.0")
        val methods = listOf(
            ServiceMethodDefinition(
                methodId = MethodId("app.mesh.file-index/search"),
                methodName = "search",
                semantics = RpcMethodSemantics.QUERY,
                requiredCapabilityId = "service.app.mesh.file-index.search.invoke"
            )
        )

        val hash1 = ContractHashGenerator.generateHash(contractId, version, "JSON_SCHEMA", methods)
        val hash2 = ContractHashGenerator.generateHash(contractId, version, "JSON_SCHEMA", methods)

        assertEquals(64, hash1.value.length)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `different versions or methods produce different contract hashes`() {
        val contractId = ContractId("app.mesh.file-index.contract")
        val v1 = ContractVersion("1.0")
        val v2 = ContractVersion("1.1")
        val methods = listOf(
            ServiceMethodDefinition(
                methodId = MethodId("app.mesh.file-index/search"),
                methodName = "search",
                semantics = RpcMethodSemantics.QUERY,
                requiredCapabilityId = "service.app.mesh.file-index.search.invoke"
            )
        )

        val hashV1 = ContractHashGenerator.generateHash(contractId, v1, "JSON_SCHEMA", methods)
        val hashV2 = ContractHashGenerator.generateHash(contractId, v2, "JSON_SCHEMA", methods)

        assertNotEquals(hashV1, hashV2)
    }
}
