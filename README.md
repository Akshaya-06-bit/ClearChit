# ClearChit

**ClearChit** is a backend-first, full-stack demonstration platform for transparent and auditable traditional chit fund management. It combines a Spring Boot REST API with a responsive browser UI, PostgreSQL persistence, JWT authentication, and Redis configuration.

> Demo only: wallet recharge/withdrawal and contributions update the application's demo ledger. This project does not connect to a bank or payment gateway and must not be used to handle real funds without a proper legal, security, and financial review.

## Features

- Account registration and sign-in with BCrypt password hashing and JWT authentication.
- Create and browse chit groups with contribution, commission, capacity, duration, start date, and lifecycle status.
- Add registered members to a group using their member ID.
- Create monthly cycles, place bids, review bids, record contributions, and inspect payment summaries.
- Member wallet, demo recharge/withdrawal, and transaction history.
- Group ledger and audit trail views.
- Responsive frontend served directly by Spring Boot — no separate frontend build or CORS setup required.
- Docker Compose setup for the app, PostgreSQL, and Redis.

## Tech stack

- Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Bean Validation
- PostgreSQL, Redis, Maven
- HTML, CSS, and vanilla JavaScript
- Docker / Docker Compose

## Run locally on Windows

### Option A — use your existing local PostgreSQL and Redis

1. Ensure PostgreSQL is running and a database named `clearchit` exists.
2. Check `src/main/resources/application.properties` environment defaults. If your PostgreSQL username/password differs, set `DB_USERNAME` and `DB_PASSWORD` in your IDE run configuration or update your local values.
3. Ensure Redis is running if you want to use the configured Redis connection.
4. From the project root in PowerShell:

   ```powershell
   .\mvnw.cmd clean test
   .\mvnw.cmd spring-boot:run
   ```

5. Open **http://localhost:8081**. Health check: **http://localhost:8081/api/health**.

### Option B — run the whole stack with Docker

Install Docker Desktop, open PowerShell in the project root, and run:

```powershell
docker compose up --build
```

Then open **http://localhost:8081**. The first run creates a local Docker PostgreSQL volume. PostgreSQL is exposed to your Windows host on port `5433` and Redis on `6380` to avoid conflicts with local services already using `5432` or `6379`. The app container connects to the internal Docker service ports. The default Docker database password is for local development only; change it before sharing the machine or deploying.

To stop the containers:

```powershell
docker compose down
```

To remove the local database and Redis volumes too (destructive):

```powershell
docker compose down -v
```

## First-use walkthrough

1. Open the site and create an account.
2. Sign in. The response includes your **member ID**; the UI displays it on the overview and wallet pages.
3. Create a chit group.
4. Register another account in a separate browser/private window, note that account's member ID, then sign back into the organizer account and add that member to the group.
5. Open the group and create a cycle. Set a cycle date and bidding window appropriate to your test.
6. Members can place bids using their own member IDs. The backend applies its existing bid validation and settlement rules.
7. Use the wallet page to add demo funds and inspect transactions; use cycle actions to record contributions and view payment summaries.

The group owner is not automatically enrolled as a group member. Add the owner's member ID separately if the owner should also participate.

## API overview

| Purpose | Endpoint |
|---|---|
| Register | `POST /api/auth/register` |
| Login | `POST /api/auth/login` |
| Health | `GET /api/health` |
| List/create groups | `GET/POST /api/chit-groups` |
| Group detail | `GET /api/chit-groups/{id}` |
| Group members | `GET /api/chit-groups/{groupId}/members` |
| Add a member | `POST /api/chit-groups/{groupId}/members/{memberId}` |
| Group cycles | `GET/POST /api/chit-groups/{groupId}/cycles` |
| Bids | `GET/POST /api/cycles/{cycleId}/bids` |
| Payments | `GET /api/cycles/{cycleId}/payments` |
| Payment summary | `GET /api/cycles/{cycleId}/payment-summary` |
| Member wallet | `GET /api/members/{memberId}/wallet` |
| Wallet transactions | `GET /api/members/{memberId}/wallet/transactions` |
| Group ledger | `GET /api/chit-groups/{groupId}/ledger` |
| Audit trail | `GET /api/chit-groups/{groupId}/audit-logs` |

All API routes other than health, registration, and login require `Authorization: Bearer <JWT>`.

## Configuration

Environment variables override the development defaults:

| Variable | Purpose |
|---|---|
| `PORT` | HTTP port; defaults to `8081` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL address/database |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL credentials |
| `DDL_AUTO` | Hibernate schema mode; local default `update` |
| `SHOW_SQL` | Hibernate SQL logging; default `false` |
| `JWT_SECRET` | Base64-encoded secret for signing JWTs; set a unique secret in production |
| `JWT_EXPIRATION` | JWT lifetime in milliseconds; default `3600000` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` | Redis connection settings |

Never commit real passwords, database URLs, or production JWT secrets. See [`DEPLOYMENT.md`](DEPLOYMENT.md) for cloud deployment.

## Project layout

```text
src/main/java/com/clearchit/       Spring Boot API, services, entities, security
src/main/resources/static/         Responsive frontend (index.html, styles.css, app.js)
src/main/resources/application.properties
Dockerfile
docker-compose.yml
render.yaml
DEPLOYMENT.md
```
