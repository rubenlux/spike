# Transfer Application

Application service layer for SPEC-002.

This layer orchestrates domain services and storage contracts. It is where transaction boundaries, event append, recovery and public command semantics are composed.

The current files define the service boundary and intentionally return typed `REJECTED` results until a concrete storage-backed implementation is added.
