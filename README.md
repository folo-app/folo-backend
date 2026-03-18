# folo-backend

FOLO backend for a social investing app that shares trades and builds long-term investing habits together.

## Stack

- Java 17
- Spring Boot 3.5
- Spring Security + JWT
- Spring Data JPA
- Flyway
- PostgreSQL
- Redis
- Gradle

## Implemented Scope

- Email signup, email verification, login, refresh, logout, withdraw
- JWT access/refresh token flow with rotation
- User profile 조회/수정, user search
- Follow / unfollow / follower / following
- Stock search and price lookup
- Trade create / list / detail / update / delete
- Holding + portfolio projection from trades
- Feed by follow graph with `FRIENDS_ONLY` visibility
- Comment create / list / delete
- Reaction toggle / change
- Notification list / read / read-all
- Notification settings update
- Reminder create / list / update / delete
- CSV import preview + confirm
- OCR import preview stub
- KIS key encrypted storage
- KIS sync stub adapter

## Key Rules

- `signup` creates the account and sends a verification code, but does not issue normal tokens.
- `email/confirm` is the first point where access/refresh tokens are issued.
- `trades` are the source of truth.
- `holdings` and `portfolios` are recalculated read models.
- `FRIENDS_ONLY` means mutual follow.
- `users`, `trades`, and `comments` use soft-delete semantics where relevant.
- import confirm accepts `importResultIds`, not `tradeIds`.
- preferred import confirm endpoint is `POST /portfolio/import/confirm`.
- repeated import confirm on the same preview result is rejected to prevent duplicate trades.

## Local Run

1. Start infra:

```bash
docker compose up -d
```

2. Run the app:

```bash
./gradlew bootRun
```

- `bootRun` defaults to the `local` profile.
- The local profile uses `localhost:55432` for PostgreSQL and `localhost:56379` for Redis to avoid conflicts with other projects.
- If you need a different profile, override it explicitly:

```bash
SPRING_PROFILES_ACTIVE=default ./gradlew bootRun
```

3. Open Swagger:

- `http://localhost:8080/api/swagger-ui.html`

4. Contract review artifacts:

- `docs/openapi.json`
- `docs/Folo.postman_collection.json`
- `docs/response-shape-guide.md`
- `docs/api-contract-review.md`

## Test

```bash
./gradlew test
./gradlew build
```

## Notes

- Test profile uses H2 in PostgreSQL compatibility mode because the current local environment does not have a running Docker daemon for Testcontainers.
- Redis-backed email verification is used outside the test profile; the test profile uses an in-memory verification store.
- `KIS_STUB_FILE=docs/examples/kis-sync-stub.example.json` can be used for local sync testing.
- `docs/examples/trades.sample.csv` can be used for CSV import testing.
- OCR import is currently a filename-based stub. Example filename:
  - `005930_KRX_BUY_10_74200_20250312101500.png`
- Apple/Google login, real KIS integration, real OCR, chart API, and home summary remain deferred.
