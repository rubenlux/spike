# Identity Application

Application service layer for SPEC-003.

This layer orchestrates identity domain rules, crypto provider contracts and storage contracts. The current scaffold wires validation/derivation boundaries and intentionally rejects operations that require a concrete crypto/storage implementation.
