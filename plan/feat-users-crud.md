# feat-users-crud — User Management (CRUD without hard delete)

**Status:** ✅ Implemented 2026-08-07 — backend + frontend + tests shipped (commit pending)
**Date:** 2026-08-07
**Scope:** Create / update (role, active, password) users as EDITOR. No hard delete.
**Decisions (confirmed by Abdullah):**
1. No hard delete — deactivate instead (audit trail + refresh-token integrity)
2. Safety rules: no self-demote/self-deactivate; cannot remove the last active EDITOR
3. Password editable by EDITOR (any user, including self)
4. Tests: unit + integration

---

## 1. Backend

### Endpoints (all under `/api/v1/users`, EDITOR-only for writes)

| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| GET | `/users` | any authenticated (was EDITOR-only) | — | `List<UserResponse>` — VIEWER can now see the list read-only |
| POST | `/users` | EDITOR | `CreateUserRequest` | `201 UserResponse` |
| PUT | `/users/{id}` | EDITOR | `UpdateUserRequest` | `UserResponse` |

### DTOs (records, jakarta validation — house style)

```java
public record CreateUserRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters") String username,
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password,
    @NotNull(message = "Role is required") Role role) {}
```

```java
public record UpdateUserRequest(
    Role role,               // null = unchanged
    Boolean active,          // null = unchanged
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    String password) {}      // null/blank = unchanged
```

`UserResponse` stays `(id, username, role, active)` — no schema change.

### Service logic (`UserService`)

**`create(CreateUserRequest, actor)`**
- `existsByUsername` → `ConflictException("Username already exists")` (409)
- encode with existing Argon2 `PasswordEncoder`, save
- `auditService.record("CREATE", "user", id, null, UserResponse.from(user))`

**`update(Long id, UpdateUserRequest, actor)`**
- find or `NotFoundException("User not found")` (404)
- **Safety rule A:** if `actor == target` and (role → VIEWER or active → false) →
  `ConflictException("You cannot demote or deactivate your own account")`
- **Safety rule B:** if target is an EDITOR and the change removes it from the active-EDITOR
  pool (active → false, or role EDITOR → VIEWER): count active EDITORs; if ≤ 1 →
  `ConflictException("Cannot remove the last active editor")`
- apply non-null fields; non-blank password → re-encode
- save, `auditService.record("UPDATE", "user", id, oldResponse, newResponse)`

Username immutable. `updated_at` touched (entity already does this).

### Controller

`@PreAuthorize("hasRole('EDITOR')")` on POST + PUT; **remove** it from GET `/users` so
VIEWERs can browse the read-only list (matches TESTING.md roles table).

---

## 2. Frontend (`Users.tsx` rewrite — CashSheet pattern)

- **Create form card:** username, password (type=password), role select (EDITOR/VIEWER)
  → POST → invalidate `["users"]`
- **Table:** username · role badge (green/amber as today) · status badge (Active/Inactive)
  · actions column
- **Edit mode** (`editingId` state, CashSheet style): role select, Active toggle,
  optional "New password (leave blank to keep)" field → PUT
- **Deactivate / Reactivate** button per row → PUT `{active: false/true}`
- **Self-row:** hide Deactivate + disable role change to VIEWER for the current user
  (`useAuth()`), server still enforces (409 surfaces via ErrorBanner)
- VIEWER role: form + actions hidden (`isEditor` gate, same as other pages)

`types.ts`: add `CreateUserRequest`, `UpdateUserRequest` interfaces.

---

## 3. Tests

### Unit (`UserServiceTest` — Mockito, no Spring context)
1. create → encodes password, saves, audits CREATE
2. create duplicate username → ConflictException
3. update role/active → audits UPDATE with old/new values
4. self-demote / self-deactivate → ConflictException
5. deactivate last active EDITOR → ConflictException
6. role-change EDITOR→VIEWER on last active EDITOR → ConflictException
7. password reset re-encodes hash

### Integration (`UsersFlowIntegrationTest` — Testcontainers + MockMvc, `AuthFlowIntegrationTest` pattern)
1. admin login → POST user → 201, appears in GET list, audit entry exists
2. duplicate username → 409
3. VIEWER login → GET /users 200 (read-only), POST /users → 403
4. admin PUTs viewer → EDITOR, active=false → 200; deactivated user login → 401
5. self-deactivate → 409; deactivate last active EDITOR → 409
6. password reset → old password rejected, new password logs in

---

## 4. Verification

```bash
make backend-test        # unit + integration (Testcontainers, needs Docker up)
cd frontend && npx tsc --noEmit && npm run build   # typecheck + prod build
```

Manual smoke via `make restart` + browser: create user → login as them → role change →
deactivate → login rejected → audit log shows user entries.

## 5. Deliverable

Commit `feat(backend+frontend): user management CRUD` on `main` (local; push only on
request). TESTING.md section 13 + 4 updated to reflect the shipped feature.
