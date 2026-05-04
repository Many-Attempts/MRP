<#
  bootstrap-azure.ps1
  ---------------------
  Provisions the Azure resources used by the SWEN-Project MRP pipeline:
    - Resource group, ACR, Postgres Flexible Server, Key Vault
    - App Service Plan (Linux B1) + two Web Apps for Containers (staging + prod)
    - Wires Key Vault references into App Service settings
    - Grants the Web App's managed identity AcrPull + Key Vault Secrets User

  Run from PowerShell after `az login`. Idempotent-ish: re-running with the same
  $Suffix will mostly succeed by reusing existing resources.

  At the end it prints the values you must paste into azure-pipelines.yml.
#>

[CmdletBinding()]
param(
    [string] $ResourceGroup = "rg-mrp-dev",
    [string] $Location      = "westeurope",
    [string] $Suffix        = ([string](Get-Random -Minimum 10000 -Maximum 99999)),
    [string] $PgAdmin       = "mrpadmin"
)

$ErrorActionPreference = "Stop"

# Names derived from the suffix
$Acr        = "acrmrp$Suffix"
$Kv         = "kv-mrp-$Suffix"
$Pg         = "psql-mrp-$Suffix"
$Plan       = "asp-mrp-linux"
$AppStaging = "app-mrp-staging-$Suffix"
$AppProd    = "app-mrp-prod-$Suffix"

# Generate a random Postgres admin password (24 chars, no shell-unfriendly symbols)
$alphabet  = (48..57) + (65..90) + (97..122)
$PgPassword = -join ($alphabet | Get-Random -Count 24 | ForEach-Object { [char]$_ })

Write-Host ""
Write-Host "==== SWEN-Project / MRP Azure bootstrap ====" -ForegroundColor Cyan
Write-Host "Resource group : $ResourceGroup"
Write-Host "Location       : $Location"
Write-Host "Suffix         : $Suffix"
Write-Host "ACR            : $Acr"
Write-Host "Key Vault      : $Kv"
Write-Host "Postgres       : $Pg (admin: $PgAdmin)"
Write-Host "App (staging)  : $AppStaging"
Write-Host "App (prod)     : $AppProd"
Write-Host ""
Write-Host "Postgres admin password (write this down NOW): $PgPassword" -ForegroundColor Yellow
Write-Host ""
Read-Host "Press Enter to continue, or Ctrl+C to abort"

function Step($msg) { Write-Host "`n--- $msg ---" -ForegroundColor Green }

# 1. Resource group + ACR
Step "Creating resource group + ACR"
az group create -n $ResourceGroup -l $Location | Out-Null
az acr create -g $ResourceGroup -n $Acr --sku Basic --admin-enabled false | Out-Null

# 2. Postgres Flexible Server
Step "Creating Postgres Flexible Server (Burstable B1ms) — takes ~5 min"
az postgres flexible-server create `
    -g $ResourceGroup -n $Pg `
    --tier Burstable --sku-name Standard_B1ms `
    --storage-size 32 --version 16 `
    --admin-user $PgAdmin --admin-password $PgPassword `
    --public-access 0.0.0.0 `
    --yes | Out-Null

az postgres flexible-server db create -g $ResourceGroup -s $Pg -d mrp_db | Out-Null
$PgHost = az postgres flexible-server show -g $ResourceGroup -n $Pg --query fullyQualifiedDomainName -o tsv
Write-Host "Postgres FQDN: $PgHost"

# 3. Key Vault + secrets
Step "Creating Key Vault and seeding secrets"
az keyvault create -g $ResourceGroup -n $Kv --enable-rbac-authorization true | Out-Null
$KvId = az keyvault show -n $Kv --query id -o tsv
$Me = az ad signed-in-user show --query id -o tsv
az role assignment create --role "Key Vault Secrets Officer" --assignee $Me --scope $KvId | Out-Null

$JdbcUrl = "jdbc:postgresql://$PgHost`:5432/mrp_db?sslmode=require"
az keyvault secret set --vault-name $Kv --name DB-URL      --value $JdbcUrl    | Out-Null
az keyvault secret set --vault-name $Kv --name DB-USER     --value $PgAdmin    | Out-Null
az keyvault secret set --vault-name $Kv --name DB-PASSWORD --value $PgPassword | Out-Null

# 4. App Service plan + two Web Apps
Step "Creating App Service plan (Linux B1) and two Web Apps"
az appservice plan create -g $ResourceGroup -n $Plan --is-linux --sku B1 | Out-Null

$Placeholder = "mcr.microsoft.com/appsvc/staticsite:latest"
$AcrId = az acr show -n $Acr --query id -o tsv

foreach ($app in @($AppStaging, $AppProd)) {
    Write-Host "  -> $app"
    az webapp create -g $ResourceGroup -p $Plan -n $app --deployment-container-image-name $Placeholder | Out-Null
    az webapp identity assign -g $ResourceGroup -n $app | Out-Null
    $appMi = az webapp identity show -g $ResourceGroup -n $app --query principalId -o tsv

    az role assignment create --role "Key Vault Secrets User" --assignee $appMi --scope $KvId | Out-Null
    az role assignment create --role "AcrPull"                --assignee $appMi --scope $AcrId | Out-Null

    $kvUrl   = "@Microsoft.KeyVault(VaultName=$Kv;SecretName=DB-URL)"
    $kvUser  = "@Microsoft.KeyVault(VaultName=$Kv;SecretName=DB-USER)"
    $kvPass  = "@Microsoft.KeyVault(VaultName=$Kv;SecretName=DB-PASSWORD)"

    az webapp config appsettings set -g $ResourceGroup -n $app --settings `
        "DB_URL=$kvUrl" `
        "DB_USER=$kvUser" `
        "DB_PASSWORD=$kvPass" `
        "WEBSITES_PORT=8080" | Out-Null

    az webapp config set -g $ResourceGroup -n $app `
        --generic-configurations '{"acrUseManagedIdentityCreds": true}' | Out-Null
}

# 5. Print the values to paste into azure-pipelines.yml
Write-Host ""
Write-Host "==== DONE — paste these into azure-pipelines.yml variables: block ====" -ForegroundColor Cyan
Write-Host "  acrName:        '$Acr'"
Write-Host "  appNameStaging: '$AppStaging'"
Write-Host "  appNameProd:    '$AppProd'"
Write-Host "  resourceGroup:  '$ResourceGroup'"
Write-Host ""
Write-Host "Key Vault:       $Kv"
Write-Host "Postgres host:   $PgHost"
Write-Host "Postgres pass:   $PgPassword     <-- save this securely (it is also in Key Vault as DB-PASSWORD)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Next step: apply init.sql to seed schema, then commit + push the pipeline file." -ForegroundColor Cyan
Write-Host "  PGPASSWORD='$PgPassword' psql `"host=$PgHost user=$PgAdmin dbname=mrp_db sslmode=require`" -f init.sql"
