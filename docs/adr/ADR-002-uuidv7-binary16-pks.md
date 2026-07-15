# ADR-002: UUIDv7 in BINARY(16) for primary keys; BIGINT only for append-only event tables

- **Status:** Accepted
- **Date:** 2026-07-15

## Context

Auto-increment keys leak volume, complicate multi-writer futures, and can't be generated
client-side (the Idempotency-Key flow wants ids before the row exists). Random UUIDv4 in InnoDB
fragments the clustered index badly at scale.

## Decision

All entity PKs are UUIDv7 stored as BINARY(16) — time-ordered, so inserts are append-friendly
and range scans by recency use the PK. High-volume append-only tables (request_events,
sync_events, audit_log) use BIGINT AUTO_INCREMENT — they're insert-only and read by FK + time
index anyway. Idempotency-Key headers are also UUIDv7 (rejected otherwise, 3.13).

## Consequences

- `UuidV7` helper in `app-common` is the single generation point in Java; `uuidv7` npm package
  in the wizard.
- Debugging raw rows needs `BIN_TO_UUID()` — the dev seed shows the pattern.
