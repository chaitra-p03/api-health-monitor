# API Health Monitor

A Java application that checks API availability and response speed,
persists monitoring history in Azure, and displays results in a public dashboard.

## Live dashboard

https://api-health-monitor.azurewebsites.net/api/dashboard

Viewing the dashboard requires no key.
Demo controls require a valid Azure Functions key.
Never publish keys or storage connection strings.

## Features

- Checks HTTP status, response latency, and request timeouts.
- Classifies results as HEALTHY, SLOW, or FAILED.
- Announces outages after three consecutive failures.
- Announces slowness after three consecutive slow results.
- Detects failure rates of at least 30% over a full window of ten checks.
- Records recovery and suppresses repeated alerts during an active incident.
- Saves check history and monitoring state across executions.
- Checks four configured endpoints every five minutes.
- Provides a separate, interactive demo API.
- Builds, tests, and deploys application code through GitHub Actions.
- Defines Azure infrastructure using Bicep.

## Dashboard

The dashboard displays:

- Summary counts for the four scheduled APIs.
- Individual cards for scheduled APIs and the separate demo API.
- Latest status, latency, check time, counters, and active alert flags.
- A latency chart using up to 100 saved checks from the past hour.
- A table showing the latest 12 checks from that history.
- A timeline showing the latest 30 saved demo events.

Saved history and demo events persist after page reloads.

The page refreshes saved data every 30 seconds.
Refreshing does not perform new API checks.

### Protected demo controls

- Simulate failure: selects an HTTP 500 endpoint and performs one check.
- Run demo check: checks the currently selected demo endpoint.
- Restore API: selects an HTTP 200 endpoint and performs one check.

These controls affect only demo-api.
They do not change the four scheduled endpoints or Azure infrastructure.
Concurrent demo operations are guarded by a storage-backed lock.

The entered key is held in page memory and is not embedded in the HTML.
Azure enforces authorization on the demo endpoint.

## Monitoring rules

| Rule | Alert condition | Recovery condition |
|---|---|---|
| Outage | Three consecutive FAILED results | A result that is not FAILED |
| Slowness | Three consecutive SLOW results | A HEALTHY result |
| Failure rate | At least 30% failures in the latest ten checks | Below 30% failures |

SLOW results count as successful availability outcomes.
An unexpected HTTP status is classified as FAILED before latency is considered.

## Architecture

The timer function invokes the Java monitor every five minutes.

For each scheduled endpoint, the monitor:

1. Loads its saved monitoring state.
2. Performs an HTTP check.
3. Saves the result.
4. Evaluates alert rules and updates state.
5. Saves the updated state and logs notifications.

Azure Table Storage contains:

- HealthChecks: a separate record for each check.
- MonitorState: current counters, alert flags, latest results, and demo data.

Application Insights collects execution logs.
Azure Monitor evaluates alert and recovery messages and invokes an email action group.

The frontend calls Java HTTP functions to retrieve saved data.
It does not access Azure Tables directly.

## Main components

| Component | Responsibility |
|---|---|
| EndpointConfig | Expected status, timeout, URL, and latency threshold |
| ApiChecker | Performs HTTP requests and classifies results |
| CheckResult | Carries one check's observation |
| MonitorState | Remembers counters and alert flags across checks |
| MonitorRules | Updates state and produces notifications |
| AzureCheckStore | Reads and writes Azure Table data |
| App | Coordinates monitoring |
| MonitorTimerFunction | Starts scheduled monitoring |
| DashboardPageFunction | Serves the HTML dashboard |
| DashboardDataFunction | Returns saved dashboard data |
| DashboardDemoFunction | Runs protected demo operations |

## Technology

Java 21, Maven, JUnit 5, Azure Functions Flex Consumption,
Azure Table Storage, Application Insights, Azure Monitor,
GitHub Actions, and Bicep.

## Build and tests

Use JDK 21 or later, Maven, and Azure Functions Core Tools v4
for the Functions build and local host.

```powershell
mvn test
mvn clean package
```

Eleven tests cover:

- HTTP status handling, slow responses, timeouts, and invalid URLs.
- Outage, slowness, and rolling failure-rate rules.
- Public GET access configuration for dashboard data.
- Key-protected POST configuration for demo controls.

HTTP tests use a temporary local server with an automatically assigned port.
Tests do not require Azure access.

## Run the local monitor

Azure persistence requires Azure CLI sign-in and
Storage Table Data Contributor permission on the storage account.
The HealthChecks and MonitorState tables must exist.

From the project folder:

```powershell
az login
mvn compile dependency:copy-dependencies
```

Start the local test API in one terminal:

```powershell
java -cp target/classes dev.apimonitor.TestApi
```

Run one monitoring round in another PowerShell terminal:

```powershell
$env:MONITOR_STORAGE_ACCOUNT = "YOUR_STORAGE_ACCOUNT_NAME"
java -cp "target/classes;target/dependency/*" dev.apimonitor.App
```

The local test API uses port 18080. Start only one copy.
Unset cloud URL overrides when testing the localhost endpoints.

## Configuration

| Setting | Purpose |
|---|---|
| MONITOR_STORAGE_ACCOUNT | Storage account for history and state |
| HEALTHY_API_URL | Healthy test endpoint |
| ERROR_API_URL | HTTP failure test endpoint |
| SLOW_API_URL | Slow-response test endpoint |
| TIMEOUT_API_URL | Timeout test endpoint |

The scheduled cloud demo uses:

| API | URL |
|---|---|
| healthy-api | https://httpbingo.org/status/200 |
| error-api | https://httpbingo.org/status/500 |
| slow-api | https://httpbingo.org/delay/3 |
| timeout-api | https://httpbingo.org/delay/10 |

Each expects HTTP 200, uses a five-second timeout,
and has a two-second latency threshold.

These are test endpoints from one external service, not four independent websites.
Real availability and latency can vary.

## Authentication

The deployed Function App uses a managed identity with
Storage Table Data Contributor permission.

DefaultAzureCredential obtains credentials for that identity in Azure.
Locally, it can use the developer's Azure CLI sign-in.

Dashboard viewing is public.
Demo operations require an Azure Functions key.
The key authorizes demo requests; it is not a storage credential.

## Infrastructure

- infra/storage.bicep: storage account, tables, and deployment container.
- infra/function-app.bicep: hosting plan, Function App, and Application Insights.
- infra/monitoring.bicep: action group, log alert rule, and table permission.
- infra/main.bicep: coordinates the modules.
- infra/example.parameters.json: placeholder parameter template.
- infra/current.parameters.json: local deployment values, excluded from Git.

The resource group and referenced Log Analytics workspace must already exist.

Copy the example and replace every placeholder:

```powershell
Copy-Item infra/example.parameters.json infra/current.parameters.json
```

For a new environment, omit the monitorPrincipalId and
tableRoleAssignmentName overrides so the template derives new values.

Compile the infrastructure:

```powershell
az bicep build --file infra/main.bicep --outfile target/infra-template.json
```

Preview before deployment:

```powershell
$monitorGroup = "YOUR_RESOURCE_GROUP"
$monitorAlertEmail = Read-Host "Alert email"

az deployment group what-if --resource-group "$monitorGroup" --template-file infra/main.bicep --parameters "@infra/current.parameters.json" notificationEmail="$monitorAlertEmail" --result-format ResourceIdOnly
```

Deploy only after reviewing the preview:

```powershell
az deployment group create --resource-group "$monitorGroup" --template-file infra/main.bicep --parameters "@infra/current.parameters.json" notificationEmail="$monitorAlertEmail" --mode Incremental
```

Infrastructure deployment remains manual.
Application code is deployed separately.
Review app settings before applying templates to an existing environment.

## GitHub Actions

The build workflow compiles, tests, packages, and validates Bicep.

The deployment workflow builds and tests changes pushed to main,
then deploys them using OpenID Connect.

Required repository variables:

- AZURE_CLIENT_ID
- AZURE_TENANT_ID
- AZURE_SUBSCRIPTION_ID

Configure the deployment identity and GitHub federated credential separately.
The credential subject must match the repository's actual OIDC subject.
Grant the deployment identity Website Contributor on the target Function App.

The app name and package path in pom.xml and deploy.yml must match.
This identity deploys code; the Function App's managed identity accesses tables.

## Validation

11 automated tests cover HTTP status handling, latency, timeouts,
alert thresholds, recovery, repeated-alert suppression, and HTTP trigger
access configuration.

The Azure deployment was verified through successful scheduled executions,
public dashboard access, rejection of demo requests without a key,
and email delivery through the replacement alert rule.

## Design considerations

- Configurable httpbingo.org endpoints provide controlled responses for
  demonstrating healthy, failed, slow, and timed-out requests.
- Monitoring runs every five minutes. The dashboard refreshes saved data
  every 30 seconds without triggering new checks.
- Check history and state are written separately. Atomic updates across
  those writes are a future improvement.
