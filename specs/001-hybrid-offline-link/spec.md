# SPEC-001 - Spike-001: Hybrid Offline Link

**Estado**: Draft  
**Tipo**: Technical Spike / Proof of Feasibility  
**Prioridad**: P0 - Fundacional  
**Plataforma objetivo**: Android  
**Conectividad**: 100% offline  
**Transportes experimentales**: Bluetooth Low Energy (BLE) + Wi-Fi Direct  
**Dependencias externas**: Ninguna  
**Servidor requerido**: No  
**Internet requerido**: No  
**Origen**: Solicitud adjunta del usuario, normalizada como especificacion de repositorio.

## 1. Proposito

Validar experimentalmente si dos smartphones Android reales, sin Internet ni infraestructura externa, pueden descubrirse mediante BLE, negociar un enlace Wi-Fi Direct, transferir un objeto grande de forma fragmentada, sobrevivir a interrupciones y reanudar la transferencia desde el progreso verificado.

Esta SPEC no define el protocolo definitivo del proyecto. Su objetivo es producir evidencia tecnica, mediciones, restricciones reales de Android y decisiones arquitectonicas fundamentadas.

## 2. Hipotesis Principal

Es tecnicamente viable construir un enlace hibrido offline entre smartphones Android donde:

1. BLE actua como mecanismo de descubrimiento y canal inicial de coordinacion.
2. Los dispositivos intercambian capacidades y metadatos minimos mediante BLE.
3. Cuando existe suficiente volumen de datos pendiente, ambos acuerdan elevar la sesion a Wi-Fi Direct.
4. Wi-Fi Direct establece un canal de transferencia de alto ancho de banda sin Internet, router, servidor ni infraestructura externa.
5. El objeto transferido se divide en chunks independientes y verificables.
6. El receptor persiste localmente el estado de la transferencia.
7. Una interrupcion no invalida los chunks ya recibidos y verificados.
8. En un encuentro posterior, ambos peers determinan que chunks faltan.
9. La transferencia continua solo desde los chunks faltantes.
10. El receptor reconstruye el objeto completo y verifica su integridad final.

## 3. Pregunta de Validacion

Pueden dos smartphones Android, sin Internet ni infraestructura externa, descubrirse mediante BLE, negociar un enlace Wi-Fi Direct, transferir un objeto grande de forma fragmentada, sobrevivir a una interrupcion de conectividad y continuar posteriormente desde el progreso verificado?

El Spike se considera exitoso solo si la respuesta puede demostrarse fisicamente usando dispositivos Android reales.

## 4. Principios No Negociables

- **Offline absoluto**: sin Internet, datos moviles, router, hotspot externo, servidor, API remota, cloud ni coordinacion externa.
- **Dispositivos reales**: la validacion final requiere al menos dos smartphones Android fisicos.
- **Responsabilidades separadas**: BLE se usa para descubrimiento, senalizacion, capacidades y negociacion; Wi-Fi Direct se usa para bulk transfer.
- **Transferencia independiente del transporte**: la transferencia logica debe sobrevivir a la desaparicion de enlaces fisicos.
- **No reiniciar desde cero**: chunks recibidos, persistidos y verificados no se retransfieren salvo corrupcion.
- **Verificacion antes de confianza**: ningun chunk se marca como recibido hasta estar persistido, hasheado y registrado como disponible.

## 5. Fuera de Alcance

No se implementaran mensajeria de usuario, grupos, contactos, perfiles, chat, routing multi-hop, DTN, gossip, store-carry-forward con tres o mas nodos, Internet fallback, servidores relay, cloud, cuentas centralizadas, blockchain, iOS, multiplataforma, cifrado extremo a extremo definitivo, identidad definitiva, anonimato, proteccion Sybil completa ni produccion.

## 6. Escenario Obligatorio de Validacion

```text
DEVICE A: Sender
DEVICE B: Receiver

Internet: OFF
Mobile Data: OFF
External Wi-Fi: OFF
External Router: NONE
External Server: NONE
BLE: AVAILABLE
Wi-Fi Direct: AVAILABLE
```

Flujo minimo:

```text
DEVICE A                            DEVICE B

    |---- BLE ADVERTISEMENT -------->|
    |<--- BLE DISCOVERY -------------|
    |<--- CAPABILITY EXCHANGE ------>|
    |<--- SESSION NEGOTIATION ------>|
    |     UPGRADE_TO_WIFI_DIRECT     |
    |<========== WIFI DIRECT =======>|
    |---- MANIFEST ----------------->|
    |---- CHUNK 0001 --------------->|
    |---- CHUNK 0002 --------------->|
    |---- CHUNK 0003 --------------->|
    |---- CHUNK 0004 --------X       |
    |          CONNECTION LOST       |

              TIME PASSES

    |---- BLE REDISCOVERY ---------->|
    |<--- TRANSFER IDENTIFICATION -->|
    |<--- MISSING CHUNKS EXCHANGE -->|
    |     UPGRADE_TO_WIFI_DIRECT     |
    |<========== WIFI DIRECT =======>|
    |---- CHUNK 0004 --------------->|
    |---- CHUNK 0005 --------------->|
    |---- FINAL CHUNK -------------->|
    |<--- COMPLETE ACK --------------|

         FINAL OBJECT HASH VERIFIED
```

## 7. Arquitectura Experimental Minima

```text
apps/android-spike/
  Minimal Android UI: local node, discovery, transfer controls, metrics export

core/transfer/
  Object manifest, chunking, hash verification, persistent progress, resume

transports/ble/
  Advertise, scan, GATT/control signaling, capability exchange

transports/wifi-direct/
  Group formation, endpoint resolution, socket transport, bulk transfer

storage/
  Durable manifests, transfers, verified chunks, metrics

observability/
  Structured events, metrics, local export
```

## 8. Modelo Conceptual Minimo

### Node

- `localNodeId`
- `protocolVersion`
- `capabilities`
- `sessionNonce`

### Peer

- `peerId`
- `protocolVersion`
- `capabilities`
- `bleAddressOrHandle`
- `lastSeenAt`
- `connectionState`

### TransferObject

- `objectId`
- `totalSize`
- `chunkSize`
- `chunkCount`
- `objectHash`
- `manifestVersion`

### Chunk

- `objectId`
- `chunkIndex`
- `offset`
- `length`
- `chunkHash`
- `payload`

### Transfer

- `transferId`
- `objectId`
- `senderNodeId`
- `receiverNodeId`
- `state`
- `createdAt`
- `updatedAt`
- `completedAt`
- `bytesVerified`
- `chunksVerified`

## 9. Identificacion del Objeto

Para el Spike:

```text
objectHash = SHA-256(fileBytes)
objectId = objectHash
```

Esto permite identificar contenido, detectar objetos ya existentes, verificar integridad final, reanudar transferencias y evitar duplicacion experimental.

## 10. Chunking y Manifest

Tamanho inicial configurable:

```text
DEFAULT_CHUNK_SIZE = 1 MiB
```

Cada chunk contiene `objectId`, `chunkIndex`, `offset`, `length` y `SHA-256(chunkBytes)`. Los chunks pueden recibirse fuera de orden, verificarse individualmente, persistirse individualmente, solicitarse nuevamente y sobrevivir reinicios.

El receptor persiste el manifest antes de aceptar chunks:

```text
Manifest
  manifestVersion
  objectId
  totalSize
  chunkSize
  chunkCount
  objectHash
  chunks:
    index
    offset
    length
    hash
```

## 11. Persistencia

Deben persistirse localmente manifests, transfers, chunks verificados, estado de completitud, metricas basicas y timestamps relevantes. El cierre de la app o reinicio del dispositivo no debe destruir progreso verificado.

## 12. Maquina de Estados

Estados minimos:

```text
CREATED
DISCOVERING
PEER_FOUND
NEGOTIATING
UPGRADING_TRANSPORT
TRANSFERRING
INTERRUPTED
WAITING_FOR_PEER
RESUMING
VERIFYING
COMPLETED
FAILED
```

Transicion principal:

```text
CREATED -> DISCOVERING -> PEER_FOUND -> NEGOTIATING -> UPGRADING_TRANSPORT
-> TRANSFERRING -> VERIFYING -> COMPLETED

TRANSFERRING -> INTERRUPTED -> WAITING_FOR_PEER -> RESUMING -> TRANSFERRING
VERIFYING -> FAILED when final hash is invalid
```

Cada transicion debe producir un evento observable.

## 13. BLE Discovery y Capability Exchange

BLE debe permitir anunciar presencia, escanear peers, detectar compatibilidad de protocolo y evitar mostrar dispositivos ajenos como peers validos.

Advertisement conceptual:

- Protocol Service UUID
- Protocol Version
- Ephemeral Discovery Identifier
- Capability Flags

No se anuncia nombre real, nombre del archivo, `objectId` completo, inventario de objetos ni identificadores personales.

`PeerCapabilities`:

- `protocolVersion`
- `supportsBleControl`
- `supportsWifiDirect`
- `maxChunkSize`
- `maxParallelStreams`
- `availableStorageClass`

## 14. Negociacion de Transporte

Politica inicial determinista:

```text
IF peer.supportsWifiDirect
AND pendingBytes >= WIFI_DIRECT_THRESHOLD
THEN REQUEST_WIFI_DIRECT_UPGRADE
ELSE USE_BLE_TRANSFER
```

Para la prueba obligatoria, el objeto debe superar el threshold y usar Wi-Fi Direct.

## 15. Wi-Fi Direct

El adaptador Wi-Fi Direct debe descubrir o usar el peer identificado, iniciar negociacion, formar grupo, determinar Group Owner, obtener endpoint IP, establecer socket, notificar `LINK_READY`, detectar desconexion y liberar recursos.

El Group Owner es solo un rol tecnico temporal, no autoridad de protocolo ni identidad persistente.

## 16. Protocolo Experimental de Transferencia

Sobre el socket Wi-Fi Direct se implementa framing explicito. Tipos minimos:

```text
HELLO
MANIFEST
MANIFEST_ACK
MISSING_CHUNKS
CHUNK
CHUNK_ACK
TRANSFER_STATUS
COMPLETE
COMPLETE_ACK
ERROR
```

Cada frame contiene:

```text
protocolVersion
frameType
transferId
sequenceNumber
payloadLength
payload
integrityCheck
```

No se permite depender de delimitadores textuales ambiguos. Debe usarse length-prefix framing o formato binario equivalente documentado.

## 17. Reanudacion

Despues de una interrupcion, el receptor calcula:

```text
missingChunks = manifest.chunks - verifiedLocalChunks
```

En el siguiente encuentro, los peers se redescubren, identifican transferencias incompletas compatibles, intercambian estado, el receptor comunica chunks faltantes, se restablece Wi-Fi Direct, se transfieren solo los chunks faltantes y se verifica el objeto completo.

## 18. Interrupciones Obligatorias

- **Caso A - Desconexion Wi-Fi Direct**: `TRANSFERRING -> INTERRUPTED`; progreso persistido.
- **Caso B - Dispositivos fuera de alcance**: perdida detectada; progreso persistido; recuperacion posterior.
- **Caso C - Cierre de app receptora**: chunks verificados sobreviven al reinicio de app.
- **Caso D - Reinicio del dispositivo receptor**: manifest, bitmap/lista de chunks y transferencia pendiente sobreviven.
- **Caso E - Chunk corrupto**: hash invalido; chunk rechazado; no se marca verificado; retransmision posterior.

## 19. Instrumentacion

Eventos minimos:

```text
BLE_SCAN_STARTED
BLE_PEER_DISCOVERED
BLE_CONTROL_CONNECTED
CAPABILITIES_EXCHANGED
WIFI_DIRECT_UPGRADE_REQUESTED
WIFI_DIRECT_GROUP_FORMED
WIFI_DIRECT_LINK_READY
TRANSFER_STARTED
CHUNK_SENT
CHUNK_RECEIVED
CHUNK_VERIFIED
CHUNK_REJECTED
LINK_LOST
TRANSFER_INTERRUPTED
PEER_REDISCOVERED
TRANSFER_RESUME_STARTED
MISSING_CHUNKS_CALCULATED
TRANSFER_RESUMED
OBJECT_RECONSTRUCTED
OBJECT_HASH_VERIFIED
TRANSFER_COMPLETED
```

Cada evento registra `timestamp`, `deviceIdExperimental`, `peerIdExperimental`, `transferId`, `eventType`, `transport` y `relevantMetrics`.

## 20. Metricas Obligatorias

- Discovery: BLE discovery latency; time to establish BLE control channel.
- Wi-Fi Direct: time to group formation; time to `LINK_READY`; connection success rate.
- Transferencia: total bytes; effective throughput; transfer duration; chunks sent; chunks verified; chunks retransmitted.
- Recuperacion: bytes before interruption; verified chunks before interruption; time to rediscover; time to resume; duplicate chunks after resume.
- Integridad: chunk verification failures; final object hash result.

## 21. Requisitos Funcionales

- **FR-001**: El sistema MUST descubrir un peer compatible mediante BLE.
- **FR-002**: El sistema MUST intercambiar capacidades mediante un canal BLE.
- **FR-003**: El sistema MUST negociar el establecimiento de Wi-Fi Direct.
- **FR-004**: El sistema MUST transferir un objeto de al menos 1 GiB mediante Wi-Fi Direct.
- **FR-005**: El sistema MUST dividir el objeto en chunks verificables.
- **FR-006**: El receptor MUST persistir cada chunk valido.
- **FR-007**: El receptor MUST verificar criptograficamente cada chunk.
- **FR-008**: El sistema MUST detectar la interrupcion del enlace.
- **FR-009**: El progreso MUST sobrevivir al cierre de la aplicacion.
- **FR-010**: El progreso MUST sobrevivir al reinicio del dispositivo.
- **FR-011**: Los dispositivos MUST poder redescubrirse mediante BLE.
- **FR-012**: El sistema MUST identificar transferencias incompletas.
- **FR-013**: El receptor MUST calcular los chunks faltantes.
- **FR-014**: El sistema MUST transferir unicamente chunks faltantes despues de reanudar.
- **FR-015**: El receptor MUST reconstruir el objeto.
- **FR-016**: El receptor MUST verificar el SHA-256 final.
- **FR-017**: El sistema MUST funcionar sin Internet ni infraestructura externa.
- **FR-018**: El sistema MUST producir logs y metricas locales.

## 22. Requisitos No Funcionales

- **NFR-001 - Determinismo observable**: Toda transicion relevante debe ser registrable y reproducible mediante logs.
- **NFR-002 - Persistencia**: Un proceso Android muerto no debe implicar perdida del progreso verificado.
- **NFR-003 - Integridad**: Ningun dato corrupto debe aceptarse silenciosamente.
- **NFR-004 - Independencia logica**: La transferencia logica debe sobrevivir a la destruccion de enlaces fisicos.
- **NFR-005 - Recursos acotados**: La app no debe cargar el archivo completo en memoria.
- **NFR-006 - Streaming**: Lectura, hashing, envio, recepcion y reconstruccion deben realizarse por streaming o acceso aleatorio acotado.
- **NFR-007 - Backpressure**: El emisor no debe producir datos ilimitadamente cuando receptor o socket no puedan consumirlos.
- **NFR-008 - Trazabilidad**: Cada fallo debe asociarse a BLE discovery, BLE control, negotiation, Wi-Fi Direct formation, socket connection, transfer, persistence, verification o resume.

## 23. Seguridad Minima

Obligatorio: SHA-256 por chunk, SHA-256 del objeto completo, identificadores de sesion no triviales, validacion estricta de longitudes, limites maximos de frames/manifests, rechazo de indices invalidos, proteccion contra path traversal, archivos recibidos en directorio controlado por la app y no confiar en nombres de archivo enviados por el peer.

La autenticacion criptografica definitiva queda fuera de alcance.

## 24. Aplicacion Experimental

Pantallas/superficies requeridas:

- **Local Node**: Experimental Node ID, BLE State, Wi-Fi Direct State, Storage Available.
- **Peer Discovery**: peers compatibles descubiertos.
- **Transfer**: Select File, Select Peer, Start Transfer, Force Disconnect, Resume Transfer.
- **Transfer State**: Transfer ID, Object ID, state, current transport, total bytes, verified bytes, verified chunks, missing chunks, throughput, elapsed time.
- **Metrics**: visualizacion y export local de resultados.

No se debe invertir esfuerzo en diseno visual de produccion.

## 25. Estrategia de Implementacion

1. BLE Discovery: dos dispositivos se descubren consistentemente.
2. BLE Control Channel: intercambian version y capacidades.
3. Wi-Fi Direct Link: forman grupo y establecen socket.
4. Raw Streaming: archivo pequeno sin chunking persistente.
5. Chunked Transfer: fragmentos con hashes individuales.
6. Durable Receiver: manifest, chunks y progreso persistentes.
7. Interruption Recovery: perdida del enlace conserva progreso.
8. Resume: calcula y transfiere solo chunks faltantes.
9. Large Object Test: objeto mayor o igual a 1 GiB.
10. Destructive Validation: cierre de app, reinicio, perdida de alcance y corrupcion.

## 26. Criterios de Exito

El Spike sera `VALIDATED` solo si dos smartphones Android reales:

1. Funcionan sin Internet.
2. Se descubren por BLE.
3. Intercambian capacidades.
4. Establecen Wi-Fi Direct.
5. Transfieren un objeto mayor o igual a 1 GiB.
6. Interrumpen deliberadamente la transferencia antes de completarla.
7. Conservan chunks verificados.
8. Sobreviven cierre de app o reinicio del receptor sin perder progreso.
9. Se redescubren.
10. Identifican la transferencia pendiente.
11. Calculan correctamente chunks faltantes.
12. Restablecen Wi-Fi Direct.
13. No retransfieren chunks ya verificados salvo corrupcion.
14. Completan transferencia.
15. Verifican que SHA-256 final coincide.
16. Producen logs y metricas suficientes para reconstruir temporalmente el experimento.

## 27. Criterios de Fallo

El Spike sera `INVALIDATED` si BLE no permite descubrimiento confiable, no puede correlacionarse el peer BLE con el peer Wi-Fi Direct, la formacion de grupo requiere interaccion manual incompatible, Android impide el comportamiento bajo condiciones realistas, la reanudacion reinicia desde cero, el estado persistente queda inconsistente, la tasa de exito de Wi-Fi Direct es insuficiente o el upgrade consume demasiado tiempo para encuentros cortos.

Un resultado `INVALIDATED` debe producir evidencia suficiente para decidir si modificar la estrategia hibrida, mantener BLE como unico transporte, evaluar otra tecnologia offline, cambiar descubrimiento o modificar el modelo de upgrade.

## 28. Entregables

Documentacion:

```text
/specs/001-hybrid-offline-link/spec.md
/docs/spikes/SPIKE-001-RESULTS.md
/docs/spikes/SPIKE-001-ANDROID-CONSTRAINTS.md
/docs/spikes/SPIKE-001-METRICS.md
/docs/adr/ADR-001-HYBRID-TRANSPORT-VIABILITY.md
```

Codigo experimental:

```text
/apps/android-spike/
/core/transfer/
/transports/ble/
/transports/wifi-direct/
/storage/
/observability/
```

## 29. Decisiones Posteriores al Spike

La evidencia debe responder si BLE es adecuado para descubrimiento continuo, si debe mantenerse un canal BLE durante Wi-Fi Direct, cuanto tarda el upgrade, que porcentaje de formacion Wi-Fi Direct es exitoso, que chunk size equilibra throughput/persistencia/reanudacion, si conviene ACK por chunk, ventanas o bitmaps, si el manifest debe contener todos los hashes o una estructura jerarquica, si objetos grandes son viables durante encuentros moviles, si el enlace hibrido puede mantenerse en background, que restricciones de Android condicionan el Protocol Core y si BLE + Wi-Fi Direct debe convertirse en transporte inicial de referencia.

## 30. Resultado Esperado

El resultado esperado no es una aplicacion terminada. Es evidencia:

```text
BLE DISCOVERY
  + CONTROL NEGOTIATION
  + WI-FI DIRECT BULK TRANSFER
  + DURABLE CHUNKING
  + INTERRUPTION
  + REDISCOVERY
  + RESUME
  + FINAL VERIFICATION
    -> VALIDATED or INVALIDATED
    -> ARCHITECTURAL DECISION
```

Primero demostramos que el enlace hibrido funciona bajo condiciones fisicas reales. Despues se disena el protocolo encima de evidencia.
