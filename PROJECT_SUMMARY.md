# Media Ratings Platform (MRP) — Project Summary

## Purpose

The **Media Ratings Platform** is a REST backend for managing user-generated media reviews and ratings (movies, TV, music, games). Users register, browse media, rate items, like other users' ratings, mark favorites, and see leaderboards & personal recommendations. The platform is delivered as a containerized Java service backed by PostgreSQL.

This repository is the deliverable for our DevOps coursework, demonstrating an end-to-end CI/CD pipeline on **Azure DevOps** (build + release) with secret management via **Azure Key Vault**, deployed to **Azure App Service for Containers**.

---

## Project size

**5 members** (FH Technikum Wien — SWEN Project, Azure DevOps org `DevOpsLessonsKost`).

## Members & responsibilities

> Adjust roles below if the actual division of work was different.

| Name              | Email (FH)                     | Role                  | Responsibilities                                                                            |
|-------------------|--------------------------------|-----------------------|---------------------------------------------------------------------------------------------|
| Daniel Kosterski  | if24b124@technikum-wien.at     | DevOps engineer       | `azure-pipelines.yml`, Dockerfile, ACR, App Service, Key Vault, service connections.        |
| Abdussalim Sen    | if24b257@technikum-wien.at     | Backend lead          | API design, `Main.java`, services & handlers (auth, media, ratings).                        |
| Elena Dordevic    | if24b156@technikum-wien.at     | Database / data       | Schema (`init.sql`), repositories layer, query tuning, Postgres on Azure.                   |
| Lisa Brunda       | if24b009@technikum-wien.at     | QA / testing          | JUnit unit tests under `src/test`, Postman regression collection, smoke-check verification. |
| Maria Giebl       | if24b067@technikum-wien.at     | Docs / Scrum master   | README, this summary, `docs/azure-setup.md`, sprint coordination, demo.                     |

---

## Tech stack

- **Language / runtime**: Java 17, plain `com.sun.net.httpserver.HttpServer` (no Spring Boot)
- **Build**: Maven 3.9 (with `maven-shade-plugin` producing a runnable fat JAR)
- **Database**: PostgreSQL 16 — local via Docker Compose, cloud via Azure Database for PostgreSQL Flexible Server
- **Container**: multi-stage Dockerfile → `eclipse-temurin:17-jre-alpine` runtime, port 8080
- **Tests**: JUnit Jupiter 5.10 + Mockito 5.14
- **CI/CD**: Azure Pipelines (multi-stage YAML)
- **Cloud hosting**: Azure App Service for Containers (Linux B1)
- **Image registry**: Azure Container Registry (Basic SKU)
- **Secrets**: Azure Key Vault, consumed by App Service via Managed Identity + `@Microsoft.KeyVault(...)` references

## Architecture

```
┌────────────┐  push to    ┌─────────────────┐  build+test+push  ┌────────────┐
│   GitHub   │────master──▶│ Azure Pipelines │──────────────────▶│    ACR     │
└────────────┘             └─────────────────┘                   └─────┬──────┘
                                    │                                   │
                                    ▼                                   ▼
                            ┌──────────────┐  manual approval  ┌──────────────────┐
                            │  staging Web │◀─────────────────▶│  production Web  │
                            │  App (B1)    │                    │  App (B1)        │
                            └──────┬───────┘                   └─────────┬────────┘
                                   │                                     │
                                   │  Managed Identity (no creds in app) │
                                   ▼                                     ▼
                            ┌─────────────────────────────────────────────────┐
                            │        Azure Key Vault (DB-URL, DB-USER,        │
                            │                       DB-PASSWORD)              │
                            └─────────────────────────┬───────────────────────┘
                                                      ▼
                                          ┌─────────────────────┐
                                          │ Azure DB for        │
                                          │ PostgreSQL (B1ms)   │
                                          └─────────────────────┘
```

---

## CI/CD pipeline overview

| Stage | Trigger | What it does |
|-------|---------|--------------|
| **Build** | push to `master` or PR | Caches `~/.m2`, runs `mvn clean package` (incl. JUnit tests), publishes JUnit results, builds Docker image, pushes to ACR with tags `$(Build.BuildId)` + `latest`. |
| **Deploy_Staging** | after Build, master only | `AzureWebAppContainer@1` deploys the new image tag to the **staging** Web App; runs an HTTP smoke check. |
| **Deploy_Production** | after Staging, **manual approval** | Same deploy task targeting the **production** Web App; smoke check. |

## Security posture

- ✅ **No secrets in Git** — `Database.java` reads `DB_URL` / `DB_USER` / `DB_PASSWORD` from env vars; `.env` is gitignored; `.env.example` only documents keys, never real values.
- ✅ **No secrets in pipeline** — DB credentials never appear as pipeline variables; App Service pulls them from Key Vault directly via Managed Identity.
- ✅ **No long-lived secrets in Azure DevOps** — AzureRM service connection uses **Workload Identity Federation (OIDC)**.
- ✅ **No registry passwords** — App Service uses `AcrPull` role on its Managed Identity to pull images.
- ✅ **Manual approval gate** before production deploys.

---

## Repository layout (additions for this assignment)

| Path | Why |
|------|-----|
| `azure-pipelines.yml` | Multi-stage CI/CD pipeline. |
| `Dockerfile`, `.dockerignore` | Containerize the app. |
| `.env.example` | Documents required env-var keys (no values). |
| `.gitignore` | Excludes `.env`, build output, IDE files. |
| `docs/azure-setup.md` | Step-by-step bootstrap of Azure resources + DevOps wiring. |
| `PROJECT_SUMMARY.md` | This file. |

## Code changes for this assignment

- `src/main/java/org/example/db/Database.java` — replaced hardcoded credentials with `System.getenv(...)` lookup, fail-fast when unset.
- `pom.xml` — added `maven-shade-plugin` so `mvn package` produces `target/MRP.jar` runnable directly.
- `docker-compose.yml` — switched from hardcoded credentials to env-var interpolation from local `.env`.

---

## How to demo

1. Show `git grep -i "password"` returns no real credentials anywhere in the tree.
2. Show the Key Vault in Azure Portal with the three secrets.
3. Open `azure-pipelines.yml`, walk through the three stages.
4. Trigger a build by pushing a commit; show the live pipeline.
5. Hit the staging URL after deploy.
6. Click *Approve* on the production gate; hit the production URL.
7. Tail logs (`az webapp log tail`) to show `Connected to PostgreSQL database!`.
