<#
.SYNOPSIS
  Lab 6: primary/replica replication of users_db, stale read vs read-your-writes, and node failures.

.DESCRIPTION
    1. Topology: pg_stat_replication on the primary, recovery state on the replica.
    2. Replication: a user created through the API (primary) appears on the replica.
    3. Replication lag: the replica is restarted with REPLICA_APPLY_DELAY (default 3s).
       - another user reading the colleague directory (eventual, replica) sees the OLD value: stale read;
       - the admin who made the change reads from the primary for READ_YOUR_WRITES_TTL (sticky routing).
    4. docker stop <replica>: reads fall back to the primary, /health = DEGRADED, no errors.
    5. docker stop <primary>: writes answer 503 (read-only mode), eventual reads keep working from the replica.
  The replica delay is reset to 0 and all nodes are started again at the end.

.EXAMPLE
  .\scripts\demo-replication.ps1
#>
param(
    [string]$ApplyDelay = '3s',
    [string]$AdminUser = 'superadmin',
    [string]$AdminPassword = 'superadmin',
    [string]$KeycloakUrl = 'http://localhost:8084'
)

. "$PSScriptRoot\lab-common.ps1"

$primaryContainer = 'bricklayers-users-db'
$replicaContainer = 'bricklayers-users-db-replica'

function Invoke-WithRoute([string]$Method, [string]$Path, [string]$Token, $Body) {
    $params = @{ Method = $Method; Uri = "$($script:BaseUrl)$Path"; Headers = @{ Authorization = "Bearer $Token" }; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5))
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    if ($PSVersionTable.PSVersion.Major -ge 7) { $params['SkipHttpErrorCheck'] = $true }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode; $headers = $response.Headers; $content = $response.Content
    } catch [System.Net.WebException] {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) { throw }
        $status = [int]$errorResponse.StatusCode; $headers = $errorResponse.Headers
        $content = (New-Object IO.StreamReader($errorResponse.GetResponseStream())).ReadToEnd()
    }
    $watch.Stop()
    if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
    $parsed = $null
    if ($content) { try { $parsed = $content | ConvertFrom-Json } catch { $parsed = $content } }
    [pscustomobject]@{
        Method = $Method; Path = $Path; Status = $status; Body = $parsed
        Route = Get-HeaderValue $headers 'X-DB-Route'; Ms = [math]::Round($watch.Elapsed.TotalMilliseconds, 1)
    }
}

function Write-Call($Result, [string]$Note = '') {
    Write-Host ("  {0,-4} {1,-50} -> {2}  X-DB-Route={3,-16} {4,6} ms  {5}" -f $Result.Method, $Result.Path,
        $Result.Status, $Result.Route, $Result.Ms, $Note)
}

function Get-KeycloakAdminToken {
    $body = @{ grant_type = 'password'; client_id = 'admin-cli'; username = 'admin'; password = 'admin' }
    (Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" -Body $body).access_token
}

function New-DemoUser([string]$Token, [string]$Username, [string]$Role, [switch]$WithPassword) {
    $created = Invoke-WithRoute POST '/api/v1/admin/users' $Token @{
        username = $Username; email = "$Username@demo.test"; firstName = 'Replica'; lastName = 'Initial'
        role = $Role; companyId = $script:DemoCompanyId
    }
    if ($created.Status -ne 201) { throw "Cannot create $Username : HTTP $($created.Status) $($created.Body | ConvertTo-Json -Compress)" }
    $user = [pscustomobject]@{ Id = $created.Body.id; Username = $Username; Password = $null }
    if ($WithPassword) {
        $kc = Get-KeycloakAdminToken
        $headers = @{ Authorization = "Bearer $kc" }
        $kcUser = @(Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/bricklayers/users?exact=true&username=$Username" -Headers $headers)[0]
        $password = "Demo-$Username-1"
        $credential = @{ type = 'password'; value = $password; temporary = $false } | ConvertTo-Json
        Invoke-RestMethod -Method Put -Uri "$KeycloakUrl/admin/realms/bricklayers/users/$($kcUser.id)/reset-password" `
            -Headers $headers -Body $credential -ContentType 'application/json' | Out-Null
        $user.Password = $password
    }
    $user
}

function Set-LastName([string]$Token, $User, [string]$LastName) {
    Invoke-WithRoute PUT "/api/v1/admin/users/$($User.Id)" $Token @{
        firstName = 'Replica'; lastName = $LastName; email = "$($User.Username)@demo.test"; role = 'BUILDER'
        companyId = $script:DemoCompanyId
    }
}

function Get-ReplicaLastName([string]$Username) {
    (@(Invoke-ServiceDb 'postgres-users-replica' "SELECT last_name FROM users WHERE username = '$Username'") -join '').Trim()
}

function Restart-Replica([string]$Delay) {
    $env:REPLICA_APPLY_DELAY = $Delay
    Invoke-Compose up -d --wait postgres-users-replica | Out-Null
    $applied = (@(Invoke-ServiceDb 'postgres-users-replica' "SELECT current_setting('recovery_min_apply_delay')") -join '').Trim()
    Write-Host "  replica restarted with recovery_min_apply_delay = $applied"
}

function Wait-Health([string]$Expected, [int]$TimeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try { $health = Invoke-RestMethod -Uri "$($script:BaseUrl)/health" -UseBasicParsing } catch { $health = $null }
        if ($health -and $health.status -eq $Expected) { return $health }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "/health did not become $Expected within $TimeoutSeconds s"
}

$admin = Get-LabToken $AdminUser $AdminPassword
$suffix = Get-Date -Format 'HHmmss'
$created = @()
$stopped = @()

try {
    Write-Step '1. Topology (GET /api/v1/admin/db/replication)'
    $status = (Invoke-WithRoute GET '/api/v1/admin/db/replication' $admin).Body
    $sender = @($status.primary.replicas)[0]
    Write-Host "  topology: $($status.topology)"
    Write-Host "  primary : wal sender to $($sender.client_addr) state=$($sender.state) sync=$($sender.sync_state) sent=$($sender.sent_lsn) replay=$($sender.replay_lsn) lag=$($sender.lag_bytes) B"
    Write-Host "  replica : in_recovery=$($status.'replica-1'.in_recovery) replay_lsn=$($status.'replica-1'.replay_lsn)"
    Assert-Lab ($sender.state -eq 'streaming' -and $status.'replica-1'.in_recovery) 'asynchronous streaming replication is running'

    Write-Step '2. Write on the primary, read the row on the replica'
    $target = New-DemoUser $admin "repl-target-$suffix" 'BUILDER'
    $created += $target
    $reader = New-DemoUser $admin "repl-reader-$suffix" 'FOREMAN' -WithPassword
    $created += $reader
    Start-Sleep -Milliseconds 500
    $onReplica = Get-ReplicaLastName $target.Username
    Write-Host "  replica: SELECT last_name FROM users WHERE username = '$($target.Username)' -> '$onReplica'"
    Assert-Lab ($onReplica -eq 'Initial') 'the INSERT made on the primary is on the replica'

    $readerToken = Get-LabToken $reader.Username $reader.Password
    $colleagues = Invoke-WithRoute GET '/api/v1/users' $readerToken
    Write-Call $colleagues 'reader warms the profile cache'

    Write-Step "3. Replication lag: replica applies WAL $ApplyDelay late"
    Restart-Replica $ApplyDelay
    Start-Sleep -Seconds 2
    $marker = "Lag$suffix"
    $write = Set-LastName $admin $target $marker
    Write-Call $write "admin writes lastName=$marker"
    $stale = Invoke-WithRoute GET '/api/v1/users' $readerToken
    $staleName = (@($stale.Body) | Where-Object id -eq $target.Id).lastName
    Write-Call $stale "another user (eventual read): lastName=$staleName"
    Assert-Lab ($stale.Route -like 'replica-*' -and $staleName -ne $marker) 'STALE READ: the replica still returns the old value'
    $own = Invoke-WithRoute GET '/api/v1/admin/users?page=0&size=100' $admin
    $ownName = (@($own.Body.content) | Where-Object id -eq $target.Id).lastName
    Write-Call $own "the writer (sticky): lastName=$ownName"
    Assert-Lab ($own.Route -eq 'primary-sticky' -and $ownName -eq $marker) 'READ-YOUR-WRITES: the writer is routed to the primary and sees the change'
    $lag = (Invoke-WithRoute GET '/api/v1/admin/db/replication' $admin).Body
    Write-Host "  replication status right after the write: lag_bytes=$(@($lag.primary.replicas)[0].lag_bytes) replica replay_lsn=$($lag.'replica-1'.replay_lsn)"
    Start-Sleep -Seconds 4
    $later = Invoke-WithRoute GET '/api/v1/users' $readerToken
    $laterName = (@($later.Body) | Where-Object id -eq $target.Id).lastName
    Write-Call $later "after the delay: lastName=$laterName"
    Assert-Lab ($laterName -eq $marker) 'eventually consistent: the replica caught up'
    Restart-Replica '0'

    Write-Step "4. docker stop $replicaContainer : reads fall back to the primary"
    & docker stop $replicaContainer | Out-Null
    $stopped += $replicaContainer
    $fallback = Invoke-WithRoute GET '/api/v1/users' $readerToken
    Write-Call $fallback
    Assert-Lab ($fallback.Status -eq 200 -and $fallback.Route -eq 'primary-fallback') 'eventual read served by the primary (Primary Fallback), no error'
    $health = Wait-Health 'DEGRADED'
    Write-Host "  /health: $($health | ConvertTo-Json -Compress)"
    $writeOk = Set-LastName $admin $target "NoReplica$suffix"
    Write-Call $writeOk 'writes are unaffected'
    & docker start $replicaContainer | Out-Null
    $stopped = @($stopped | Where-Object { $_ -ne $replicaContainer })
    Wait-Health 'UP' 60 | Out-Null
    Start-Sleep -Seconds 3
    $back = Invoke-WithRoute GET '/api/v1/users' $readerToken
    Write-Call $back 'replica is back'

    Write-Step "5. docker stop $primaryContainer : read-only mode"
    & docker stop $primaryContainer | Out-Null
    $stopped += $primaryContainer
    $health = Wait-Health 'READ_ONLY'
    Write-Host "  /health: $($health | ConvertTo-Json -Compress)"
    $blocked = Set-LastName $admin $target "NoPrimary$suffix"
    Write-Call $blocked "message: $($blocked.Body.message)"
    Assert-Lab ($blocked.Status -eq 503) 'writes are rejected with 503 (read-only mode), not 500'
    $eventual = Invoke-WithRoute GET '/api/v1/users' $readerToken
    Write-Call $eventual 'non-critical read from the replica'
    Assert-Lab ($eventual.Status -eq 200 -and $eventual.Route -like 'replica-*') 'eventual reads stay available from the replica'
    $strict = Invoke-WithRoute GET "/api/v1/admin/users/$($target.Id)" $admin
    Write-Call $strict 'strict read (@ReadFromPrimary)'
    & docker start $primaryContainer | Out-Null
    $stopped = @($stopped | Where-Object { $_ -ne $primaryContainer })
    Wait-Health 'UP' 90 | Out-Null
    Write-Host '  primary is back, replica reconnected'
} finally {
    foreach ($container in $stopped) { & docker start $container | Out-Null }
    if ($env:REPLICA_APPLY_DELAY -and $env:REPLICA_APPLY_DELAY -ne '0') { Restart-Replica '0' }
    Remove-Item Env:REPLICA_APPLY_DELAY -ErrorAction SilentlyContinue
    try { Wait-Health 'UP' 90 | Out-Null } catch { Write-Host "  warning: $_" }
    foreach ($user in $created) {
        $deleted = Invoke-WithRoute DELETE "/api/v1/admin/users/$($user.Id)" $admin
        Write-Host "  cleanup: DELETE $($user.Username) -> $($deleted.Status)"
    }
}
