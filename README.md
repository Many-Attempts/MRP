# Media Ratings Platform (MRP)

A REST backend for media ratings and reviews — built in Java 17 on `com.sun.net.httpserver.HttpServer`, backed by PostgreSQL 16, deployed to Azure App Service for Containers via Azure Pipelines.

> See [`PROJECT_SUMMARY.md`](PROJECT_SUMMARY.md) for the one-pager (purpose, members, architecture).
> See [`docs/azure-setup.md`](docs/azure-setup.md) for the one-time Azure + DevOps bootstrap.

## Quick start (local development)

Prereqs: Java 17, Maven 3.9+, Docker.

```bash
# 1. Configure environment
cp .env.example .env
# edit .env if you want non-default credentials (otherwise leave as-is for local dev)

# 2. Start Postgres on host port 5434
docker compose --env-file .env up -d postgres

# 3. Build (runs unit tests too)
mvn clean package

# 4. Run the server
#    Linux/macOS:
set -a; source .env; set +a
java -jar target/MRP.jar

#    PowerShell (Windows):
Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim()) } }
java -jar target/MRP.jar
```

The server listens on http://localhost:8080.

## Project structure

```
src/main/java/org/example/
├── Main.java                  ← HTTP server bootstrap (port 8080)
├── db/Database.java           ← reads DB_URL / DB_USER / DB_PASSWORD env vars
├── handlers/                  ← HTTP handlers per resource
├── services/                  ← business logic
├── repositories/              ← data access
├── models/                    ← domain types
├── exceptions/                ← typed errors
└── utils/                     ← Router, JSON, UUIDv7

src/test/java/org/example/
└── services/, utils/          ← JUnit + Mockito tests

azure-pipelines.yml            ← multi-stage CI/CD (Build → Staging → Production)
Dockerfile                     ← multi-stage container build
docker-compose.yml             ← local Postgres for development
init.sql                       ← schema seed
docs/azure-setup.md            ← cloud bootstrap walkthrough
PROJECT_SUMMARY.md             ← team / purpose / architecture one-pager
```

## Configuration

All sensitive configuration is via **environment variables**. Nothing is hardcoded.

| Variable        | Purpose                                       | Local default (in `.env.example`)               |
|-----------------|-----------------------------------------------|-------------------------------------------------|
| `DB_URL`        | JDBC connection string                        | `jdbc:postgresql://localhost:5434/mrp_db`       |
| `DB_USER`       | Database username                             | `postgres`                                      |
| `DB_PASSWORD`   | Database password                             | `changeme`                                      |
| `POSTGRES_USER` | Used by `docker-compose` to seed local DB     | same as `DB_USER`                               |
| `POSTGRES_PASSWORD` | Used by `docker-compose`                  | same as `DB_PASSWORD`                           |
| `POSTGRES_DB`   | Used by `docker-compose`                      | `mrp_db`                                        |

**In Azure** (staging/production) the `DB_*` variables are injected by App Service from Azure Key Vault. The application code is identical between local and cloud.

## Tests

```bash
mvn test
```

Unit tests live under `src/test/java/org/example/services/` and `src/test/java/org/example/utils/`. They mock the repository layer (no live DB required to run them).

## CI/CD

`azure-pipelines.yml` runs on every push to `master`:

1. **Build** — Maven test + package, Docker build + push to ACR
2. **Deploy → Staging** — auto-deploy to `app-mrp-staging-*.azurewebsites.net`, smoke-check
3. **Deploy → Production** — manual approval gate, then deploy to `app-mrp-prod-*.azurewebsites.net`, smoke-check

Setup of the cloud resources, service connections, and approval gate is covered in `docs/azure-setup.md`.

## Security notes

- DB credentials live **only** in Azure Key Vault. Never in Git, never in pipeline variables, never in the container image.
- The Azure RM service connection uses **Workload Identity Federation** — no client secret is stored in Azure DevOps.
- Container pulls from ACR use the App Service's Managed Identity (`AcrPull` role) — no admin credentials.

## API endpoints

The server prints the full route list on startup. Highlights:

- `POST /api/auth/register`, `POST /api/auth/login`
- `GET|POST|PUT|DELETE /api/media[/{id}]`
- `POST /api/media/{id}/ratings`, `PUT /api/ratings/{id}`, `POST /api/ratings/{id}/like`
- `GET /api/users/{username}/profile`, `GET /api/leaderboard`, `GET /api/recommendations`

A Postman collection (`Media Ratings Platform (MRP) Copy.postman_collection.json`) is included for regression testing.
