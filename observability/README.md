# Observability

Structured local telemetry for SPEC-001.

Every state transition and transport milestone should emit an event that can be exported from the device without Internet.

Minimum export formats:

- JSONL for event reconstruction.
- CSV for metric tables.
