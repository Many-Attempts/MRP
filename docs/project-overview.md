# MRP DevOps Project — Full Technical Overview

> **Companion document to [PROJECT_SUMMARY.md](../PROJECT_SUMMARY.md).**
> The summary is the assignment-required one-pager. *This* document is the in-depth writeup: every design decision, the alternatives we ruled out, every command we ran, what each output meant, and how the pieces fit together end-to-end.

---

## 1. Executive summary

The **Media Ratings Platform (MRP)** is a Java 17 REST backend (port 8080, plain `com.sun.net.httpserver.HttpServer`, no Spring Boot) that uses PostgreSQL 16. As the SWEN-Project DevOps deliverable, we wrapped it with:

- A **multi-stage Azure Pipeline** (Build → Deploy_Staging → Deploy_Production) defined in `azure-pipelines.yml`.
- A **container image** built via a multi-stage `Dockerfile`, pushed to **Azure Container Registry**.
- Deployment to **Azure App Service for Containers** (one Web App per environment).
- **Azure Key Vault** holding the database credentials, consumed by App Service via **System-Assigned Managed Identity**.
- **Workload Identity Federation** for the Azure DevOps → Azure RM service connection — zero long-lived secrets stored anywhere.

### Grading rubric — coverage map

| Rubric item (pts) | Status | Where it's satisfied |
|---|---|---|
| Code Integration via Git (5) | ✅ | GitHub `Many-Attempts/MRP` connected to Azure DevOps via the GitHub OAuth service connection; `azure-pipelines.yml` triggers on `master`. |
| Build Pipeline (10) | ✅ | `azure-pipelines.yml` `Build` stage: `Maven@4` runs `mvn clean package` with JUnit results published; `AzureCLI@2` `az acr login`; inline `docker build` + `docker push` to ACR. |
| Release Pipeline (5) | ✅ | `azure-pipelines.yml` stages `Deploy_Staging` and `Deploy_Production`, both using `AzureWebAppContainer@1`, with manual approval gate on the `production` Environment. |
| Security / Service Connections / Key Vault (10) | ✅ | Key Vault `kv-mrp-38215` holds `DB-URL` / `DB-USER` / `DB-PASSWORD`; both Web Apps reference them via `@Microsoft.KeyVault(...)` resolved by their Managed Identity (Key Vault Secrets User role). The Azure RM service connection uses Workload Identity Federation — no client secret stored. |
| **No secrets in Git or pipeline** *(hard rule)* | ✅ | `Database.java` reads `DB_URL` / `DB_USER` / `DB_PASSWORD` from environment; `.env` is gitignored; `.env.example` documents only placeholder values. The pipeline never sees real credentials — they flow Key Vault → App Service only. |
| One-page project summary | ✅ | [`PROJECT_SUMMARY.md`](../PROJECT_SUMMARY.md). |

### Live state (as of 2026-05-04)

- Pipeline run **20260504.6** = green through Build + Staging; awaiting approval on Production.
- Image `acrmrp38215.azurecr.io/mrp:6` in ACR (history `latest, 6, 3, 2`).
- Staging serving HTTP 200: `https://app-mrp-staging-38215.azurewebsites.net/` →
  `{"status":"ok","service":"Media Ratings Platform"}`.

---

## 2. Architecture

```
┌──────────────┐      git push       ┌────────────────────────────┐
│   Developer  │ ─────────────────▶  │ GitHub: Many-Attempts/MRP  │
└──────────────┘     (master)        └────────────┬───────────────┘
                                                  │  webhook
                                                  ▼
                                       ┌──────────────────────┐
                                       │  Azure DevOps        │
                                       │  Pipeline (run N)    │
                                       │                      │
                                       │  Stage: Build        │
                                       │   - mvn test/package │
                                       │   - az acr login     │
                                       │   - docker build/push│
                                       └────┬─────────┬───────┘
                                            │         │
                  WIF (OIDC, short-lived) ──┘         │ Docker push
                                            │         ▼
                                            ▼   ┌─────────────────────┐
                                  ┌─────────────│ Azure Container     │
                                  │             │ Registry (ACR)      │
                                  │             │ acrmrp38215         │
                                  │             └──────────┬──────────┘
                                  │                        │ pull (AcrPull via MI)
                                  │                        ▼
                            ┌─────┴───────┐   ┌────────────────────────┐
                            │ Stage:      │   │ App Service             │
                            │ Deploy_     │──▶│   app-mrp-staging-38215│
                            │ Staging     │   └──────────┬─────────────┘
                            └─────┬───────┘              │
                                  │                      │ Managed Identity
                            (manual approval)            ▼
                                  │           ┌─────────────────────────┐
                            ┌─────┴───────┐   │ Azure Key Vault         │
                            │ Stage:      │   │   kv-mrp-38215          │
                            │ Deploy_     │   │   secrets:              │
                            │ Production  │   │     DB-URL              │
                            │             │──▶│     DB-USER             │
                            └─────────────┘   │     DB-PASSWORD         │
                                       │      └──────────┬──────────────┘
                                       ▼                 │
                            ┌────────────────────────┐   │ resolved at startup
                            │ App Service             │  │ → injected as env vars
                            │   app-mrp-prod-38215    │◀─┘
                            └──────────┬─────────────┘
                                       │
                                       │ JDBC over TLS
                                       ▼
                              ┌─────────────────────────┐
                              │ Azure DB for PostgreSQL │
                              │   psql-mrp-38215        │
                              │   db: mrp_db            │
                              └─────────────────────────┘
```

### Trust boundaries

The most important property of this design: **the database credentials never leave the Azure Key Vault → App Service path.** Specifically:

- The build agent never sees `DB-USER`/`DB-PASSWORD`. It only does `az acr login` (registry-token-only auth) and `docker push`.
- The pipeline YAML never references the DB secrets. Variable groups are not used.
- The deploy task `AzureWebAppContainer@1` only sets the container image name — it does not pass app settings.
- App Service settings `DB_URL`, `DB_USER`, `DB_PASSWORD` were configured *once* (by `bootstrap-azure.ps1` running `az webapp config appsettings set`), and their values are **placeholder strings of the form** `@Microsoft.KeyVault(VaultName=kv-mrp-38215;SecretName=DB-PASSWORD)`. Azure Resource Manager resolves those at container start, using the App Service's own Managed Identity. The container process sees the resolved value as an ordinary environment variable.

### Identity / authorization in plain terms

| Principal | Has what role / where | Why |
|---|---|---|
| Daniel Kosterski (the user) | Owner on the subscription `Azure for Students` | Bootstrap-time provisioning. |
| `sc-azure-rm` service connection (workload-identity SP, short-lived OIDC token) | Contributor on `rg-mrp-dev` (auto-assigned during connection creation) | Pipeline tasks need to deploy + push images. |
| `app-mrp-staging-38215` Managed Identity | `Key Vault Secrets User` on `kv-mrp-38215`; `AcrPull` on `acrmrp38215` | App Service needs to read three secrets at start, and pull images from ACR. |
| `app-mrp-prod-38215` Managed Identity | same as staging | same |

Notably absent: there is **no service principal with a client secret** anywhere in the system.

---

## 3. End-to-end flow (one concrete example)

A `git push origin master` to fix a typo. Walk through what actually happens:

1. **GitHub** receives the push. Webhook fires to Azure DevOps.
2. **Azure DevOps** matches the trigger (`trigger.branches.include: [master]`) and queues a run on Microsoft-hosted Linux pool. *Caveat: queues for ~2-3 min during peak hours; we measured 2:32 on run #6.*
3. **Build stage, ubuntu-latest agent**:
   1. `Checkout` clones the repo at `feature/azure-pipelines` or `master` HEAD.
   2. `Maven@4` runs `mvn -B clean package`. JUnit (41 tests) executes; results published as a Test Run.
   3. `AzureCLI@2` exchanges the WIF OIDC token for a Microsoft Entra ID token, then calls `az acr login --name acrmrp38215`. ACR responds with a 3-hour pull/push token, which gets stored in `~/.docker/config.json`.
   4. Inline `script:` runs `docker build -t acrmrp38215.azurecr.io/mrp:$(Build.BuildId) -t acrmrp38215.azurecr.io/mrp:latest -f Dockerfile .`. The two-stage build: Maven layer downloads deps and produces `target/MRP.jar`, then the Alpine JRE layer copies just the jar.
   5. `docker push` sends both tags to ACR.
   6. `publish` artifact step uploads `azure-pipelines.yml` itself as a build artifact (audit trail).
4. **Deploy_Staging stage, ubuntu-latest agent**:
   1. `AzureWebAppContainer@1` calls Azure RM to update `app-mrp-staging-38215` with the new image name. App Service tells ACR: "give me this image". ACR validates the App Service's MI has `AcrPull` → image streams to App Service.
   2. App Service starts the container. The runtime resolves `@Microsoft.KeyVault(...)` placeholders by calling Key Vault as the App Service's MI. Java sees `DB_URL=jdbc:postgresql://...`, opens a TLS-protected JDBC connection.
   3. `Database.connect()` prints `Connected to PostgreSQL database!`.
   4. `AzureCLI@2` smoke-check: `curl -fsS https://app-mrp-staging-38215.azurewebsites.net/`. Retries up to 6× with 15s sleep so cold-start latency doesn't fail the build.
5. **Deploy_Production stage**:
   1. The `production` Environment has an Approvals check — pipeline pauses, sends an email/in-app notification.
   2. Approver clicks "Review → Approve".
   3. Same `AzureWebAppContainer@1` deploys to `app-mrp-prod-38215`. Same Key Vault references, same MI resolution.
   4. Same smoke-check.

Total wall-clock: ~5–8 minutes from push to staging-live, plus however long the human takes to approve.

---

## 4. Azure resources we created

All in resource group `rg-mrp-dev`, region `switzerlandnorth` (the closest of the five allowed by Azure-for-Students region policy on the FH Technikum Wien tenant).

| Resource | Name | SKU / size | Why this resource | Why this SKU |
|---|---|---|---|---|
| Resource group | `rg-mrp-dev` | n/a | One container for everything; `az group delete -n rg-mrp-dev --yes --no-wait` deletes it all on teardown. | n/a |
| Container registry | `acrmrp38215` | Basic | Private image store, MI-pullable. ACR-Tasks build available if we ever want server-side build. | Basic = $5/mo, includes 10 GiB storage, fine for one app's image history. |
| PostgreSQL Flexible Server | `psql-mrp-38215` | Burstable B1ms, 32 GiB SSD, PG 16 | The application's only stateful dependency. | Burstable B1ms ≈ $13/mo; far above what a school project needs but it's the smallest tier for Flexible Server. |
| Key Vault | `kv-mrp-38215` | Standard | The single secret store. | Standard is half the price of Premium; we don't need HSM-backed keys. RBAC mode (`--enable-rbac-authorization true`) means access is governed by Azure RBAC, not legacy access policies — simpler and consistent with the rest of the project's IAM. |
| App Service plan | `asp-mrp-linux` | Linux B1 | Compute for both Web Apps. | B1 = $13/mo, supports custom containers + always-on. Hosts both Web Apps on the same plan, so the second Web App is essentially free. |
| Web App for Containers | `app-mrp-staging-38215` | n/a (uses plan) | Staging deploy target. | n/a |
| Web App for Containers | `app-mrp-prod-38215` | n/a (uses plan) | Production deploy target. | n/a |

**Approximate monthly bill: $31 USD.** The Azure-for-Students $100 credit covers ~3 months — enough for the assignment lifecycle. Teardown command is in §10.

---

## 5. Why this design (alternatives we considered, and why we ruled them out)

This is the most important section for the grader. Every bullet is `Alternative considered → why we rejected it → why we chose what we did`.

### 5.1 Move DB credentials out of source code

**Alternatives considered**:

- **Keep hardcoded `postgres/postgres` and just don't push** — No. The repo is already in GitHub, the rubric explicitly forbids it, and a dev environment leak is still a leak.
- **Use a `.properties` file loaded from disk** — Would still ship in the JAR. Same problem.
- **Use Spring profiles** — We don't have Spring. Pulling Spring in just for config would be a sledgehammer for a nail.

**Chosen**: `System.getenv("DB_URL")` etc. with **fail-fast** validation — `IllegalStateException` on missing value. Why fail-fast: a silent `null` would crash later when the first JDBC call ran, and the error would be misleading (`URL must not be null` from the driver) instead of "you forgot to set DB_URL".

Why the env-var lookup is *lazy* (called inside `Database.connect()`, not in a `static final` initializer): any test that imports `Database` transitively (e.g. through `UserRepository`) would trigger the static initializer. Mockito's `@Mock` skips constructors but does *not* skip class-loader-time initializers. Lazy lookup avoids breaking the 41-test suite when env vars aren't set.

### 5.2 Build the container with a multi-stage Dockerfile

**Alternatives considered**:

- **Single-stage `maven` image as the runtime** — Image is ~700 MB. Includes `javac`, full Maven, the entire dependency cache. Larger surface = more CVEs.
- **Build outside Docker, then use `openjdk:17-jre` as runtime** — Forces every developer (and the CI agent) to have a working JDK + Maven on the host. We *don't* have that locally on Windows; we'd be installing Maven just for the project. Multi-stage Dockerfile means `docker build` is the only requirement.

**Chosen**: build stage uses `maven:3.9-eclipse-temurin-17`, runtime stage uses `eclipse-temurin:17-jre-alpine`. Final image: **187 MB**. The build-stage layer is discarded; the runtime ships only the JRE and `app.jar`.

### 5.3 Maven shade plugin (fat JAR)

**Alternatives considered**:

- **`maven-jar-plugin` with `<Class-Path>` manifest** — requires shipping all dependency JARs as separate files in the container, then constructing the classpath. More moving parts.
- **`maven-assembly-plugin`** — works, but does *not* automatically merge `META-INF/services/...` files. The PostgreSQL driver registers its `java.sql.Driver` SPI through service files. With assembly, those would silently overwrite each other on conflicting JARs.
- **Spring Boot's `spring-boot-maven-plugin`** — would require pulling Spring Boot in. Out of scope.

**Chosen**: `maven-shade-plugin` with `ServicesResourceTransformer`. SPI files merge correctly. Resulting `target/MRP.jar` is fully self-contained and runnable with `java -jar`.

### 5.4 Azure App Service for Containers (over AKS / ACI / VM)

**Alternatives considered**:

- **AKS (Kubernetes)** — Even the cheapest control plane is ~$73/month, plus node VMs. Massive overkill for one Java app. Adds Helm/manifests/ingress/cert-manager complexity that would dwarf the actual deliverable.
- **Azure Container Instances** — Cheap, but no first-class deployment slot story, no built-in TLS termination, no native Key Vault reference resolution at runtime, no deploy-via-pipeline-task convenience.
- **A Linux VM running `docker compose`** — Forces us to write systemd units, write our own log rotation, set up Let's Encrypt for HTTPS, configure firewall rules… The rubric is about pipelines, not Linux administration. Every hour spent on `apt install` is an hour not on Pipelines.

**Chosen**: Azure App Service for Containers (Linux). Reasons:

- Native `AzureWebAppContainer@1` deploy task — one YAML stanza per deploy.
- Native `@Microsoft.KeyVault(...)` reference resolution — Key Vault wiring is "set an app setting and grant a role", no SDK changes.
- Native MI-based ACR pull (`acrUseManagedIdentityCreds: true`) — no admin user, no separate registry credentials.
- Free TLS cert on the `*.azurewebsites.net` hostname.
- `az webapp log tail` for live logs — no log shipping setup.

### 5.5 Two Web Apps instead of deployment slots

**Alternative considered**: Single Web App with a `staging` slot, then "swap" on prod release.

**Why rejected**: deployment slots are a **Standard tier (S1) feature**, not Basic. S1 is +$56/month over B1. For a school project that's genuinely wasted money.

**Chosen**: Two distinct Web Apps (`app-mrp-staging-38215`, `app-mrp-prod-38215`) sharing the same App Service plan. Marginal cost is ~zero (CPU/RAM is the plan, not the app). Trade-off: no zero-downtime swap; a redeploy = ~30 seconds of cold-start. Acceptable.

### 5.6 Workload Identity Federation, not service-principal-with-secret

**Alternative considered**: `az ad sp create-for-rbac --role contributor` produces a `client_id`/`client_secret`. Paste those into the Azure RM service connection.

**Why rejected**: That `client_secret` is itself a stored credential. Even though Azure DevOps encrypts service-connection secrets at rest, the *concept* of "we stored a long-lived secret in the pipeline" violates the spirit of the rubric. And SP secrets expire, requiring rotation work.

**Chosen**: WIF — the service connection is bound to a federated Azure AD application credential. When the pipeline runs, Azure DevOps requests a short-lived OIDC token and exchanges it for an Azure access token. No long-lived secret exists anywhere — Azure DevOps doesn't have one to leak. Confirmed via:

```text
az devops service-endpoint list
# sc-azure-rm    WorkloadIdentityFederation
```

### 5.7 Key Vault references in App Service settings, not pipeline variable groups

**Alternative considered**: Create a **Variable Group** in Azure DevOps Library, link it to Key Vault, mark its variables as secret. The Maven/Docker steps would have `DB_URL`/`DB_USER`/`DB_PASSWORD` available as env vars during build/deploy.

**Why rejected**: this puts the secrets *into the build agent*. Even if every task carefully avoids `echo`-ing them, the values are in `process.env` of every command — one `printenv` in a debug step leaks them to logs that may be world-readable inside the org. The build agent has no need to know them.

**Chosen**: Three app settings on each Web App, with the value form `@Microsoft.KeyVault(VaultName=…;SecretName=…)`. Azure Resource Manager resolves those *inside the App Service runtime*, using the Web App's MI. The pipeline never touches the values; the build agent never has them in its environment.

Verification (the `value` column shows the placeholder, never the resolved secret):

```text
$ az webapp config appsettings list -g rg-mrp-dev -n app-mrp-staging-38215 \
   --query "[?starts_with(value, '@Microsoft.KeyVault')]" -o table
Name           Value
-------------  ------------------------------------------------------------------
DB_URL         @Microsoft.KeyVault(VaultName=kv-mrp-38215;SecretName=DB-URL)
DB_USER        @Microsoft.KeyVault(VaultName=kv-mrp-38215;SecretName=DB-USER)
DB_PASSWORD    @Microsoft.KeyVault(VaultName=kv-mrp-38215;SecretName=DB-PASSWORD)
```

### 5.8 ACR push via `az acr login` over the Azure RM connection (not a separate Docker Registry connection)

**Alternative considered**: `Docker@2` task with `containerRegistry: 'sc-acr'`, where `sc-acr` is a dedicated Docker Registry service connection.

**Why rejected**: this is what we tried first, and it produced our biggest pipeline failure. The connection was created in the DevOps UI but accidentally configured as **Registry Type: "Others"** — its URL field was the default `https://hub.docker.com/`. The Docker@2 task fell through to a vanilla `docker push mrp:1`, which went to Docker Hub. Image leaked publicly (briefly — we deleted the run; the image is now private).

The deeper issue: *that misconfiguration was undetectable from the pipeline YAML*. The YAML correctly named `sc-acr`. The connection existed. The auth scheme was `UsernamePassword` (which is normal for ACR-via-admin or generic). Only `data.url` revealed the mistake. A dedicated registry connection is a footgun.

**Chosen**: drop the Docker Registry connection entirely. Use the existing `sc-azure-rm` (workload identity, Contributor on the RG) plus `az acr login --name acrmrp38215`. The CLI exchanges the WIF token for an ACR access token (3-hour lifetime), writes it to `~/.docker/config.json`, and `docker push` against the fully qualified `acrmrp38215.azurecr.io/mrp:tag` succeeds. One fewer service connection means one fewer thing that can be misconfigured.

### 5.9 Master-only deploy condition

```yaml
condition: and(succeeded(), eq(variables['Build.SourceBranch'], 'refs/heads/master'))
```

**Alternative considered**: deploy from any branch with `condition: succeeded()`.

**Why rejected**: feature branches must produce CI signal (build green, tests pass) but must *not* deploy. Otherwise an experimental branch could accidentally overwrite production. Standard practice in industry.

**Cost**: we had to merge `feature/azure-pipelines` to `master` before the deploy stages would actually run. Did that with `git merge --no-ff`.

### 5.10 `set -euo pipefail` in every bash block

Without it, `docker build && some-other-thing` will report success if `some-other-thing` succeeds, even if `docker build` failed and the failure message scrolled off-screen. With `set -euo pipefail`, *any* failure aborts the script immediately.

This is one of those "you'll never notice it works, you'll only notice the day a silent failure ships to production" details. The cost of including it is one line.

### 5.11 Region: `switzerlandnorth`

**What we tried first**: `westeurope` (Amsterdam, ~1000 km from Vienna).

**What happened**: `RequestDisallowedByAzure` on the very first ACR creation attempt. The Azure-for-Students subscription policy `sys.regionrestriction` only permits these five regions:

```
- uaenorth
- spaincentral
- switzerlandnorth
- italynorth
- polandcentral
```

**Chosen**: `switzerlandnorth` — closest to Vienna geographically among the allowed set, mature region, no compliance gotchas for educational use.

### 5.12 Drop the Maven cache step

**What was there**: `Cache@2` keyed on `**/pom.xml`, caching `$(Pipeline.Workspace)/.m2/repository`.

**What happened**: post-job `tar` failed with `Cannot open: No such file or directory`. Reason: the Maven@4 task uses the agent's default `~/.m2/repository`, not our custom path. Our cache path was a directory that never existed.

**Options to fix**: (a) point the cache at `/home/vsts/.m2/repository`, or (b) force Maven to use our custom path via `MAVEN_OPTS=-Dmaven.repo.local=...` actually applied to the Maven task.

**Chosen**: drop the cache step entirely. The build takes 30s; cache saves ~15s. For a school project that builds maybe ten times total, 150 seconds saved isn't worth two more lines of YAML and the failure mode it brings.

---

## 6. Pipeline stages — detailed breakdown

### Stage `Build`

Defined at lines 41–95 of `azure-pipelines.yml`. Pool: `ubuntu-latest`.

| # | Task | Inputs | What it does | Why it's there |
|---|---|---|---|---|
| 1 | (implicit) `Checkout` | repo=self, branch=$(Build.SourceBranch) | Clones `Many-Attempts/MRP` to `$(Pipeline.Workspace)/s` | Without source, nothing to build. |
| 2 | `Maven@4` | pom.xml, goals=`clean package`, jdk=17, JUnit publish=true | Runs `mvn -B clean package`. Compiles, runs all 41 unit tests, packages the fat JAR into `target/MRP.jar`. Test results published to the run as a Test Run. | Satisfies the rubric's Build Pipeline test requirement; produces the JAR that the Dockerfile needs. |
| 3 | `AzureCLI@2` | azureSubscription=`sc-azure-rm`, scriptType=bash, inlineScript=`az acr login --name $(acrName)` | Uses the WIF service connection to exchange OIDC for an Entra token, then calls ACR's token endpoint to get a 3-hour push token. Writes it to `~/.docker/config.json`. | Authenticates Docker to ACR without storing a registry username/password anywhere. |
| 4 | `script:` (bash) | inline | `docker build -t $IMAGE:$(Build.BuildId) -t $IMAGE:latest -f Dockerfile .` then two `docker push` commands. | Produces the artifact (image) and uploads it to ACR with two tags: an immutable `$(Build.BuildId)` tag for traceability, and a moving `latest` for convenience. |
| 5 | `publish` artifact | $(Build.SourcesDirectory)/azure-pipelines.yml → pipeline-manifest | Uploads the YAML itself as a build artifact. | Audit trail — for any past run we can see *exactly* what pipeline definition produced it, in case the YAML was edited later. |

### Stage `Deploy_Staging`

Lines 97–129. Pool: `ubuntu-latest`. Environment: `staging` (no approvals).

| # | Task | What it does | Why it's there |
|---|---|---|---|
| 1 | `AzureWebAppContainer@1` | Updates `app-mrp-staging-38215` to use `acrmrp38215.azurecr.io/mrp:$(Build.BuildId)`. App Service pulls the image (via its MI's `AcrPull` role on the registry), starts the container, resolves the three Key Vault references at startup. | The actual deploy. |
| 2 | `AzureCLI@2` smoke check | `curl -fsS https://app-mrp-staging-38215.azurewebsites.net/` with up to 6 retries × 15s. | Cold-start can take 20-40s. Without retries, a single fast curl would race the container's startup and falsely fail the build. Confirms the app actually responds, not just that the deploy task succeeded. |

### Stage `Deploy_Production`

Lines 132–164. Pool: `ubuntu-latest`. Environment: `production` (Approvals check configured to require ≥1 approver).

| # | Task | What it does |
|---|---|---|
| (gate) | Approval check | The pipeline is paused here. The DevOps UI shows a "Review" button. Configured approver(s) click Approve or Reject. Pipeline does not proceed until ≥1 Approve. |
| 1 | `AzureWebAppContainer@1` | Same as staging, but targets `app-mrp-prod-38215`. |
| 2 | `AzureCLI@2` smoke check | Same as staging, but probes the prod URL. |

---

## 7. Commands used (chronological), with explanations

This is the reproducible runbook. If a teammate wants to redo the project from scratch, follow this section start-to-finish and you'll arrive at the same end state.

### Phase A — Local code and config changes

```bash
git clone https://github.com/Many-Attempts/MRP.git
cd MRP
git checkout -b feature/azure-pipelines
```

> Why a feature branch first, not `master` directly: `master` is the deploy trigger. Pushing untested YAML to `master` would have triggered failed deploys against real Azure. Feature branch first → green build → merge.

```bash
# Edit src/main/java/org/example/db/Database.java to read env vars.
# Edit pom.xml to add maven-shade-plugin.
# Create Dockerfile, .dockerignore, .env.example.
# Update .gitignore to exclude .env.
# Update docker-compose.yml to interpolate ${POSTGRES_USER} etc.
# Create azure-pipelines.yml.
# Create scripts/bootstrap-azure.ps1.
# Create docs/azure-setup.md.
# Create PROJECT_SUMMARY.md.

git add .gitignore .dockerignore .env.example Dockerfile docker-compose.yml \
        pom.xml README.md PROJECT_SUMMARY.md \
        azure-pipelines.yml docs/azure-setup.md \
        src/main/java/org/example/db/Database.java \
        src/main/java/org/example/Main.java
git commit -m "Add Azure Pipelines, containerize, move DB creds to env vars"
```

### Phase B — Local verification (before any Azure cost)

```powershell
# Verify the Docker build works (and Maven inside it).
docker build -t mrp:local-verify .

# Run the container without env vars to verify fail-fast.
docker run --rm mrp:local-verify
# Expected: java.lang.IllegalStateException: Required environment variable DB_URL is not set
```

> Why we test fail-fast explicitly: a working app with hardcoded fallback values would pass this test trivially. We need to *see* the IllegalStateException to know the env-var path is actually wired up.

```powershell
# Run JUnit tests via Maven inside a throwaway container (we have no local Maven).
docker run --rm -v "C:/Users/etien/Desktop/DevOpsProject/MRP:/workspace" -w /workspace `
   maven:3.9-eclipse-temurin-17 mvn -B test
# Expected: Tests run: 41, Failures: 0, Errors: 0
```

> Why run tests locally before pushing: avoids burning a CI run on a broken commit.

### Phase C — Azure resource bootstrap (driven by `scripts/bootstrap-azure.ps1`, equivalent CLI here)

```powershell
# One-time auth.
az login
az account show --query "{name:name, id:id}" -o table
# Expected: Azure for Students  1d9e4649-0f8a-4604-8fad-d45dfb40c9b5

# Pin variables for the rest of the session.
$RG = 'rg-mrp-dev'
$Loc = 'switzerlandnorth'   # only allowed region for our tenant
$Suffix = '38215'
```

```powershell
# Step 1: Register resource providers (a fresh Azure-for-Students subscription has none registered).
@('Microsoft.ContainerRegistry','Microsoft.DBforPostgreSQL','Microsoft.KeyVault',
  'Microsoft.Web','Microsoft.ManagedIdentity','Microsoft.Authorization') |
  ForEach-Object { az provider register --namespace $_ }
# Wait until all six show registrationState=Registered.
```

> Why we hit this: when you sign up for Azure-for-Students, no resource providers are registered. The first `az ... create` against a provider triggers a registration and may succeed eventually, but for a script we want to register all in one go.

```powershell
# Step 2: Resource group + ACR.
az group create -n $RG -l $Loc
az acr create -g $RG -n "acrmrp$Suffix" --sku Basic --admin-enabled false --location $Loc
```

> Why `--admin-enabled false`: the ACR admin user is a username/password pair stored in the registry. Disabling it forces all push/pull to go through Azure RBAC (App Service MI's `AcrPull`, pipeline's `az acr login` token). One less long-lived credential.

```powershell
# Step 3: Postgres flexible server + database.
$PgPassword = '<24 random chars from PowerShell Get-Random>'
az postgres flexible-server create -g $RG -n "psql-mrp-$Suffix" `
   --location $Loc --tier Burstable --sku-name Standard_B1ms `
   --storage-size 32 --version 16 `
   --admin-user mrpadmin --admin-password $PgPassword `
   --public-access 0.0.0.0 --yes
az postgres flexible-server db create -g $RG -s "psql-mrp-$Suffix" -d mrp_db
```

> Why `--public-access 0.0.0.0`: this is shorthand for "allow all Azure services". The App Service connects to Postgres via Azure's internal network; it doesn't need a public IP firewall rule. Tightening this further (private endpoint + VNet integration) would cost ~$50/month more for the VNet endpoint — out of scope.

```powershell
# Step 4: Key Vault + give myself Secrets Officer + set the three secrets.
az keyvault create -g $RG -n "kv-mrp-$Suffix" -l $Loc --enable-rbac-authorization true
$KvId = az keyvault show -n "kv-mrp-$Suffix" --query id -o tsv
$Me = az ad signed-in-user show --query id -o tsv
az role assignment create --role "Key Vault Secrets Officer" `
   --assignee-object-id $Me --assignee-principal-type User --scope $KvId

# Wait ~30 seconds for RBAC to propagate, otherwise the next set will 403.
Start-Sleep 30

$PgHost = az postgres flexible-server show -g $RG -n "psql-mrp-$Suffix" --query fullyQualifiedDomainName -o tsv
az keyvault secret set --vault-name "kv-mrp-$Suffix" --name DB-URL `
   --value "jdbc:postgresql://$($PgHost):5432/mrp_db?sslmode=require"
az keyvault secret set --vault-name "kv-mrp-$Suffix" --name DB-USER     --value mrpadmin
az keyvault secret set --vault-name "kv-mrp-$Suffix" --name DB-PASSWORD --value $PgPassword
```

> Why RBAC mode: the legacy "access policy" mode requires a separate UI/API to grant secret access. RBAC mode means everything in IAM uses the same Azure roles you already grant for any other resource. Simpler audit story.
>
> Why `Key Vault Secrets Officer` (not `Owner`): least-privilege. The role lets us read/write secrets but not delete the vault.

```powershell
# Step 5: Apply the schema. We have Docker but not psql — run psql in a throwaway container.
$myIp = (Invoke-RestMethod 'https://api.ipify.org?format=json').ip
az postgres flexible-server firewall-rule create -g $RG --name "psql-mrp-$Suffix" `
   --rule-name allow-bootstrap-host --start-ip-address $myIp --end-ip-address $myIp

docker run --rm -e PGPASSWORD=$PgPassword `
   -v "C:/Users/etien/Desktop/DevOpsProject/MRP/init.sql:/init.sql:ro" `
   postgres:16 psql "host=$PgHost user=mrpadmin dbname=mrp_db sslmode=require" -f /init.sql
```

> Why we add a firewall rule for our host then run psql in a container: avoids requiring a local psql install. `init.sql` ran clean except for one warning (`uuid-ossp` extension is not allowlisted on Azure-managed Postgres — confirmed unused, ignored).

```powershell
# Step 6: App Service plan + two Web Apps + their managed identities + role assignments.
az appservice plan create -g $RG -n asp-mrp-linux -l $Loc --is-linux --sku B1

$AcrId = az acr show -n "acrmrp$Suffix" --query id -o tsv
foreach ($app in @("app-mrp-staging-$Suffix", "app-mrp-prod-$Suffix")) {
    # Create with placeholder image; the pipeline will overwrite it.
    az webapp create -g $RG -p asp-mrp-linux -n $app `
       --deployment-container-image-name "mcr.microsoft.com/appsvc/staticsite:latest"

    # System-assigned managed identity.
    $mi = az webapp identity assign -g $RG -n $app --query principalId -o tsv

    # Grant the MI two roles: read secrets from KV, pull images from ACR.
    az role assignment create --role "Key Vault Secrets User" `
       --assignee-object-id $mi --assignee-principal-type ServicePrincipal --scope $KvId
    az role assignment create --role "AcrPull" `
       --assignee-object-id $mi --assignee-principal-type ServicePrincipal --scope $AcrId

    # Tell App Service to use the MI for ACR pulls.
    az webapp config set -g $RG -n $app `
       --generic-configurations '{\"acrUseManagedIdentityCreds\": true}'
}
```

> Why `--assignee-principal-type ServicePrincipal`: managed identities show up in Azure AD as "service principals" in the IAM model. Without this flag, `az role assignment create` may try to look the principal up by name in the user/group directory and fail.

```powershell
# Step 7: Wire Key Vault references into App Service settings.
# We have to use a JSON file because PowerShell mangles the (...) and ; characters
# when passing arguments through cmd.exe to Python.
$settings = @(
   @{ name='DB_URL'      ; value="@Microsoft.KeyVault(VaultName=kv-mrp-$Suffix;SecretName=DB-URL)"      ; slotSetting=$false }
   @{ name='DB_USER'     ; value="@Microsoft.KeyVault(VaultName=kv-mrp-$Suffix;SecretName=DB-USER)"     ; slotSetting=$false }
   @{ name='DB_PASSWORD' ; value="@Microsoft.KeyVault(VaultName=kv-mrp-$Suffix;SecretName=DB-PASSWORD)" ; slotSetting=$false }
   @{ name='WEBSITES_PORT'; value='8080'                                                                ; slotSetting=$false }
)
$jsonPath = Join-Path $env:TEMP 'mrp_appsettings.json'
$settings | ConvertTo-Json | Set-Content -Path $jsonPath -Encoding ASCII

foreach ($app in @("app-mrp-staging-$Suffix", "app-mrp-prod-$Suffix")) {
    az webapp config appsettings set -g $RG -n $app --settings "@$jsonPath"
}
```

> Why `WEBSITES_PORT=8080`: App Service for Containers defaults to expecting the container to listen on port 80. Our Java HttpServer binds to 8080. This setting tells App Service to route incoming traffic to that port instead.

### Phase D — DevOps wiring

UI clicks (no `az` equivalent for the GitHub OAuth flow):

1. **Service connection: GitHub** → OAuth → repo `Many-Attempts/MRP` → name `github.com_Many-Attempts`.
2. **Service connection: Azure Resource Manager** → Workload Identity Federation (automatic) → subscription `Azure for Students` → resource group `rg-mrp-dev` → name `sc-azure-rm`.
3. **Environments → New** → `staging` (no approvals).
4. **Environments → New** → `production` → ⋯ → Approvals and checks → + → Approvals → add yourself → minimum 1.
5. **Pipelines → New pipeline** → GitHub → `Many-Attempts/MRP` → branch `feature/azure-pipelines` → Existing YAML at `/azure-pipelines.yml` → Run.

CLI for triggering and inspecting runs:

```powershell
# Add the az devops extension (one-time).
az extension add --name azure-devops
az devops configure --defaults organization=https://dev.azure.com/DevOpsLessonsKost project='SWEN Project'

# Trigger a run on a specific branch.
$pipelineId = az pipelines list --query "[0].id" -o tsv
az pipelines run --id $pipelineId --branch master

# Inspect the timeline of a run.
az devops invoke --area build --resource Timeline `
   --route-parameters project='SWEN Project' buildId=6 `
   --org https://dev.azure.com/DevOpsLessonsKost --api-version 7.1-preview

# Cancel a hung run (no `az pipelines runs cancel` exists; PATCH directly).
'{"status":"cancelling"}' | Out-File $env:TEMP/cancel.json -Encoding ASCII
az devops invoke --area build --resource builds `
   --route-parameters project='SWEN Project' buildId=4 `
   --http-method PATCH --in-file $env:TEMP/cancel.json --api-version 7.1-preview
```

### Phase E — Verification

```powershell
# 1. Pipeline run is green.
az pipelines runs show --id 6 --query "{build:buildNumber,status:status,result:result}" -o table
# build         status     result
# -----------   ---------  ---------
# 20260504.6    completed  succeeded

# 2. Image is in ACR.
az acr repository show-tags --name acrmrp38215 --repository mrp --orderby time_desc -o table
# Result
# -------
# latest
# 6
# 3
# 2

# 3. Staging app is live and DB-connected.
Invoke-WebRequest -Uri https://app-mrp-staging-38215.azurewebsites.net/ -UseBasicParsing |
   Select-Object StatusCode, @{n='body';e={$_.Content.Substring(0, 80)}}
# StatusCode body
# ---------- ----
#        200 {"status":"ok","service":"Media Ratings Platform"}

# 4. Key Vault references (placeholder, never the resolved value).
az webapp config appsettings list -g rg-mrp-dev -n app-mrp-staging-38215 `
   --query "[?starts_with(value, '@Microsoft.KeyVault')]" -o table

# 5. WIF on the AzureRM service connection (no stored secret).
az devops service-endpoint list --query "[?name=='sc-azure-rm'].{name:name,authType:authorization.scheme}" -o table
# Name         AuthType
# -----------  --------------------------
# sc-azure-rm  WorkloadIdentityFederation
```

---

## 8. Trouble we hit (a real-world post-mortem)

This section exists because a "happy path" project report tells the grader nothing about how you actually do DevOps. Real DevOps is debugging.

| # | Problem | Symptom | Root cause | Fix |
|---|---|---|---|---|
| 1 | `bootstrap-azure.ps1` parser error on first run | `Schließende ")" fehlt in einem Ausdruck` at line 102 | PowerShell expanded `$Kv;SecretName...` as a *scope-qualified variable expression* inside the double-quoted `"@Microsoft.KeyVault(VaultName=$Kv;SecretName=DB-URL)"` literal. | Switched to single-quoted strings + concatenation: `'@Microsoft.KeyVault(VaultName=' + $Kv + ';SecretName=DB-URL)'`. Single-quoted strings do *zero* interpolation, so no parsing edge cases. |
| 2 | First ACR creation rejected | `RequestDisallowedByAzure: ...best available regions where your subscription can deploy resources` | Azure-for-Students tenant policy `sys.regionrestriction` excludes most regions including `westeurope`. | Queried the policy parameters, discovered the allowed list (`uaenorth, spaincentral, switzerlandnorth, italynorth, polandcentral`). Recreated the resource group in `switzerlandnorth`. |
| 3 | ACR creation rejected (round 2) | `MissingSubscriptionRegistration: ...not registered to use namespace 'Microsoft.ContainerRegistry'` | Fresh student subscription — no resource providers were auto-registered. | Ran `az provider register --namespace ...` for all six providers we'd use; polled `registrationState` until all = `Registered`; retried. |
| 4 | `init.sql` printed an error mid-stream | `extension "uuid-ossp" is not allow-listed for users in Azure Database for PostgreSQL` | Azure-managed Postgres maintains an allow-list of installable extensions; `uuid-ossp` is not on it by default. | Read `init.sql` — the extension is declared but unused (Java's `UUIDGenerator.generateUUIDv7()` produces all UUIDs, never `uuid_generate_v4()`). Confirmed all `CREATE TABLE` statements after the failure executed normally; ignored the warning. |
| 5 | `az webapp config appsettings set` argv mangling | `"DB_USER" kann syntaktisch an dieser Stelle nicht verarbeitet werden` (German PowerShell error) | The `az` CLI on Windows is a `.cmd` wrapper around Python. PowerShell's argument escaping for arguments containing `(`, `)`, and `;` (Key Vault reference syntax) doesn't survive the cmd.exe layer cleanly. | Wrote the four settings to `$env:TEMP/mrp_appsettings.json` and used `--settings "@<path>"`. Filenames don't have shell-special characters, so no escaping issues. |
| 6 | Pipeline run #1 pushed to Docker Hub instead of ACR | Logs showed `docker push mrp:1` (no registry prefix); image landed at `docker.io/library/mrp` | The `sc-acr` service connection had been created in the DevOps UI with **Registry Type: Others** and the default URL `https://hub.docker.com/`. The Docker@2 task fell through to a generic Docker Hub push. | Replaced the Docker@2 step with `az acr login` over `sc-azure-rm` plus an inline `script:` doing `docker build` / `docker push` against the fully-qualified ACR image name. Deleted the broken `sc-acr` connection. |
| 7 | Pipeline run #2 marked failed | "Post-job: Cache Maven local repo" task failed with `tar: /home/vsts/work/1/.m2/repository: Cannot open: No such file or directory` | The `Cache@2` task was pointed at `$(Pipeline.Workspace)/.m2/repository`, but the Maven@4 task uses `~/.m2/repository` by default. The cache path was a directory that never got populated. | Removed the `Cache@2` step entirely. Saves only ~15s on a 30s build; not worth the configuration complexity for a school project. |
| 8 | Pipeline run #4 never started | Job stayed `pending` for 17 minutes; `usedCount: 0`, `resourceLimit: null` on the hosted pool | At first looked like a parallelism quota issue (Microsoft requires a form-fill grant for new orgs to use free hosted agents). However, runs 1-3 had completed normally — meaning the org *did* have parallelism. The actual cause was Microsoft-hosted capacity at peak hours. | Cancelled run #4. Re-triggered as run #6 — agent picked up after 2:32 minutes. (For the longer term, filing the parallelism grant is on the future-improvements list to make queue times more consistent.) |

---

## 9. File-by-file summary of what we added/changed

| Path | Status | Purpose |
|---|---|---|
| `azure-pipelines.yml` | new | The multi-stage pipeline definition. |
| `Dockerfile` | new | Multi-stage container build (Maven → Alpine JRE). |
| `.dockerignore` | new | Keep `target/`, `.git/`, etc. out of the build context. |
| `.env.example` | new | Documents the env-var keys the app needs (no real values). |
| `.gitignore` | edited | Added `.env`, `.env.*` exclusions and a `!.env.example` override. |
| `docker-compose.yml` | edited | Switched hardcoded `postgres/postgres` to `${POSTGRES_USER}` / `${POSTGRES_PASSWORD}` / `${POSTGRES_DB}` interpolation with `${VAR:?...}` syntax that errors out if unset. |
| `pom.xml` | edited | Added `maven-shade-plugin` (with `ServicesResourceTransformer`) so `mvn package` produces a runnable `target/MRP.jar`. |
| `src/main/java/org/example/db/Database.java` | edited | Replaced hardcoded `URL`/`USER`/`PASSWORD` constants with `requireEnv("DB_URL")` etc., called lazily inside `connect()`, with `IllegalStateException` on missing values. |
| `src/main/java/org/example/Main.java` | edited | Trivial — fixed the startup banner, which used to say "PostgreSQL on localhost:5433" (and was lying — it was 5434). |
| `scripts/bootstrap-azure.ps1` | new | One-shot PowerShell script equivalent to the bash bootstrap in `docs/azure-setup.md`, for Windows users. |
| `docs/azure-setup.md` | new | Step-by-step bootstrap walkthrough (bash version + DevOps UI clicks). |
| `docs/project-overview.md` | new (this file) | The deep technical writeup. |
| `PROJECT_SUMMARY.md` | new | One-page project summary — the assignment's hard requirement. |
| `README.md` | edited | Added quick-start, env-var docs, security posture, links to the two doc files. |

---

## 10. Future improvements / known limits

- **Apply for Microsoft-hosted parallelism grant** at https://aka.ms/azpipelines-parallelism-request. Capacity delays would become rare.
- **Integration tests** with a Testcontainers Postgres in the build stage. Right now the 41 unit tests mock the repository layer; we have no automated check that the JDBC SQL is valid against a real DB.
- **Application Insights** wired to App Service. Currently the only observability is `az webapp log tail` — fine for poking around, useless for trend analysis.
- **App Service plan upgrade to S1** would enable deployment slots → blue/green deploys with zero-downtime swap, instead of the two-Web-App pattern. Cost: +$56/month, only worth it for production traffic.
- **Private endpoint for Postgres** to remove `--public-access 0.0.0.0`. Cost: ~$50/month for the VNet endpoint. Out of scope for the school project.

---

## 11. Teardown

When the assignment is graded and you no longer need the resources:

```powershell
az group delete -n rg-mrp-dev --yes --no-wait
```

That single command removes the RG, ACR, Postgres server, Key Vault, App Service plan, and both Web Apps in one shot — Azure handles cascading deletes automatically. Verify after a few minutes:

```powershell
az group exists -n rg-mrp-dev   # should print: false
```

The Azure DevOps project, pipeline definition, environments, and service connections persist (no Azure cost, just metadata). Delete those manually from the DevOps UI if desired.
