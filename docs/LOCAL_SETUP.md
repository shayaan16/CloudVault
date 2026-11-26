# Local installation (Windows)

## Required tools

- Docker Desktop with WSL 2, Git, VS Code. Docker builds Java and Maven inside the container, so local Java/Maven are optional for the Docker workflow.
- For development outside Docker install JDK 21 and Maven 3.9+, and the VS Code Extension Pack for Java.

## Launch

1. Extract `CloudVault.zip`, open the folder containing `pom.xml` and `compose.yaml` in VS Code.
2. Start Docker Desktop. Open PowerShell and run `docker version`, `docker compose version`.
3. `Copy-Item .env.example .env`; edit `.env` and set `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` to distinct strong values. The local compose network does not expose the database port to the host.
4. `docker compose up --build -d`.
5. `docker compose ps` and `docker compose logs --tail=100 app`.
6. Open `http://localhost:8080` and `http://localhost:8025` (local Mailpit inbox).
7. Register, verify by following the Mailpit email, and sign in. Demo mode is read-only; use your own account to test upload, deletion and sharing. Register a second account for direct sharing.
8. `docker compose down` stops services without deleting volumes. `docker compose up -d` restarts them. `docker compose up --build -d` rebuilds code changes.

The first build needs internet access for Docker images and Maven dependencies. This fresh project uses its own compose project volumes, not the previous database. Avoid reusing old volumes without migration.

## Native Maven run (optional)

Run a local MySQL 8.4 server, create a `cloudvault` database and a dedicated user with schema permissions, and run Mailpit separately (or `docker compose up -d mailpit`). Set credentials in the current PowerShell session:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/cloudvault?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USER='cloudvault'
$env:DB_PASSWORD='YOUR_LOCAL_PASSWORD'
$env:BASE_URL='http://localhost:8080'
mvn clean verify
mvn spring-boot:run
```

The application uses Hibernate schema generation; it creates the required tables on a new database. Local files go to `data/files`, excluded by Git. MySQL must be reachable at the specified address. `.env` is consumed by Docker Compose; Maven does not automatically import it.

## Troubleshooting

| Symptom | What to check |
|---|---|
| Port 8080 busy | Change `APP_PORT=8081` and `BASE_URL=http://localhost:8081`, then recreate app. |
| Hibernate cannot determine dialect | Check MySQL health, JDBC URL, username and password. Do not force a dialect to hide a failed DB connection. |
| MySQL unhealthy after changing `.env` | MySQL initialization passwords only apply on first startup. Restore the password used when creating the volume. Do not delete existing data. |
| Email not received locally | Open Mailpit at port 8025, ensure `MAIL_MODE=smtp`, `MAIL_HOST=mailpit` in Compose and that container is running. |
| Verification link wrong port | Fix `BASE_URL`, recreate app, resend after 60 seconds. |
| 403 on a mutation | Reload or sign in again; CSRF token is bound to session. Demo cannot mutate. |
| S3 unavailable | Verify bucket, region, IAM permissions, endpoint and key. Never switch storage mode on existing metadata without moving objects. |
| Health endpoint down | Inspect `docker compose logs mysql app`; database connectivity drives health. Mail delivery must be tested separately. |

## Local verification

Run `mvn clean verify` (if Maven is installed), or `docker run --rm -v "${PWD}:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -B verify` in PowerShell. Docker build skips tests for image creation; run the test command explicitly. Follow `TEST_REPORT.md` for actual acceptance testing.
