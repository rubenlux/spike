package spike.secure.session.contracts

import spike.secure.session.domain.SecureRecordType
import spike.secure.session.domain.SecureSessionEvent
import spike.secure.session.domain.SecureSessionSnapshot

interface SecureSessionEventListener {
    fun onEvent(event: SecureSessionEvent)
}

interface SecureSessionCore {
    suspend fun createSession(encounterId: String, remoteNodeId: String): SecureSessionSnapshot

    suspend fun acceptSession(encounterId: String, remoteNodeId: String): SecureSessionSnapshot

    suspend fun processHandshakeFrame(sessionId: String, frame: ByteArray): ByteArray?

    suspend fun getSession(sessionId: String): SecureSessionSnapshot?

    suspend fun getSessionSnapshot(sessionId: String): SecureSessionSnapshot?

    suspend fun encrypt(sessionId: String, recordType: SecureRecordType, plaintext: ByteArray): ByteArray

    suspend fun decrypt(sessionId: String, secureRecordBytes: ByteArray): ByteArray

    suspend fun requestRekey(sessionId: String)

    suspend fun closeSession(sessionId: String, reason: String)

    suspend fun handleTransportLoss(sessionId: String, reason: String)

    suspend fun recover()

    fun subscribeEvents(listener: SecureSessionEventListener)
}

interface SecureSessionTransport {
    suspend fun sendOpaqueFrame(frame: ByteArray)

    suspend fun receiveOpaqueFrame(): ByteArray

    fun close()

    fun getState(): String

    fun subscribeEvents(listener: TransportEventListener)
}

interface TransportEventListener {
    fun onEvent(eventType: String, metadata: Map<String, String>)
}
