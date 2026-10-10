param location string = 'centralindia'
param functionAppName string
param hostingPlanName string
param storageAccountName string
param deploymentContainerName string
param workspaceResourceId string
param notificationEmail string

// Supply existing IDs when updating the current environment.
param tableRoleAssignmentName string = ''
param monitorPrincipalId string = ''

param healthyApiUrl string = 'https://httpbingo.org/status/200'
param errorApiUrl string = 'https://httpbingo.org/status/500'
param slowApiUrl string = 'https://httpbingo.org/delay/3'
param timeoutApiUrl string = 'https://httpbingo.org/delay/10'

module storageInfrastructure './storage.bicep' = {
  name: 'monitor-storage'
  params: {
    location: location
    storageAccountName: storageAccountName
    deploymentContainerName: deploymentContainerName
  }
}

module appInfrastructure './function-app.bicep' = {
  name: 'monitor-function-app'
  params: {
    location: location
    functionAppName: functionAppName
    hostingPlanName: hostingPlanName
    storageAccountName: storageInfrastructure.outputs.storageAccountName
    deploymentContainerName: deploymentContainerName
    workspaceResourceId: workspaceResourceId
    healthyApiUrl: healthyApiUrl
    errorApiUrl: errorApiUrl
    slowApiUrl: slowApiUrl
    timeoutApiUrl: timeoutApiUrl
  }
}

var resolvedRoleAssignmentName = empty(tableRoleAssignmentName)
  ? guid(resourceGroup().id, storageAccountName, functionAppName, 'table-contributor')
  : tableRoleAssignmentName

module monitoringInfrastructure './monitoring.bicep' = {
  name: 'monitor-alerts-and-permissions'
  params: {
    location: location
    functionAppName: functionAppName
    storageAccountName: storageInfrastructure.outputs.storageAccountName
    notificationEmail: notificationEmail
    tableRoleAssignmentName: resolvedRoleAssignmentName
    monitorPrincipalId: empty(monitorPrincipalId)
      ? appInfrastructure.outputs.functionAppPrincipalId
      : monitorPrincipalId
  }
  dependsOn: [
    appInfrastructure
  ]
}

output functionAppId string = appInfrastructure.outputs.functionAppId
output alertRuleId string = monitoringInfrastructure.outputs.alertRuleId