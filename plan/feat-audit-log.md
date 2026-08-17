# Feat — Audit log wiring (step 6)

**Date:** 2026-08-06 · **Status:** Approved (project lead: "do audit log and Frontend phase 2") · **Repo:** sandook

## Goal
Write an `audit_log` row on every write (who + when + what changed), and expose it read-only to the frontend. Table already exists (V2):

```sql
audit_log (id, user_id → users.id, action, entity, entity_id, old_value JSONB, new_value JSONB, created_at)
```

## Design

### New `audit/` package
- `AuditLog` entity — `action` (CREATE/UPDATE/DELETE), `entity` ("cash_day", "petty_cash_tx", "parking_bill", "parking_cash_move", "parking_booking", "transfer"), `entity_id`, `old_value`/`new_value` as **JSONB** (`tools.jackson.databind.JsonNode` + `@JdbcTypeCode(SqlTypes.JSON)` — Jackson 3, matches Hibernate 7), lazy read-only `user` relation for username join
- `AuditLogRepository` — JPQL search with LEFT JOIN FETCH user, filters (entity/action), `ORDER BY id DESC`, limit/offset
- `AuditService` — `record(action, entity, entityId, oldValue, newValue)`; serializes objects via injected `ObjectMapper.valueToTree`; actor resolved from `SecurityContextHolder` (username → user id); **same transaction** as the business write (rollback together)
- `AuditEntryResponse` record — id, username, action, entity, entityId, oldValue, newValue, createdAt
- `AuditController` — `GET /api/v1/audit?entity=&action=&limit=&offset=` (limit capped at 500), any authenticated user (viewer can see, editor manages — per design doc)

### Wiring (service write methods call `auditService.record` at the end)
| Service | CREATE | UPDATE (old = pre-mutation entity snapshot) | DELETE |
|---|---|---|---|
| CashDayService | ✓ | ✓ | ✓ |
| PettyCashService | ✓ | ✓ | ✓ |
| ParkingBillService | ✓ | ✓ | ✓ |
| ParkingCashMoveService | ✓ | ✓ | ✓ |
| ParkingBookingService | ✓ | ✓ | ✓ |
| TransferService | ✓ | ✓ | ✓ |

Old value = JsonNode captured from the entity **before** `apply()`; new value = the response record (already the canonical post-write shape, incl. computed balances/links).

## Tests
New `AuditFlowIntegrationTest` (Testcontainers + MockMvc, same pattern):
- writes across modules produce audit rows with correct action/entity/entity_id + old/new values
- viewer can read `/api/v1/audit`, filters work, ordering newest-first

## Out of scope
Frontend audit page lives in `feat-frontend-phase2.md`.
