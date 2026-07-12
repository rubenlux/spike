rootProject.name = "spike"

// Stage 0 — Legacy leaf (zero internal deps)
include(":core:transfer")

// Stage 1 — Leaf modules (no internal imports)
include(":core:identity-domain")
include(":core:transfer-domain")
include(":crypto:session-crypto-contracts")

// Stage 2 — Contracts depending only on Stage 1
include(":core:identity-contracts")
include(":core:transfer-contracts")
include(":core:secure-session-domain")
include(":crypto:crypto-provider-contracts")
include(":storage:identity-storage-contracts")

// Stage 3 — Modules depending on Stage 1-2
include(":core:discovery-domain")
include(":core:secure-session-contracts")
include(":core:encounter-domain")
include(":storage:transfer-storage-contracts")

// Stage 4 — Application modules depending on Stage 1-3
include(":core:discovery-contracts")
include(":core:identity-application")
include(":core:transfer-application")
include(":core:secure-session-application")
include(":core:discovery-application")

// Stage 5 — Testing modules + encounter-application, discovery-testing
include(":core:identity-testing")
include(":core:transfer-testing")
include(":core:secure-session-testing")
include(":core:encounter-application")
include(":core:discovery-testing")

// Stage 6 — Transports
include(":transports:ble-control-contracts")
include(":transports:ble-discovery-contracts")
include(":transports:ble")
include(":transports:wifi-direct")

// Stage 7 — Cross-cutting
include(":observability")
include(":storage")
