using './function-app.bicep'

param functionAppName = 'api-monitor-chaitra-240905378'
param hostingPlanName = 'ASP-apimonitorrg-3653'
param storageAccountName = 'apimon644667006'
param deploymentContainerName = 'app-package-apimonitorchaitra240905378-1727647'
param workspaceResourceId = '/subscriptions/05cce0b9-eb26-4c45-93da-f9c8f8da0643/resourceGroups/DefaultResourceGroup-CID/providers/Microsoft.OperationalInsights/workspaces/DefaultWorkspace-05cce0b9-eb26-4c45-93da-f9c8f8da0643-CID'