# Azure Setup — One-Time Bootstrap

Everything in this document is **one-time setup** that produces the Azure resources and Azure DevOps service connections referenced by `azure-pipelines.yml`. After it's done, every push to `master` will build, test, and deploy automatically (with a manual approval gate before production).

> **No secrets in Git.** DB credentials live only in Azure Key Vault. The pipeline never sees them — the App Service injects them at runtime via Managed Identity + Key Vault references.

---

## Prerequisites

- Azure subscription with Owner/Contributor + User Access Administrator on the subscription (needed to grant role assignments).
- Azure DevOps organization + project.
- `az` CLI installed locally and signed in (`az login`).
- The MRP repo connected to the Azure DevOps project (or accessible via GitHub service connection).

---

## Part 1 — Azure resources (run from PowerShell or bash)

> The block below uses **bash** syntax. On Windows, run it in **WSL** or **Git Bash**, or translate to PowerShell. Lines with `$RANDOM` produce a different value each run — capture them once and reuse.

```bash
# ---- Variables (edit names if you like) ----
RG=rg-mrp-dev
LOC=westeurope
SUFFIX=$RANDOM                 # one suffix used for everything globally-named
ACR=acrmrp$SUFFIX              # alpha-numeric only, 5-50 chars
KV=kv-mrp-$SUFFIX              # 3-24 chars, alphanumeric + hyphen
PG=psql-mrp-$SUFFIX
PLAN=asp-mrp-linux
APP_STAGING=app-mrp-staging-$SUFFIX
APP_PROD=app-mrp-prod-$SUFFIX
PG_ADMIN=mrpadmin
PG_PASSWORD="$(openssl rand -base64 24 | tr -d '/=+' | cut -c1-24)"
echo "Capture this Postgres admin password (you'll only see it once): $PG_PASSWORD"

# ---- 1. Resource group + ACR ----
az group create -n $RG -l $LOC
az acr create -g $RG -n $ACR --sku Basic --admin-enabled false

# ---- 2. Azure Database for PostgreSQL Flexible Server (Burstable B1ms) ----
az postgres flexible-server create -g $RG -n $PG \
  --tier Burstable --sku-name Standard_B1ms \
  --storage-size 32 --version 16 \
  --admin-user $PG_ADMIN --admin-password "$PG_PASSWORD" \
  --public-access 0.0.0.0 \
  --yes
az postgres flexible-server db create -g $RG -s $PG -d mrp_db

# Apply init.sql (one-shot schema seed). Paste the file contents or use psql.
PG_HOST=$(az postgres flexible-server show -g $RG -n $PG --query fullyQualifiedDomainName -o tsv)
echo "Postgres host: $PG_HOST"
# Optional schema seed (run once from your machine):
# PGPASSWORD="$PG_PASSWORD" psql "host=$PG_HOST user=$PG_ADMIN dbname=mrp_db sslmode=require" -f init.sql

# ---- 3. Key Vault + secrets ----
az keyvault create -g $RG -n $KV --enable-rbac-authorization true

# Give your own user permission to write secrets
KV_ID=$(az keyvault show -n $KV --query id -o tsv)
ME=$(az ad signed-in-user show --query id -o tsv)
az role assignment create --role "Key Vault Secrets Officer" --assignee $ME --scope $KV_ID

az keyvault secret set --vault-name $KV --name DB-URL \
  --value "jdbc:postgresql://$PG_HOST:5432/mrp_db?sslmode=require"
az keyvault secret set --vault-name $KV --name DB-USER --value "$PG_ADMIN"
az keyvault secret set --vault-name $KV --name DB-PASSWORD --value "$PG_PASSWORD"

# ---- 4. App Service plan + two Web Apps for Containers (staging + prod) ----
az appservice plan create -g $RG -n $PLAN --is-linux --sku B1

# Use a tiny placeholder image for first creation; the pipeline will overwrite it.
PLACEHOLDER='mcr.microsoft.com/appsvc/staticsite:latest'
for APP in $APP_STAGING $APP_PROD; do
  az webapp create -g $RG -p $PLAN -n $APP --deployment-container-image-name "$PLACEHOLDER"
  az webapp identity assign -g $RG -n $APP
  APP_MI=$(az webapp identity show -g $RG -n $APP --query principalId -o tsv)

  # Grant managed identity: Key Vault Secrets User + AcrPull
  az role assignment create --role "Key Vault Secrets User" --assignee $APP_MI --scope $KV_ID
  ACR_ID=$(az acr show -n $ACR --query id -o tsv)
  az role assignment create --role AcrPull --assignee $APP_MI --scope $ACR_ID

  # Wire Key Vault refs into App Service settings (resolved at container start)
  az webapp config appsettings set -g $RG -n $APP --settings \
    DB_URL="@Microsoft.KeyVault(VaultName=$KV;SecretName=DB-URL)" \
    DB_USER="@Microsoft.KeyVault(VaultName=$KV;SecretName=DB-USER)" \
    DB_PASSWORD="@Microsoft.KeyVault(VaultName=$KV;SecretName=DB-PASSWORD)" \
    WEBSITES_PORT=8080

  # Tell the Web App to pull images from our ACR using its managed identity
  az webapp config set -g $RG -n $APP --generic-configurations '{"acrUseManagedIdentityCreds": true}'
done

echo
echo "=== Capture these values for azure-pipelines.yml variables ==="
echo "acrName:         $ACR"
echo "appNameStaging:  $APP_STAGING"
echo "appNameProd:     $APP_PROD"
echo "resourceGroup:   $RG"
echo "Key Vault:       $KV"
```

When the script finishes, **edit `azure-pipelines.yml`** and replace the `REPLACE` placeholders in the `variables:` block with the values printed at the end.

---

## Part 2 — Azure DevOps wiring (UI clicks)

### 2.1 Use your existing project

This team already has the **SWEN Project** in the **DevOpsLessonsKost** organization
(https://dev.azure.com/DevOpsLessonsKost/SWEN%20Project) — re-use it.

> If you're following this doc fresh: **+ New Project** → name it `MRP` → Visibility *Private* → Create.

### 2.2 Connect the source code

Pick **one** path:

**Option A — Source stays on GitHub (recommended):**
1. *Project Settings* → *Service connections* → *New service connection* → **GitHub**.
2. Authorize via OAuth → select repo `Many-Attempts/MRP` → name it `sc-github` → Save.
3. *Pipelines* → *New pipeline* → **GitHub** → pick `Many-Attempts/MRP` → **Existing Azure Pipelines YAML file** → `/azure-pipelines.yml`.

**Option B — Mirror into Azure Repos:**
1. *Repos* → Import repository → URL `https://github.com/Many-Attempts/MRP.git`.
2. *Pipelines* → *New pipeline* → Azure Repos Git → pick the repo → existing YAML.

### 2.3 Create the AzureRM service connection (Workload Identity Federation)

1. *Project Settings* → *Service connections* → *New service connection* → **Azure Resource Manager**.
2. Identity type: **Workload Identity Federation (automatic)**.
3. Scope level: **Subscription** → pick your sub → resource group `rg-mrp-dev`.
4. Name it **`sc-azure-rm`** (must match `azureSubscription` in `azure-pipelines.yml`).
5. ✅ Grant access to all pipelines → Save.

> Workload identity federation issues short-lived tokens via OIDC — **no client secret is stored anywhere**.

### 2.4 Create the ACR Docker Registry service connection

1. *Project Settings* → *Service connections* → *New service connection* → **Docker Registry**.
2. Registry type: **Azure Container Registry**.
3. Authentication: **Service Principal** (or use the AzureRM connection if your DevOps version supports it).
4. Subscription → registry → pick `acrmrp<SUFFIX>`.
5. Name it **`sc-acr`** (must match `acrServiceConnection` in `azure-pipelines.yml`).

### 2.5 Create environments + approval gate

1. *Pipelines* → *Environments* → *New environment*.
2. Create **`staging`** (resource: None) → Save.
3. Create **`production`** (resource: None) → Save.
4. Open **`production`** → **⋯** menu → *Approvals and checks* → *+* → **Approvals**.
5. Add yourself (and any teammate) as required approvers → minimum 1 → Save.

### 2.6 (Optional) Variable group linked to Key Vault

This isn't required for runtime DB creds (those flow Key Vault → App Service directly), but you may want it for **build-time** secrets in the future.

1. *Pipelines* → *Library* → *+ Variable group* → name `mrp-shared`.
2. Toggle **Link secrets from an Azure Key Vault as variables**.
3. Pick the AzureRM connection → vault `kv-mrp-<SUFFIX>` → Authorize.
4. *+ Add* → pick which secrets to expose → Save.

---

## Part 3 — First pipeline run

1. Edit `azure-pipelines.yml` → replace the four `REPLACE` placeholders → commit + push to `master`.
2. The Build stage runs Maven, publishes JUnit results, builds + pushes the image to ACR.
3. **Deploy_Staging** runs automatically → smoke-check probes `https://app-mrp-staging-<SUFFIX>.azurewebsites.net/`.
4. **Deploy_Production** waits for your approval in the DevOps UI → click *Approve*.
5. Tail logs to confirm DB connection:
   ```bash
   az webapp log tail -g rg-mrp-dev -n $APP_STAGING
   ```
   You should see `Connected to PostgreSQL database!`.

---

## Cost estimate

| Resource | SKU | Approx /month |
|----------|-----|---------------|
| ACR | Basic | $5 |
| App Service Plan (Linux) | B1 | $13 (one plan hosts both Web Apps) |
| Postgres Flexible Server | B1ms, 32GB | $13 |
| Key Vault | Standard | <$0.10 |
| **Total** | | **~$31/month** |

Azure for Students $100 credit covers ~3 months. Delete the resource group with `az group delete -n $RG --yes --no-wait` when done.

---

## Verification checklist

- [ ] `az keyvault secret list --vault-name $KV` shows `DB-URL`, `DB-USER`, `DB-PASSWORD`.
- [ ] App Service → Configuration: each `DB_*` setting shows a green "Key Vault Reference" indicator.
- [ ] `git grep -nE 'postgres:postgres|jdbc:postgresql://localhost:5434/mrp_db'` returns only `.env.example` (no real creds).
- [ ] Project Settings → Service connections → `sc-azure-rm` reads "Workload Identity Federation".
- [ ] Pipeline run shows three stages: green ✓ Build, green ✓ Staging, paused ⏸ Production until approved.
- [ ] Hitting `https://<staging-app>.azurewebsites.net/` returns the MRP server (or any of its endpoints).

---

## Teardown (when done with the assignment)

```bash
az group delete -n $RG --yes --no-wait
```

This removes the App Services, Postgres, ACR, and Key Vault in one shot.
