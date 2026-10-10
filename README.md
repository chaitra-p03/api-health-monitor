# API Health Monitor

A Java application that periodically checks API health, detects failures
and slow responses, and stores results and monitoring state in Azure.

## Features

- Checks HTTP status, response latency, and request timeouts.
- Classifies results as HEALTHY, SLOW, or FAILED.
- Alerts after three consecutive failures or slow responses.
- Detects failure rates of at least 30% over the latest ten checks.
- Records recovery and suppresses repeated alerts during an active incident.
- Persists check history and monitoring state across executions.
- Runs every five minutes using an Azure Functions timer.
- Builds, tests, and deploys through GitHub Actions.

## Architecture

Azure Functions runs the Java checker on a schedule.

Azure Table Storage contains:
- HealthChecks: individual check results.
- MonitorState: counters, recent outcomes, and active alert flags.

Application Insights collects execution logs.
Azure Monitor evaluates alert and recovery messages.

## Technology

Java 21, Maven, JUnit 5, Azure Functions Flex Consumption,
Azure Table Storage, Application Insights, Azure Monitor,
GitHub Actions, and Bicep.

## Run tests

Requires JDK 21 or later and Maven.

```powershell
mvn test
```

Nine tests cover failure and recovery rules, rolling-window thresholds,
HTTP status handling, slow responses, timeouts, and invalid URLs.

HTTP tests use a temporary local server with an automatically assigned port.
They do not require Azure access.

## Run the local demo

For Azure persistence, sign in with Azure CLI. The signed-in account needs
Storage Table Data Contributor permission on the configured storage account.

From the project folder:

```powershell
az login
mvn compile dependency:copy-dependencies
```

Start the demo API in one terminal:

```powershell
java -cp target/classes dev.apimonitor.TestApi
```

Run the monitor in another PowerShell terminal:

```powershell
$env:MONITOR_STORAGE_ACCOUNT = "YOUR_STORAGE_ACCOUNT_NAME"
java -cp "target/classes;target/dependency/*" dev.apimonitor.App
```

The local demo API uses port 18080. Start only one copy.

## Configuration

| Setting | Purpose |
|---|---|
| MONITOR_STORAGE_ACCOUNT | Storage account for history and state |
| HEALTHY_API_URL | Healthy endpoint |
| ERROR_API_URL | Endpoint used to demonstrate HTTP failures |
| SLOW_API_URL | Endpoint used to demonstrate slow responses |
| TIMEOUT_API_URL | Endpoint used to demonstrate request timeouts |

The cloud demo uses public httpbingo.org endpoints.
Their availability and response times depend on an external service.

## Infrastructure

- infra/storage.bicep: storage account, tables, and deployment container.
- infra/function-app.bicep: Flex hosting plan, Function App, and Application Insights.
- infra/monitoring.bicep: action group, log alert rule, and table permission.
- infra/main.bicep: coordinates the three modules.
- infra/current.parameters.json: parameters for the existing deployment.

The resource group and referenced Log Analytics workspace must already exist.
The current parameter file contains environment-specific resource names and IDs.

To preview the existing environment:

```powershell
$monitorAlertEmail = Read-Host "Existing alert email"
az deployment group what-if --resource-group api-monitor-rg --template-file infra/main.bicep --parameters "@infra/current.parameters.json" notificationEmail="$monitorAlertEmail" --result-format ResourceIdOnly
```

To deploy after reviewing the preview:

```powershell
az deployment group create --resource-group api-monitor-rg --template-file infra/main.bicep --parameters "@infra/current.parameters.json" notificationEmail="$monitorAlertEmail" --mode Incremental
```

For a new environment, supply new resource names and a valid workspace ID.
Omit the existing monitorPrincipalId and tableRoleAssignmentName overrides
so the template uses the new Function App identity.

Infrastructure deployment provisions resources; code is deployed separately.

## GitHub Actions

Java build compiles, tests, packages, and validates the Bicep modules.

Deploy to Azure tests and packages the application, then deploys pushes
to main using OpenID Connect.

Required repository variables:
- AZURE_CLIENT_ID
- AZURE_TENANT_ID
- AZURE_SUBSCRIPTION_ID

The deployment identity and its GitHub federated credential are configured
separately. It has Website Contributor permission on the Function App.

## Known limitations

Azure Monitor alerts and action-group execution have been verified.
The email receiver is verified, but alert-email delivery has not been confirmed.

An Azure Monitor alert resolving means its log-query condition cleared.
Actual API recovery is identified by the application's RECOVERY messages.

This is a demonstration monitor, not a production availability guarantee.