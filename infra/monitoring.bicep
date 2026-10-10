param location string = 'centralindia'
param functionAppName string
param storageAccountName string
param notificationEmail string
param monitorPrincipalId string = ''
param actionGroupName string = 'api-monitor-email'
param alertRuleName string = 'api-monitor-notifications'

resource functionApp 'Microsoft.Web/sites@2024-04-01' existing = {
  name: functionAppName
}

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' existing = {
  name: storageAccountName
}

resource insights 'Microsoft.Insights/components@2020-02-02' existing = {
  name: functionAppName
}

var tableContributorRoleId = subscriptionResourceId(
  'Microsoft.Authorization/roleDefinitions',
  '0a9a7e1f-b9d0-4cc4-a60d-0319b160aaa3'
)

param tableRoleAssignmentName string

resource tablePermission 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: tableRoleAssignmentName
  scope: storage
  properties: {
    roleDefinitionId: tableContributorRoleId
    principalId: empty(monitorPrincipalId) ? functionApp.identity.principalId : monitorPrincipalId
    principalType: 'ServicePrincipal'
  }
}

resource emailGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: actionGroupName
  location: 'global'
  properties: {
    enabled: true
    groupShortName: 'APIEmail'
    emailReceivers: [
      {
        name: 'EmailAlert'
        emailAddress: notificationEmail
        useCommonAlertSchema: false
      }
    ]
  }
}

resource alertRule 'Microsoft.Insights/scheduledQueryRules@2023-12-01' = {
  name: alertRuleName
  location: location
  kind: 'LogAlert'
  properties: {
    enabled: true
    severity: 3
    targetResourceTypes: [
      'microsoft.insights/components'
    ]
    scopes: [
      insights.id
    ]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT5M'
    autoMitigate: true
    criteria: {
      allOf: [
        {
          query: 'traces\n| where message contains "ALERT:" or message contains "RECOVERY:"\n'
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 0
          dimensions: []
          resourceIdColumn: ''
          failingPeriods: {
            minFailingPeriodsToAlert: 1
            numberOfEvaluationPeriods: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        emailGroup.id
      ]
      actionProperties: {}
      customProperties: {}
    }
  }
}

output alertRuleId string = alertRule.id