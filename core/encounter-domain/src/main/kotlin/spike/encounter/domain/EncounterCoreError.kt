package spike.encounter.domain

sealed class EncounterError(
    val code: String,
    message: String
) : Exception(message)

class EncounterNotFound(encounterId: EncounterId) :
    EncounterError("EncounterNotFound", "Encounter not found: ${encounterId.value}")
class EncounterAlreadyActive(remoteNodeId: String) :
    EncounterError("EncounterAlreadyActive", "Encounter already active for remote node: $remoteNodeId")
class InvalidEncounterStateTransition(from: EncounterState, to: EncounterState) :
    EncounterError("InvalidEncounterStateTransition", "Invalid encounter state transition: $from -> $to")
class DuplicateEncounterConflict(remoteNodeId: String) :
    EncounterError("DuplicateEncounterConflict", "Duplicate active encounter conflict for $remoteNodeId")
class EncounterPersistenceFailure(message: String) :
    EncounterError("EncounterPersistenceFailure", message)

