rootProject.name = "spike"

// Stage 0 — Legacy leaf (zero internal deps)
include(":core:transfer")

// Stage 1 — Leaf modules (no internal imports)
include(":core:identity-domain")
include(":core:transfer-domain")
include(":core:security-domain")
include(":core:peer-directory-domain")
include(":core:service-discovery-domain")
include(":core:dtn-routing-domain")
include(":crypto:session-crypto-contracts")

// Stage 2 — Contracts depending only on Stage 1
include(":core:identity-contracts")
include(":core:transfer-contracts")
include(":core:secure-session-domain")
include(":core:security-contracts")
include(":core:peer-directory-contracts")
include(":core:service-discovery-contracts")
include(":core:dtn-routing-contracts")
include(":crypto:crypto-provider-contracts")
include(":storage:identity-storage-contracts")
include(":storage:security-storage-contracts")
include(":storage:peer-directory-storage-contracts")
include(":storage:service-discovery-storage-contracts")
include(":storage:dtn-routing-storage-contracts")

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
include(":core:security-application")
include(":core:peer-directory-application")
include(":core:service-discovery-application")
include(":core:dtn-routing-application")

// Stage 5 — Testing modules + encounter-application, discovery-testing
include(":core:identity-testing")
include(":core:transfer-testing")
include(":core:secure-session-testing")
include(":core:encounter-application")
include(":core:discovery-testing")
include(":core:security-testing")
include(":core:peer-directory-testing")
include(":core:service-discovery-testing")

// Stage 6 — Transports
include(":transports:ble-control-contracts")
include(":transports:ble-discovery-contracts")
include(":transports:ble")
include(":transports:wifi-direct")

// Stage 7 — Cross-cutting
include(":observability")
include(":storage")
