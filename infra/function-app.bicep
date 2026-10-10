param location string = 'centralindia'
param functionAppName string
param hostingPlanName string
param storageAccountName string
param deploymentContainerName string
param workspaceResourceId string

param healthyApiUrl string = 'https://httpbingo.org/status/200'
param errorApiUrl string = 'https://httpbingo.org/status/500'
param slowApiUrl string = 'https://httpbingo.org/delay/3'
param timeoutApiUrl string = 'https://httpbingo.org/delay/10'

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' existing = {
  name: storageAccountName
}

resource hostingPlan 'Microsoft.Web/serverfarms@2024-04-01' = {
  name: hostingPlanName
  location: location
  sku: {
    name: 'FC1'
    tier: 'FlexConsumption'
    capacity: 0
  }
  properties: {
    reserved: true
  }
}

resource insights 'Microsoft.Insights/components@2020-02-02' = {
  name: functionAppName
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: workspaceResourceId
  }
}

var storageConnectionString = 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'

resource functionApp 'Microsoft.Web/sites@2024-04-01' = {
  name: functionAppName
  location: location
  kind: 'functionapp,linux'
  tags: {
    'hidden-link: /app-insights-resource-id': insights.id
  }
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: hostingPlan.id
    httpsOnly: false
    publicNetworkAccess: 'Enabled'
    functionAppConfig: {
      runtime: {
        name: 'java'
        version: '21'
      }
      deployment: {
        storage: {
          type: 'blobContainer'
          value: 'https://${storageAccountName}.blob.${environment().suffixes.storage}/${deploymentContainerName}'
          authentication: {
            type: 'StorageAccountConnectionString'
            storageAccountConnectionStringName: 'DEPLOYMENT_STORAGE_CONNECTION_STRING'
          }
        }
      }
      scaleAndConcurrency: {
        maximumInstanceCount: 100
        instanceMemoryMB: 2048
        alwaysReady: []
      }
    }
    siteConfig: {
      appSettings: [
        {
          name: 'AzureWebJobsStorage'
          value: storageConnectionString
        }
        {
          name: 'DEPLOYMENT_STORAGE_CONNECTION_STRING'
          value: storageConnectionString
        }
        {
          name: 'APPLICATIONINSIGHTS_CONNECTION_STRING'
          value: insights.properties.ConnectionString
        }
        {
          name: 'MONITOR_STORAGE_ACCOUNT'
          value: storage.name
        }
        {
          name: 'HEALTHY_API_URL'
          value: healthyApiUrl
        }
        {
          name: 'ERROR_API_URL'
          value: errorApiUrl
        }
        {
          name: 'SLOW_API_URL'
          value: slowApiUrl
        }
        {
          name: 'TIMEOUT_API_URL'
          value: timeoutApiUrl
        }
      ]
    }
  }
}

output functionAppId string = functionApp.id
output functionAppPrincipalId string = functionApp.identity.principalId