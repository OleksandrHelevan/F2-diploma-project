<#
.SYNOPSIS
  Lab 4: Cache-Aside of user profiles in Redis: MISS -> HIT -> invalidation -> MISS, and Redis failure.

.DESCRIPTION
  Uses GET /api/v1/admin/users/{id} for the demo user 'worker'. A PUT evicts the profile after commit, so the
  next read is a MISS with fresh data. With -SimulateFailure Redis is stopped: reads become BYPASS (served by
  PostgreSQL), writes keep working, and Redis is started again at the end.
  Run it without parallel load: the script expects an exact MISS/HIT sequence.

.EXAMPLE
  .\scripts\demo-cache.ps1
  .\scripts\demo-cache.ps1 -SimulateFailure
#>
param(
    [switch]$SimulateFailure,
    [int]$HotReads = 5,
    [string]$AdminUser = 'superadmin',
    [string]$AdminPassword = 'superadmin',
    [string]$TargetUser = 'worker'
)

. "$PSScriptRoot\lab-common.ps1"

$token = Get-LabToken $AdminUser $AdminPassword
$page = Invoke-Lab -Path '/api/v1/admin/users?page=0&size=100' -Token $token
$target = $page.Body.content | Where-Object username -eq $TargetUser | Select-Object -First 1
if (-not $target) { throw "User '$TargetUser' not found; wait for the Keycloak sync (about 1 minute after start)" }
$path = "/api/v1/admin/users/$($target.id)"
$originalLastName = $target.lastName
$redisStopped = $false

function Set-LastName([string]$LastName) {
    $result = Invoke-Lab -Method PUT -Path $path -Token $token -Body @{
        firstName = $target.firstName
        lastName  = $LastName
        email     = $target.email
        role      = $target.role
        companyId = $target.companyId
    }
    Write-Response $result
    Assert-Lab ($result.Status -eq 200) "PUT lastName=$LastName committed"
}

$timings = New-Object System.Collections.Generic.List[object]
function Read-Profile([string]$Expected, [string]$ExpectedLastName) {
    $result = Invoke-Lab -Path $path -Token $token
    Write-Response $result
    Assert-Lab ($result.Status -eq 200 -and $result.Cache -eq $Expected) "X-Cache=$Expected"
    if ($ExpectedLastName) {
        Assert-Lab ($result.Body.lastName -eq $ExpectedLastName) "fresh data: lastName=$ExpectedLastName"
    }
    $timings.Add([pscustomobject]@{ Cache = $result.Cache; Ms = $result.Ms; Instance = $result.Instance })
    $result
}

try {
    $marker = "Cache$(Get-Date -Format 'HHmmss')"

    Write-Step '1. PUT invalidates the cached profile (after commit)'
    Set-LastName $marker

    Write-Step '2. First read: MISS, loaded from PostgreSQL and stored in Redis with TTL'
    Read-Profile 'MISS' $marker | Out-Null

    Write-Step "3. $HotReads repeated reads: HIT from Redis, on any replica"
    for ($i = 0; $i -lt $HotReads; $i++) { Read-Profile 'HIT' $marker | Out-Null }

    Write-Step '4. PUT restores the name: next read is a MISS with the new value'
    Set-LastName $originalLastName
    Read-Profile 'MISS' $originalLastName | Out-Null
    Read-Profile 'HIT' $originalLastName | Out-Null

    if ($SimulateFailure) {
        Write-Step '5. docker compose stop redis: reads fall back to PostgreSQL (BYPASS)'
        Invoke-Compose stop redis | Out-Null
        $redisStopped = $true
        Read-Profile 'BYPASS' $originalLastName | Out-Null
        Set-LastName "$marker-noredis"
        Read-Profile 'BYPASS' "$marker-noredis" | Out-Null
        Set-LastName $originalLastName

        Write-Step '6. Redis back: empty cache (no persistence), warmed up again'
        Invoke-Compose start redis | Out-Null
        $redisStopped = $false
        # The circuit breaker skips Redis for 5 s after the last failure; wait it out before expecting MISS.
        Start-Sleep -Seconds 6
        Read-Profile 'MISS' $originalLastName | Out-Null
        Read-Profile 'HIT' $originalLastName | Out-Null
    }

    Write-Step 'Response time by source'
    $timings | Group-Object Cache | ForEach-Object {
        $stats = $_.Group | Measure-Object Ms -Average -Minimum -Maximum
        [pscustomobject]@{
            'X-Cache' = $_.Name
            Requests  = $stats.Count
            'Avg ms'  = [math]::Round($stats.Average, 1)
            'Min ms'  = $stats.Minimum
            'Max ms'  = $stats.Maximum
        }
    } | Format-Table -AutoSize | Out-String | Write-Host
} finally {
    if ($redisStopped) { Invoke-Compose start redis | Out-Null }
}
