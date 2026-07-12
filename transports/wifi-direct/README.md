# Wi-Fi Direct Transport Adapter

Wi-Fi Direct is the high-volume transfer transport for SPEC-001.

Responsibilities:

- Form a Wi-Fi Direct group after BLE negotiation.
- Record Group Owner role as temporary link metadata only.
- Resolve endpoint IP.
- Establish a socket link.
- Detect link loss.
- Release resources.

Bulk bytes should be exchanged through `core/transfer` frames over the socket, not through ad hoc text delimiters.
