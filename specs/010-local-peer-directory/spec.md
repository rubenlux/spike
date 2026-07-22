# SPEC-010 — Local Peer Directory, Human Identity Binding & Trust Establishment Protocol

**Estado:** Draft
**Tipo:** Core Identity UX & Trust Bootstrap Specification
**Prioridad:** P0 — Fundacional
**Dependencias:** SPEC-003, SPEC-004, SPEC-005, SPEC-006, SPEC-009
**Plataforma inicial:** Android
**Alcance del Core:** Multiplataforma por diseño
**Conectividad requerida:** Ninguna infraestructura externa
**Internet requerido:** No
**Servidor requerido:** No
**Autoridad central requerida:** No
**Cuenta requerida:** No
**Directorio global requerido:** No
**Transporte específico requerido:** No

---

# 1. Propósito

Definir e implementar el subsistema local mediante el cual una persona o aplicación puede:

* reconocer peers persistentes;
* asignar nombres y alias locales;
* distinguir identidad criptográfica de identidad humana;
* mostrar fingerprints verificables;
* comparar identidades fuera del canal inseguro;
* verificar peers mediante QR;
* verificar peers mediante comparación SAS;
* soportar bootstrap futuro mediante NFC;
* soportar verificación manual;
* establecer trust explícito;
* registrar evidencia de verificación;
* detectar cambios inesperados de identidad;
* detectar posibles ataques MITM;
* evitar trust-on-first-use silencioso;
* mantener relaciones de confianza entre reencuentros;
* soportar múltiples dispositivos por persona sin asumir identidad compartida;
* soportar reemplazo de dispositivo mediante migración explícita;
* soportar revocación de bindings;
* preservar historial de identidad;
* funcionar completamente offline;
* sobrevivir a process death;
* integrarse con el modelo de autorización de SPEC-009;
* producir eventos y métricas;
* ser testeable sin radios ni hardware real.

La SPEC define la transición:

```text
UNKNOWN CRYPTOGRAPHIC NODE
            │
            ▼
AUTHENTICATED ENCOUNTER
            │
            ▼
LOCAL PEER DIRECTORY ENTRY
            │
            ▼
HUMAN-READABLE IDENTITY PRESENTATION
            │
            ▼
VERIFICATION CEREMONY
            │
      ┌─────┼──────────────┐
      │     │              │
      ▼     ▼              ▼
     QR    SAS           MANUAL
      │     │              │
      └─────┴──────┬───────┘
                   │
                   ▼
          VERIFICATION EVIDENCE
                   │
                   ▼
            TRUST DECISION
                   │
          ┌────────┴────────┐
          │                 │
          ▼                 ▼
        REJECT             TRUST
          │                 │
          ▼                 ▼
       BLOCK /        LOCAL IDENTITY
       OBSERVED          BINDING
                            │
                            ▼
                  AUTHORIZATION GRANTS
```

---

# 2. Problema fundamental

Después de SPEC-009 existe:

```text
Persistent Cryptographic Node Identity
Authenticated Peer Discovery
Secure Sessions
Local Trust State
Capability Grants
Authorization Policies
Revocation
Peer Blocking
```

Pero falta resolver:

```text
¿Quién es realmente este NodeId?
¿Cómo sabe una persona que está hablando con el dispositivo correcto?
¿Cómo distinguir dos dispositivos desconocidos cercanos?
¿Cómo evitar aceptar silenciosamente un MITM durante el primer encuentro?
¿Cómo verificar identidad sin Internet?
¿Cómo guardar “este dispositivo pertenece a Rubén”?
¿Cómo detectar que un peer conocido aparece con una identidad diferente?
¿Cómo representar varias identidades de dispositivo de una misma persona?
¿Cómo reemplazar un dispositivo perdido sin transferir confianza automáticamente?
¿Cómo conservar evidencia de cómo fue verificado un peer?
¿Cómo revocar un vínculo humano sin borrar el historial de seguridad?
```

La identidad criptográfica responde:

```text
WHO CONTROLS THIS PRIVATE KEY?
```

La identidad humana responde:

```text
WHO DO I BELIEVE THIS CRYPTOGRAPHIC IDENTITY REPRESENTS?
```

No son equivalentes.

Por tanto:

> El protocolo necesita una capa explícita de binding local entre identidades criptográficas, etiquetas humanas, evidencia de verificación y decisiones de confianza.

---

# 3. Objetivo arquitectónico

Construir `LOCAL PEER IDENTITY & TRUST BOOTSTRAP CORE` que permita:
Peer Directory, Local Aliases, Peer Profiles, Human-Readable Fingerprints, Verification Sessions (QR, SAS, Manual), Verification Evidence, Trust Establishment & Override, Identity Change Detection, Identity History, Binding Revocation, Device Replacement, Multi-Device Human Grouping, Trust Migration, MITM Resistance, Crash Recovery, y Security Audit sin depender de Internet ni infraestructura centralizada.

---

# 4. Principios Fundamentales
1. **CRYPTOGRAPHIC IDENTITY ≠ HUMAN IDENTITY**: Un `NodeId` identifica una clave criptográfica, no a una persona legal.
2. **LOCAL ALIAS ≠ VERIFIED IDENTITY**: Un alias local no constituye verificación de identidad.
3. **FIRST ENCOUNTER MUST NOT SILENTLY CREATE TRUST**: Prohibido TOFU silencioso hacia estado `TRUSTED`.
4. **VERIFICATION BINDING**: La verificación vincula `localNodeId + remoteNodeId + VerificationSession + VerificationMethod + Transcript`.
5. **DISPLAY NAME IS UNTRUSTED METADATA**: Metadata anunciada por peers es no confiable por defecto.
6. **TRUST REQUIRES EVIDENCE OR EXPLICIT OVERRIDE**: Todo trust requiere evidencia o marca explícita `UNVERIFIED_TRUST_OVERRIDE`.
7. **DEVICE REPLACEMENT MUST NOT AUTOMATICALLY INHERIT TRUST**: Dispositivos nuevos requieren migración explícita.
8. **IDENTITY HISTORY MUST NOT BE REWRITTEN**: Historial auditable e inmutable.
9. **VERIFICATION FAILURE MUST NOT FALL BACK TO TRUST**: Fallo en verificación imposibilita otorgar trust.
10. **PEER DIRECTORY IS LOCAL STATE**: Estado puramente local y descentralizado.

---

# 5. Escenarios de Aceptación (A - K)
* **Scenario A — First Encounter & Observation**: Un peer desconocido se autentica; se crea `PeerDirectoryEntry` en estado `OBSERVED` sin trust.
* **Scenario B — QR Verification Ceremony**: Sesión QR interactiva vinculada a ambos `NodeId`s y nonces frescos; se produce evidencia e identidad vinculada (`HumanIdentity`).
* **Scenario C — SAS Verification Ceremony**: Comparación de string corto SAS (6 dígitos decimales + 3 palabras); confirmación mutua crea evidencia.
* **Scenario D — Manual Fingerprint Verification**: Comparación visual/fuera de banda del fingerprint canónico legible (10 grupos de 4 caracteres).
* **Scenario E — Unverified Trust Override**: Usuario otorga trust sin ceremonia de verificación; se registra `UNVERIFIED_TRUST_OVERRIDE` y restringe grants de alto riesgo.
* **Scenario F — Identity Change Detection**: Peer conocido se presenta con alias idéntico pero nuevo `NodeId`; se activa advertencia `POTENTIAL_IDENTITY_CHANGE` sin migrar trust.
* **Scenario G — Multi-Device Binding**: Misma `HumanIdentity` vinculada a múltiples `NodeId`s independientes (ej. Phone, Tablet); cada nodo se verifica por separado.
* **Scenario H — Binding Revocation**: Revocación de vínculo humano pasa `HumanNodeBinding` a `REVOKED`, reevalúa trust en SPEC-009 y preserva historial.
* **Scenario I — Device Replacement & Trust Migration**: Reemplazo de dispositivo viejo por uno nuevo; revisión explícita del plan de migración de grants.
* **Scenario J — Verification Session Collision & Replay Protection**: Nonces usados o QR expiratodo/reutilizado fallan con `VerificationNonceReplay` / `VerificationSessionExpired`.
* **Scenario K — Crash Recovery & Continuity**: Reconstitución de directorio, bindings e historial tras process death; sesiones no terminales vencidas expiran sin otorgar trust.
