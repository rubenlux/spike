package spike.identity.testing

import spike.identity.domain.KeyAlgorithm
import spike.identity.domain.PublicKeyBytes

object IdentityFixtures {
    fun publicKey(byte: Byte = 1): PublicKeyBytes =
        PublicKeyBytes(KeyAlgorithm.ED25519, ByteArray(32) { byte })
}
