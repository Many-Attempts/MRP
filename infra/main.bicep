// MRP — Azure infrastructure (resource-group scope)
//
// Source-of-truth Bicep for everything inside rg-mrp-dev.
// Generated from `az group export -g rg-mrp-dev` + `az bicep decompile`,
// then cleaned: read-only props stripped, parameters lifted, role assignments
// added, KV secret values + Postgres database + appSettings hand-added
// (none of these export cleanly from ARM).
//
// Deploy via the VSCode Bicep extension: right-click this file → "Deploy
// Bicep File…". You'll be prompted for `pgAdminPassword` and `deployerObjectId`
// — the README shows the two `az` commands that fetch them.

targetScope = 'resourceGroup'

// ===== Parameters ============================================================

@description('Azure region for all resources')
param location string = 'switzerlandnorth'

@description('Suffix appended to globally-unique resource names (matches deployed infra)')
@minLength(4)
@maxLength(8)
param suffix string = '38215'

@description('PostgreSQL admin username')
param pgAdminUser string = 'mrpadmin'

@description('PostgreSQL admin password. Pull from KV before re-deploy: az keyvault secret show --vault-name kv-mrp-38215 --name DB-PASSWORD --query value -o tsv')
@secure()
@minLength(16)
param pgAdminPassword string

@description('AAD object ID of the deployer (gets Key Vault Secrets Officer). az ad signed-in-user show --query id -o tsv')
param deployerObjectId string

@description('Container image reference (repo:tag, no registry). Pipeline overwrites the tag on every deploy.')
param containerImage string = 'mrp:latest'

@description('Optional client IP allowed to reach Postgres directly (for seeding init.sql). Empty disables the rule.')
param bootstrapHostIp string = ''

// ===== Derived names =========================================================

var acrName        = 'acrmrp${suffix}'
var kvName         = 'kv-mrp-${suffix}'
var pgName         = 'psql-mrp-${suffix}'
var planName       = 'asp-mrp-linux'
var appStagingName = 'app-mrp-staging-${suffix}'
var appProdName    = 'app-mrp-prod-${suffix}'
var dbName         = 'mrp_db'

// Built-in role definition GUIDs
var roleKvSecretsOfficer = 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7'
var roleKvSecretsUser    = '4633458b-17de-408a-b874-0445c86b69e6'
var roleAcrPull          = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

// ===== Container Registry ===================================================

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
  }
}

// ===== PostgreSQL Flexible Server + database + firewall rules ===============

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2023-12-01-preview' = {
  name: pgName
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    administratorLogin: pgAdminUser
    administratorLoginPassword: pgAdminPassword
    version: '16'
    storage: {
      storageSizeGB: 32
      autoGrow: 'Disabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    network: {
      publicNetworkAccess: 'Enabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    authConfig: {
      activeDirectoryAuth: 'Disabled'
      passwordAuth: 'Enabled'
    }
  }
}

resource postgresDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-12-01-preview' = {
  parent: postgres
  name: dbName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

resource postgresFwAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-12-01-preview' = {
  parent: postgres
  name: 'AllowAllAzureServicesAndResourcesWithinAzureIps'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource postgresFwBootstrapHost 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-12-01-preview' = if (!empty(bootstrapHostIp)) {
  parent: postgres
  name: 'allow-bootstrap-host'
  properties: {
    startIpAddress: bootstrapHostIp
    endIpAddress: bootstrapHostIp
  }
}

// ===== Key Vault + secrets + deployer role assignment ======================

resource kv 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: kvName
  location: location
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 90
    publicNetworkAccess: 'Enabled'
  }
}

resource kvSecretDbUrl 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: kv
  name: 'DB-URL'
  properties: {
    value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${dbName}?sslmode=require'
  }
  dependsOn: [
    postgresDb
  ]
}

resource kvSecretDbUser 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: kv
  name: 'DB-USER'
  properties: {
    value: pgAdminUser
  }
}

resource kvSecretDbPassword 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: kv
  name: 'DB-PASSWORD'
  properties: {
    value: pgAdminPassword
  }
}

resource kvRoleDeployer 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: kv
  name: guid(kv.id, deployerObjectId, roleKvSecretsOfficer)
  properties: {
    principalId: deployerObjectId
    principalType: 'User'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKvSecretsOfficer)
  }
}

// ===== App Service Plan ====================================================

resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: planName
  location: location
  kind: 'linux'
  sku: {
    name: 'B1'
    tier: 'Basic'
  }
  properties: {
    reserved: true
  }
}

// ===== Two Web Apps for Containers =========================================

var siteConfigCommon = {
  linuxFxVersion: 'DOCKER|${acr.properties.loginServer}/${containerImage}'
  acrUseManagedIdentityCreds: true
  ftpsState: 'FtpsOnly'
  minTlsVersion: '1.2'
  appSettings: [
    {
      name: 'DB_URL'
      value: '@Microsoft.KeyVault(VaultName=${kvName};SecretName=DB-URL)'
    }
    {
      name: 'DB_USER'
      value: '@Microsoft.KeyVault(VaultName=${kvName};SecretName=DB-USER)'
    }
    {
      name: 'DB_PASSWORD'
      value: '@Microsoft.KeyVault(VaultName=${kvName};SecretName=DB-PASSWORD)'
    }
    {
      name: 'WEBSITES_PORT'
      value: '8080'
    }
  ]
}

resource appStaging 'Microsoft.Web/sites@2023-12-01' = {
  name: appStagingName
  location: location
  kind: 'app,linux,container'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    reserved: true
    httpsOnly: false
    keyVaultReferenceIdentity: 'SystemAssigned'
    siteConfig: siteConfigCommon
  }
  dependsOn: [
    kvSecretDbUrl
    kvSecretDbUser
    kvSecretDbPassword
  ]
}

resource appProd 'Microsoft.Web/sites@2023-12-01' = {
  name: appProdName
  location: location
  kind: 'app,linux,container'
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    reserved: true
    httpsOnly: false
    keyVaultReferenceIdentity: 'SystemAssigned'
    siteConfig: siteConfigCommon
  }
  dependsOn: [
    kvSecretDbUrl
    kvSecretDbUser
    kvSecretDbPassword
  ]
}

// ===== Role assignments — each app's MI gets AcrPull + KV Secrets User =====

resource roleStagingAcrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: acr
  name: guid(acr.id, appStaging.id, roleAcrPull)
  properties: {
    principalId: appStaging.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleAcrPull)
  }
}

resource roleProdAcrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: acr
  name: guid(acr.id, appProd.id, roleAcrPull)
  properties: {
    principalId: appProd.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleAcrPull)
  }
}

resource roleStagingKvUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: kv
  name: guid(kv.id, appStaging.id, roleKvSecretsUser)
  properties: {
    principalId: appStaging.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKvSecretsUser)
  }
}

resource roleProdKvUser 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: kv
  name: guid(kv.id, appProd.id, roleKvSecretsUser)
  properties: {
    principalId: appProd.identity.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKvSecretsUser)
  }
}

// ===== Outputs (match azure-pipelines.yml variable names) ==================

output acrName string           = acr.name
output acrLoginServer string    = acr.properties.loginServer
output appNameStaging string    = appStaging.name
output appNameProd string       = appProd.name
output keyVaultName string      = kv.name
output postgresFqdn string      = postgres.properties.fullyQualifiedDomainName
output resourceGroupName string = resourceGroup().name
