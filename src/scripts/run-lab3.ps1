<#
.SYNOPSIS
  Lab 3 experiments through HAProxy: request distribution, Round Robin vs Least Connections with one slow
  replica, scale-out 1/2/3 and node failure under load.

.DESCRIPTION
  Every k6 run writes results/<run>.json (RPS, avg/p95/p99, error rate, requests per X-Instance-ID).
  The cache is disabled for the experiments so that every request reaches PostgreSQL; defaults are restored
  at the end (2 replicas, roundrobin, cache on, default profile).

.EXAMPLE
  .\scripts\run-lab3.ps1 -Scenario distribution
  .\scripts\run-lab3.ps1 -Scenario algorithms
  .\scripts\run-lab3.ps1 -Scenario scale
  .\scripts\run-lab3.ps1 -Scenario failure
  .\scripts\run-lab3.ps1 -Scenario all
#>
param(
    [ValidateSet('distribution', 'algorithms', 'scale', 'failure', 'all')]
    [string]$Scenario = 'all',
    [int]$Vus = 20,
    [string]$Duration = '30s',
    [string]$Warmup = '10s',
    # Scale-out: a CPU limit per replica and enough virtual users to saturate one replica.
    [string]$ReplicaCpus = '1',
    [int]$ScaleVus = 100
)

. "$PSScriptRoot\lab-common.ps1"

$resultsDir = Join-Path $script:SrcDir 'results'
New-Item -ItemType Directory -Force $resultsDir | Out-Null

function Set-LoadBalancer([string]$Algorithm) {
    $env:LB_ALGORITHM = $Algorithm
    Invoke-Compose up -d --no-deps --force-recreate --wait lb | Out-Null
    Write-Host "  HAProxy balance = $Algorithm"
}

function Set-UserReplicas([int]$Count, [switch]$Recreate) {
    $upArgs = @('up', '-d', '--no-deps', '--wait', '--scale', "user-service=$Count")
    if ($Recreate) { $upArgs += '--force-recreate' }
    Invoke-Compose @upArgs user-service | Out-Null
    # Give HAProxy DNS discovery and health checks time to admit new replicas.
    Start-Sleep -Seconds 5
    $ids = @((Get-ReplicaMap 'user-service').Keys)
    $env:INSTANCE_IDS = $ids -join ','
    Write-Host "  user-service replicas: $($ids -join ', ')"
    return $ids
}

function Invoke-Load([string]$Name, [string]$RunDuration = $Duration) {
    $env:RUN_NAME = $Name
    $env:DURATION = $RunDuration
    $env:VUS = "$Vus"
    Invoke-Compose run --rm --no-deps load | Out-Null
    $report = (Get-Content (Join-Path $resultsDir "$Name.json") -Raw | ConvertFrom-Json).report
    return $report
}

function Format-Report($Report, [int]$Replicas) {
    $distribution = ($Report.distribution.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ' '
    [pscustomobject]@{
        Run          = $Report.run
        Replicas     = $Replicas
        RPS          = [math]::Round($Report.rps, 1)
        'avg ms'     = [math]::Round($Report.avgMs, 1)
        'p95 ms'     = [math]::Round($Report.p95Ms, 1)
        'p99 ms'     = [math]::Round($Report.p99Ms, 1)
        'Error rate' = $Report.errorRate
        Distribution = $distribution
    }
}

function Use-ExperimentMode {
    # No cache: measure the backend + PostgreSQL path. lab3 profile: enables X-Lab-Slow-Instance.
    $env:CACHE_ENABLED = 'false'
    $env:SPRING_PROFILES_ACTIVE = 'lab3'
    Remove-Item Env:SLOW_INSTANCE -ErrorAction SilentlyContinue
    Remove-Item Env:REQUIRE_NO_ERRORS -ErrorAction SilentlyContinue
}

function Restore-Defaults {
    Write-Step 'Restoring defaults: 2 replicas, roundrobin, cache on, default profile'
    foreach ($name in 'CACHE_ENABLED', 'SPRING_PROFILES_ACTIVE', 'SLOW_INSTANCE', 'REQUIRE_NO_ERRORS',
                      'INSTANCE_IDS', 'RUN_NAME', 'DURATION', 'VUS', 'LB_ALGORITHM', 'USER_SERVICE_CPUS') {
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
    Invoke-Compose up -d --no-deps --wait --force-recreate --scale user-service=2 user-service | Out-Null
    Invoke-Compose up -d --no-deps --wait --force-recreate lb | Out-Null
}

function Test-Distribution {
    Write-Step 'Distribution of 30 sequential requests (Round Robin)'
    $token = Get-LabToken 'worker' 'worker'
    1..30 | ForEach-Object { (Invoke-Lab -Path '/api/v1/me' -Token $token).Instance } |
        Group-Object | Select-Object @{ n = 'X-Instance-ID'; e = { $_.Name } }, Count | Format-Table -AutoSize | Out-String | Write-Host
}

function Test-Algorithms {
    Write-Step 'Round Robin vs Least Connections, one replica slowed down by 500 ms'
    Use-ExperimentMode
    $ids = Set-UserReplicas 2 -Recreate
    $env:SLOW_INSTANCE = $ids[0]
    Write-Host "  slow replica: $($ids[0])"
    $rows = foreach ($algorithm in 'roundrobin', 'leastconn') {
        Set-LoadBalancer $algorithm
        Invoke-Load "$algorithm-warmup" $Warmup | Out-Null
        Format-Report (Invoke-Load "$algorithm-slow") 2
    }
    Remove-Item Env:SLOW_INSTANCE
    $rows | Format-Table -AutoSize | Out-String | Write-Host
}

function Test-Scale {
    Write-Step "Scale-out: 1, 2 and 3 replicas ($ReplicaCpus CPU each, $ScaleVus VU, Round Robin, no artificial delay)"
    Use-ExperimentMode
    $env:USER_SERVICE_CPUS = $ReplicaCpus
    Set-LoadBalancer 'roundrobin'
    $defaultVus = $Vus
    $script:Vus = $ScaleVus
    $rows = foreach ($count in 1, 2, 3) {
        Set-UserReplicas $count -Recreate | Out-Null
        Invoke-Load "scale-$count-warmup" $Warmup | Out-Null
        Format-Report (Invoke-Load "scale-$count") $count
    }
    $script:Vus = $defaultVus
    Remove-Item Env:USER_SERVICE_CPUS
    $baseline = $rows[0].RPS
    $rows | ForEach-Object {
        $_ | Add-Member -NotePropertyName 'Speedup' -NotePropertyValue ([math]::Round($_.RPS / $baseline, 2)) -PassThru
    } | Format-Table -AutoSize | Out-String | Write-Host
}

function Test-Failure {
    Write-Step 'Node failure under load: docker stop of one replica while k6 runs (60 s, 0 errors required)'
    Use-ExperimentMode
    Set-LoadBalancer 'roundrobin'
    $replicas = $null
    Set-UserReplicas 2 -Recreate | Out-Null
    $replicas = Get-ReplicaMap 'user-service'
    $victimName = @($replicas.Keys)[0]
    $victim = $replicas[$victimName]

    $env:REQUIRE_NO_ERRORS = 'true'
    $env:RUN_NAME = 'node-failure'
    $env:DURATION = '60s'
    $env:VUS = "$Vus"
    $composeArgs = @('compose', '--project-directory', $script:SrcDir)
    if ($script:ComposeProject) { $composeArgs += @('-p', $script:ComposeProject) }
    # --quiet: no progress bar while the summary JSON is still written to results/.
    $composeArgs += @('run', '--rm', '--no-deps', 'load', '--quiet')
    $load = Start-Process -FilePath 'docker' -ArgumentList $composeArgs -NoNewWindow -PassThru
    try {
        Start-Sleep -Seconds 15
        Write-Host "  docker stop $victimName (graceful SIGTERM)"
        & docker stop $victim | Out-Null
        Start-Sleep -Seconds 20
        Write-Host "  docker start $victimName"
        & docker start $victim | Out-Null
        $load.WaitForExit()
    } finally {
        & docker start $victim | Out-Null
        Remove-Item Env:REQUIRE_NO_ERRORS -ErrorAction SilentlyContinue
    }
    $report = (Get-Content (Join-Path $resultsDir 'node-failure.json') -Raw | ConvertFrom-Json).report
    Format-Report $report 2 | Format-Table -AutoSize | Out-String | Write-Host
    Write-Host "  k6 exit code: $($load.ExitCode) (non-zero means the zero-error threshold was violated)"
    Invoke-Compose logs --no-log-prefix --since 2m lb | Select-String -Pattern '\[WARNING\].*(is DOWN|is UP,)' | ForEach-Object { "  $_" }
}

try {
    switch ($Scenario) {
        'distribution' { Test-Distribution }
        'algorithms' { Test-Algorithms }
        'scale' { Test-Scale }
        'failure' { Test-Failure }
        'all' { Test-Distribution; Test-Algorithms; Test-Scale; Test-Failure }
    }
} finally {
    if ($Scenario -ne 'distribution') { Restore-Defaults }
}
