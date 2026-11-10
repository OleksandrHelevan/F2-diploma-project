<#
.SYNOPSIS
  Labs 5-6: staged k6 load runs (warm-up + 10/25/50/100/200 VUs) with a recorded environment, docker stats
  sampling (bottleneck) and PostgreSQL counters (DB load).

.EXAMPLE
  .\scripts\run-load-baseline.ps1 -Experiment baseline
  .\scripts\run-load-baseline.ps1 -Experiment scaling
  .\scripts\run-load-baseline.ps1 -Experiment caching
  .\scripts\run-load-baseline.ps1 -Experiment replication
  .\scripts\run-load-baseline.ps1 -Experiment all
#>
param(
    [ValidateSet('baseline', 'scaling', 'caching', 'replication', 'workflow-indexed', 'all', 'single')]
    [string]$Experiment = 'baseline',
    [ValidateSet('a', 'b', 'c')]
    [string]$Scenario = 'a',
    [string]$Stages = '10,25,50,100,200',
    [int]$StageSeconds = 30,
    [int]$WarmupSeconds = 60,
    [int]$WarmupVus = 20,
    [string]$K6Cpus = '4',
    [string]$OutDir = 'lab5'
)

. "$PSScriptRoot\lab-common.ps1"

$ScenarioFiles = @{ a = 'scenario-a-read.js'; b = 'scenario-b-write.js'; c = 'scenario-c-workflow.js' }
$ResultsDir = Join-Path $script:SrcDir "results\$OutDir"
New-Item -ItemType Directory -Force $ResultsDir | Out-Null

$Defaults = [ordered]@{
    USER_SERVICE_REPLICAS = '2'
    USER_SERVICE_CPUS     = '1'
    USERS_DB_CPUS         = '2'
    DB_POOL_SIZE          = '10'
    CACHE_ENABLED         = 'true'
    DB_REPLICA_ENABLED    = 'false'
    READ_YOUR_WRITES_TTL  = '5s'
    REPLICA_APPLY_DELAY   = '0'
}

function Set-LabEnvironment([hashtable]$Overrides) {
    $secondReplica = $Overrides.ContainsKey('SECOND_REPLICA') -and $Overrides['SECOND_REPLICA'] -eq 'true'
    $Overrides.Remove('SECOND_REPLICA')
    $config = [ordered]@{}
    foreach ($key in $Defaults.Keys) { $config[$key] = $Defaults[$key] }
    foreach ($key in $Overrides.Keys) { $config[$key] = [string]$Overrides[$key] }
    foreach ($key in $config.Keys) { Set-Item -Path "Env:$key" -Value $config[$key] }
    Write-Host ("  environment: " + (($config.Keys | ForEach-Object { "$_=$($config[$_])" }) -join ' '))
    if ($secondReplica) {
        $env:DB_REPLICA_URL = 'jdbc:postgresql://postgres-users-replica:5432/users_db,jdbc:postgresql://postgres-users-replica-2:5432/users_db'
        Invoke-Compose --profile replica2 up -d --wait postgres-users-replica-2 | Out-Null
    } else {
        Remove-Item Env:DB_REPLICA_URL -ErrorAction SilentlyContinue
        Invoke-Compose --profile replica2 stop postgres-users-replica-2 | Out-Null
    }
    Invoke-Compose up -d --wait --scale "user-service=$($config.USER_SERVICE_REPLICAS)" `
        postgres-users postgres-users-replica user-service lb | Out-Null
    $config['REPLICAS'] = if ($config.DB_REPLICA_ENABLED -ne 'true') { '0' } elseif ($secondReplica) { '2' } else { '1' }
    Start-Sleep -Seconds 5
    $config
}

function Get-ContainerLimits {
    $limits = [ordered]@{}
    foreach ($service in 'user-service', 'postgres-users', 'postgres-users-replica', 'postgres-users-replica-2', 'redis', 'keycloak', 'lb', 'notification-service') {
        foreach ($id in @(Invoke-Compose --profile replica2 ps -q $service)) {
            if (-not $id) { continue }
            $info = (& docker inspect --format '{{.Name}}|{{.HostConfig.NanoCpus}}|{{.HostConfig.Memory}}' $id).Trim()
            $name, $nano, $memory = $info -split '\|'
            $limits[$name.TrimStart('/')] = [ordered]@{
                cpus   = if ([long]$nano -gt 0) { [math]::Round([long]$nano / 1e9, 2) } else { 'unlimited' }
                memory = if ([long]$memory -gt 0) { "$([math]::Round([long]$memory / 1MB)) MiB" } else { 'unlimited' }
            }
        }
    }
    $limits
}

function Get-DbCounters {
    $sql = "SELECT xact_commit, tup_returned, tup_fetched FROM pg_stat_database WHERE datname = current_database()"
    $counters = [ordered]@{}
    foreach ($service in 'postgres-users', 'postgres-users-replica', 'postgres-users-replica-2') {
        if (-not @(Invoke-Compose --profile replica2 ps -q $service)) { continue }
        try {
            $row = (@(Invoke-ServiceDb $service $sql) -join '').Trim() -split '\|'
            $counters[$service] = [ordered]@{ xact_commit = [long]$row[0]; tup_returned = [long]$row[1]; tup_fetched = [long]$row[2] }
        } catch {
            $counters[$service] = $null
        }
    }
    $counters
}

function Get-CounterDelta($Before, $After) {
    $delta = [ordered]@{}
    foreach ($node in $After.Keys) {
        if ($null -eq $After[$node] -or $null -eq $Before[$node]) { continue }
        $delta[$node] = [ordered]@{}
        foreach ($field in $After[$node].Keys) { $delta[$node][$field] = $After[$node][$field] - $Before[$node][$field] }
    }
    $delta
}

function Start-StatsSampler([string]$File) {
    Set-Content -Path $File -Value 'timestamp,container,cpu_percent,memory' -Encoding utf8
    Start-Job -ArgumentList $File -ScriptBlock {
        param($Target)
        while ($true) {
            $now = Get-Date -Format o
            $lines = & docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' 2>$null
            foreach ($line in $lines) {
                if ($line -match '^bricklayers-') { Add-Content -Path $Target -Value "$now,$($line -replace '%', '')" -Encoding utf8 }
            }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Get-CpuProfile([string]$File) {
    $rows = Import-Csv $File | Where-Object { $_.cpu_percent -match '^[0-9.]+$' }
    $rows | Group-Object container | ForEach-Object {
        $values = @($_.Group | ForEach-Object { [double]$_.cpu_percent } | Sort-Object)
        $p90 = $values[[math]::Min($values.Count - 1, [math]::Floor($values.Count * 0.9))]
        [ordered]@{ container = $_.Name; maxCpuPercent = [math]::Round($values[-1], 1); p90CpuPercent = [math]::Round($p90, 1) }
    } | Sort-Object { -$_.maxCpuPercent }
}

function Invoke-BaselineRun([string]$Name, [string]$ScenarioKey, [hashtable]$Overrides, [string]$Label) {
    Write-Step "$Name : scenario $ScenarioKey ($Label)"
    $config = Set-LabEnvironment $Overrides
    $limits = Get-ContainerLimits
    $statsFile = Join-Path $ResultsDir "$Name-stats.csv"
    $before = Get-DbCounters
    $sampler = Start-StatsSampler $statsFile
    $started = Get-Date
    try {
        $output = Invoke-Compose run --rm --no-deps `
            -e "RUN_NAME=$Name" -e "RESULTS_DIR=$OutDir" -e "ENV_LABEL=$Label" -e "RUN_SUFFIX=$Name" `
            -e "STAGES=$Stages" -e "STAGE_SECONDS=$StageSeconds" -e "WARMUP_SECONDS=$WarmupSeconds" -e "WARMUP_VUS=$WarmupVus" `
            --entrypoint k6 load run --quiet "/scripts/$($ScenarioFiles[$ScenarioKey])"
    } finally {
        Stop-Job $sampler | Out-Null
        Remove-Job $sampler -Force | Out-Null
    }
    $after = Get-DbCounters
    $output | Where-Object { $_ -match '^\||scenario-|teardown' } | ForEach-Object { Write-Host "  $_" }
    $environment = [ordered]@{
        run             = $Name
        scenario        = $ScenarioKey
        label           = $Label
        startedAt       = $started.ToString('o')
        durationSeconds = [math]::Round(((Get-Date) - $started).TotalSeconds)
        host            = [ordered]@{
            cpu           = (Get-CimInstance Win32_Processor | Select-Object -First 1).Name
            logicalCpus   = (Get-CimInstance Win32_Processor | Measure-Object NumberOfLogicalProcessors -Sum).Sum
            ramGiB        = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
            dockerVmCpus  = (& docker info --format '{{.NCPU}}').Trim()
            dockerVmRamGiB = [math]::Round([long](& docker info --format '{{.MemTotal}}').Trim() / 1GB, 1)
        }
        config          = $config
        k6Cpus          = $env:K6_CPUS
        warmup          = "$WarmupSeconds s at $WarmupVus VUs"
        containerLimits = $limits
        dbCounterDelta  = Get-CounterDelta $before $after
        cpuProfile      = @(Get-CpuProfile $statsFile)
    }
    $environment | ConvertTo-Json -Depth 6 | Set-Content -Path (Join-Path $ResultsDir "$Name-env.json") -Encoding utf8
    Write-Host '  hottest containers (docker stats, 100% = one core):'
    $environment.cpuProfile | Select-Object -First 5 | ForEach-Object {
        Write-Host ("    {0,-40} max {1,6}%  p90 {2,6}%" -f $_.container, $_.maxCpuPercent, $_.p90CpuPercent)
    }
    foreach ($node in $environment.dbCounterDelta.Keys) {
        $d = $environment.dbCounterDelta[$node]
        Write-Host ("  {0,-24} commits {1,8}  rows returned {2,10}" -f $node, $d.xact_commit, $d.tup_returned)
    }
}

$env:K6_CPUS = $K6Cpus
try {
    if ($Experiment -in 'baseline', 'all') {
        foreach ($key in 'a', 'b', 'c') {
            Invoke-BaselineRun "baseline-$key" $key @{} '2 x user-service (1 CPU each), PostgreSQL 2 CPU, Redis cache on, single primary'
        }
    }
    if ($Experiment -in 'scaling', 'all') {
        Invoke-BaselineRun 'scaling-1-instance' 'a' @{ USER_SERVICE_REPLICAS = '1' } '1 x user-service (1 CPU)'
        Invoke-BaselineRun 'scaling-2-instances' 'a' @{ USER_SERVICE_REPLICAS = '2' } '2 x user-service (1 CPU each)'
        Invoke-BaselineRun 'scaling-3-instances' 'a' @{ USER_SERVICE_REPLICAS = '3' } '3 x user-service (1 CPU each)'
    }
    if ($Experiment -in 'caching', 'all') {
        Invoke-BaselineRun 'caching-disabled' 'a' @{ CACHE_ENABLED = 'false' } 'Redis cache disabled'
        Invoke-BaselineRun 'caching-enabled' 'a' @{ CACHE_ENABLED = 'true' } 'Redis cache enabled, warmed up'
    }
    if ($Experiment -in 'replication', 'all') {
        $dbBound = @{ USER_SERVICE_REPLICAS = '2'; USER_SERVICE_CPUS = '4'; USERS_DB_CPUS = '0.3'; CACHE_ENABLED = 'true' }
        Invoke-BaselineRun 'replication-single-primary' 'a' ($dbBound + @{ DB_REPLICA_ENABLED = 'false' }) 'single primary (0.3 CPU)'
        Invoke-BaselineRun 'replication-1-replica' 'a' ($dbBound + @{ DB_REPLICA_ENABLED = 'true' }) 'primary + 1 replica (0.3 CPU each)'
        Invoke-BaselineRun 'replication-2-replicas' 'a' ($dbBound + @{ DB_REPLICA_ENABLED = 'true'; SECOND_REPLICA = 'true' }) 'primary + 2 replicas (0.3 CPU each)'
    }
    if ($Experiment -eq 'workflow-indexed') {
        Invoke-BaselineRun 'baseline-c-indexed' 'c' @{} 'as baseline-c, with idx_admin_audit_log_target_created_at'
    }
    if ($Experiment -eq 'single') {
        Invoke-BaselineRun "single-$Scenario" $Scenario @{} 'defaults'
    }
} finally {
    Write-Step 'Restoring the default environment (replica routing on, no CPU limits on the databases)'
    foreach ($key in $Defaults.Keys) { Remove-Item -Path "Env:$key" -ErrorAction SilentlyContinue }
    Remove-Item Env:DB_REPLICA_URL -ErrorAction SilentlyContinue
    Invoke-Compose --profile replica2 stop postgres-users-replica-2 | Out-Null
    Invoke-Compose up -d --wait postgres-users postgres-users-replica user-service lb | Out-Null
}
