# Sandook (صندوق)

Cash box ledger — parking, petty cash, shop cash & deposits, and transfers.
Replaces spreadsheet-based tracking with a secure, responsive web app.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 17 · Spring Boot 4.1 · Spring Security 7 (JWT) · Spring Data JPA |
| Database | PostgreSQL 17 · Flyway migrations |
| Frontend | Next.js 16 · React 19 · Tailwind CSS 4 · TypeScript |
| Infra | Docker Compose (db + backend + frontend) |
| Tests | JUnit 5 · Testcontainers · MockMvc |

## Features

- JWT auth with refresh rotation and reuse detection
- Role-based access — `EDITOR` (full) vs `VIEWER` (read-only)
- Bootstrap admin auto-created on empty database
- Daily cash sheet with running balance, deposit tracking, sanity checks
- Petty cash with automatic cash-sheet mirroring
- Parking bills, monthly bookings, and cash statement
- Inter-book transfers with linked ledger entries
- Full audit log on every write
- Excel import/export
- PWA with offline fallback and install prompt

## Quick start

```bash
cp .env.example .env   # optional — dev defaults work out of the box
docker compose up -d --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8081 |
| Health check | http://localhost:8081/actuator/health |

Default admin credentials are in `.env.example`. Change them before any real use.

## Local development

```bash
docker compose up -d db            # Postgres only (host port 5433)

cd backend
./mvnw spring-boot:run             # API on :8081

cd ../frontend
npm install
npm run dev                        # dev server on :3000
```

## Desktop build

Single executable for Windows, macOS, and Linux — no Java install required.

```bash
make desktop-build                 # builds jpackage app-image
make desktop-run                   # builds if needed, then runs
```

Output: `dist/Sandook/` — uses an embedded H2 database (no Docker needed).

## Configuration

All settings flow through environment variables. Dev defaults are in
`application.properties` and `docker-compose.yml`.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_PASSWORD` | dev default | Database password |
| `ADMIN_USERNAME` | `admin` | Bootstrap admin username |
| `ADMIN_PASSWORD` | *(unset — skipped)* | Bootstrap admin password |
| `JWT_SECRET` | dev value | HS256 signing key — **override in production** |
| `SERVER_PORT` | `8081` | Backend port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Allowed browser origins |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8081` | API base URL for the frontend |

## Testing

```bash
cd backend && ./mvnw test          # integration tests (Testcontainers)
make desktop-test                  # tests against embedded H2
```

## CI/CD

GitHub Actions builds desktop app-images on every push to `main`.
Push a version tag to create a release:

```bash
git tag v1.0.0
git push --tags
```

## License

MIT
