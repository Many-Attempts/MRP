# `infra/` — Bicep IaC for MRP

This directory holds the declarative Azure infrastructure for the Media Ratings Platform. It replaces the imperative `scripts/bootstrap-azure.ps1` while reproducing exactly the same resources (suffix `38215`, region `switzerlandnorth`).

## What gets deployed

A single `main.bicep` declares everything inside `rg-mrp-dev`:

- `acrmrp38215` — Azure Container Registry (Basic SKU)
- `psql-mrp-38215` — PostgreSQL Flexible Server (Burstable B1ms, v16) + `mrp_db` database + firewall rule for Azure-internal services
- `kv-mrp-38215` — Key Vault (RBAC mode) + `DB-URL` / `DB-USER` / `DB-PASSWORD` secrets
- `asp-mrp-linux` — Linux App Service Plan (B1)
- `app-mrp-staging-38215` and `app-mrp-prod-38215` — Web Apps for Containers with system-assigned managed identities
- 5 role assignments:
  - Deployer → **Key Vault Secrets Officer** on the vault
  - Each app's MI → **Key Vault Secrets User** on the vault
  - Each app's MI → **AcrPull** on the registry

App settings on both Web Apps reference Key Vault directly (`@Microsoft.KeyVault(VaultName=...;SecretName=...)`); secrets never appear in the pipeline.

## How to deploy

### One-time setup

Install the Bicep extension in VSCode if you haven't already:

```powershell
code --install-extension ms-azuretools.vscode-bicep
```

Ensure you're signed in to Azure CLI against the right subscription:

```powershell
az login
az account set --subscription "Azure for Students"
```

### Deploy from VSCode

1. Right-click `infra/main.bicep` in the file explorer → **"Deploy Bicep File…"**
2. Select your subscription → select **`rg-mrp-dev`**
3. Pick `infra/main.parameters.json` when prompted for a parameters file
4. The extension will prompt for the two parameters that have no default:

   - **`pgAdminPassword`** — the **existing** Postgres admin password. Re-deploying with a different value rotates the admin password and breaks the running apps until KV is re-seeded. Fetch the current value:
     ```powershell
     az keyvault secret show --vault-name kv-mrp-38215 --name DB-PASSWORD --query value -o tsv
     ```
   - **`deployerObjectId`** — your AAD object ID, used to grant you `Key Vault Secrets Officer`. The parameters file already has it pre-filled; only override if you're a different user:
     ```powershell
     az ad signed-in-user show --query id -o tsv
     ```

5. Click **Deploy**. Wait ~5 min (Postgres takes the longest).

### Preview changes first (`what-if`)

Command palette → **"Bicep: Deploy Bicep File (What-If)"**. Same prompts, but Azure tells you what *would* change without applying. Use this before any re-deploy.

Expected output on a parity re-deploy: mostly `Unchanged` / `Ignore`, with one `Modify` on each Web App's `linuxFxVersion` (the pipeline overwrites this on every app deploy, so the live image tag drifts from the Bicep default `mrp:latest`).

## Re-deploy gotchas

| Symptom | Cause | Fix |
|---|---|---|
| `BadRequest: The vault name 'kv-mrp-38215' is already in use` after a delete | Key Vault names are reserved 90 days after soft-delete | `az keyvault purge -n kv-mrp-38215` (only works in dev — keep purge protection off) |
| Apps start returning 500 / can't connect to DB after re-deploy | You passed a **new** `pgAdminPassword` instead of the existing one — Postgres rotated the admin password but the KV secret update lags | Always run the `az keyvault secret show` command first and paste the result |
| `linuxFxVersion` drift shows in every `what-if` | Bicep sets `mrp:latest`; `AzureWebAppContainer@1` overwrites with `:$(Build.BuildId)` on each app deploy | Expected. Either ignore or pass `-p containerImage=mrp:<buildId>` to match current state |
| ACR / KV / Postgres name conflict | Names are globally unique across all Azure tenants | Re-randomize `suffix` parameter; you'll also need to update `azure-pipelines.yml` variables |

## Relationship to `azure-pipelines.yml`

The pipeline does not run Bicep. The Bicep outputs match the variable names the pipeline hard-codes today (`acrName`, `appNameStaging`, `appNameProd`, `resourceGroup`), so the pipeline keeps working unchanged after a Bicep deploy.

If you ever want to wire the pipeline to consume Bicep outputs dynamically, add an `AzureCLI@2` step at the start of Stage 1:

```yaml
- task: AzureCLI@2
  inputs:
    azureSubscription: '$(azureSubscription)'
    scriptType: bash
    scriptLocation: inlineScript
    inlineScript: |
      out=$(az deployment group show -g rg-mrp-dev -n main --query properties.outputs -o json)
      echo "##vso[task.setvariable variable=acrName]$(echo $out | jq -r .acrName.value)"
      echo "##vso[task.setvariable variable=appNameStaging]$(echo $out | jq -r .appNameStaging.value)"
      echo "##vso[task.setvariable variable=appNameProd]$(echo $out | jq -r .appNameProd.value)"
```

Not required for this project.

## Relationship to `scripts/bootstrap-azure.ps1`

The old PowerShell bootstrap is left in place as a reference. Once you've validated the Bicep deploys produce identical infra (run `what-if` and confirm zero structural changes), the bootstrap can be moved to `scripts/legacy/`.
