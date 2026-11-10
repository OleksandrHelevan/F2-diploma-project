<#
.SYNOPSIS
  Lab 5: asynchronous e-mails across two services (fire-and-forget) and the account overview (two parallel
  @Async calls). With -SimulateMailOutage it also shows the Lab 6 Quartz job "notification-retry".

.DESCRIPTION
  Creates a temporary user through the admin API and deletes it at the end.
    1. POST /api/v1/admin/users returns at once. After commit, user-service hands a request to
       notification-service on a user-async-* thread; notification-service answers 202 and sends the
       e-mail (provider latency 1 s) on a notif-async-* thread. The e-mail shows up in Mailpit (http://localhost:8025).
    2. PUT changes the role: an "account updated" e-mail follows the same way.
    3. GET /api/v1/admin/users/{id}/overview: Keycloak data and audit history loaded in parallel on the pool.
    4. -SimulateMailOutage: Mailpit is stopped, the next e-mail fails in notification-service (logged by its
       AsyncUncaughtExceptionHandler, row stays PENDING with attempts=1); Mailpit is started again and the
       Quartz job delivers the row within about 1.5 minutes (60 s trigger interval + 30 s minimum age).

.EXAMPLE
  .\scripts\demo-async.ps1
  .\scripts\demo-async.ps1 -SimulateMailOutage
#>
param(
    [switch]$SimulateMailOutage,
    [string]$AdminUser = 'superadmin',
    [string]$AdminPassword = 'superadmin',
    [string]$MailpitUrl = 'http://localhost:8025',
    [int]$RetryWaitSeconds = 150
)

. "$PSScriptRoot\lab-common.ps1"

function Get-Mails([string]$Recipient) {
    $query = [uri]::EscapeDataString("to:$Recipient")
    $result = Invoke-RestMethod -Uri "$MailpitUrl/api/v1/search?query=$query" -UseBasicParsing
    @($result.messages)
}

function Wait-Mail([string]$Recipient, [string]$Subject, [int]$TimeoutSeconds) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $mail = Get-Mails $Recipient | Where-Object Subject -eq $Subject | Select-Object -First 1
        if ($mail) { return [pscustomobject]@{ Mail = $mail; Ms = [math]::Round($watch.Elapsed.TotalMilliseconds) } }
        Start-Sleep -Milliseconds 200
    }
    throw "No e-mail '$Subject' for $Recipient within $TimeoutSeconds s"
}

function Show-Outbox([string]$Recipient) {
    Write-Host '  notification_outbox (notifications_db):'
    Invoke-NotificationsDb "SELECT type, status, attempts, coalesce(left(regexp_replace(last_error, '\s+', ' ', 'g'), 110), '-') FROM notification_outbox WHERE recipient = '$Recipient' ORDER BY created_at" |
        ForEach-Object { Write-Host "    $_" }
}

function Show-Log([string]$Service, [string]$Pattern) {
    @(Invoke-Compose logs --since 3m $Service) | Where-Object { $_ -match $Pattern } |
        Select-Object -Last 2 | ForEach-Object { Write-Host "    $($_.Trim())" }
}

$token = Get-LabToken $AdminUser $AdminPassword
$suffix = Get-Date -Format 'HHmmss'
$username = "async-demo-$suffix"
$email = "$username@bricklayers.local"
$userId = $null
$mailpitStopped = $false

try {
    Write-Step '1. Create a user: the request waits neither for notification-service nor for the 1 s e-mail provider'
    $created = Invoke-Lab -Method POST -Path '/api/v1/admin/users' -Token $token -Body @{
        username = $username; email = $email; firstName = 'Async'; lastName = 'Demo'
        role = 'BUILDER'; companyId = $script:DemoCompanyId
    }
    Write-Response $created
    Assert-Lab ($created.Status -eq 201) 'user created (201)'
    $userId = $created.Body.id
    $welcome = Wait-Mail $email 'Welcome to Bricklayers' 10
    Write-Host "  e-mail arrived in Mailpit $($welcome.Ms) ms after the response"
    # A synchronous chain would put the e-mail into Mailpit before the response: it would be found at once.
    Assert-Lab ($welcome.Ms -ge 500) 'the response came back while the e-mail was still being sent'
    Write-Host '  user-service (hand-off):'
    Show-Log 'user-service' "accepted by notification-service.*$email"
    Write-Host '  notification-service (SMTP):'
    Show-Log 'notification-service' "Notification .* sent to $email"
    Show-Outbox $email

    Write-Step '2. Change the role: "account updated" e-mail, again after commit and off the request thread'
    $updated = Invoke-Lab -Method PUT -Path "/api/v1/admin/users/$userId" -Token $token -Body @{
        firstName = 'Async'; lastName = 'Demo'; email = $email; role = 'FOREMAN'; companyId = $script:DemoCompanyId
    }
    Write-Response $updated
    Assert-Lab ($updated.Status -eq 200) 'user updated (200)'
    $changed = Wait-Mail $email 'Your Bricklayers account was updated' 10
    Write-Host "  e-mail arrived $($changed.Ms) ms after the response"
    Assert-Lab ($changed.Ms -ge 500) 'again the response did not wait for the e-mail'

    Write-Step '3. Account overview: Keycloak + audit history in parallel, combined with thenCombine'
    $overview = Invoke-Lab -Path "/api/v1/admin/users/$userId/overview" -Token $token
    Write-Response $overview
    Assert-Lab ($overview.Status -eq 200) 'overview returned (200)'
    $o = $overview.Body
    Write-Host ("  keycloak: available={0} roles=[{1}] sessions={2} loadedBy={3}" -f `
        $o.keycloak.available, ($o.keycloak.realmRoles -join ','), $o.keycloak.activeSessions, $o.keycloak.loadedBy)
    Write-Host ("  audit:    available={0} totalActions={1} loadedBy={2}" -f `
        $o.audit.available, $o.audit.totalActions, $o.audit.loadedBy)
    Write-Host "  elapsedMs=$($o.elapsedMs)"
    Assert-Lab ($o.keycloak.loadedBy -like 'user-async-*' -and $o.audit.loadedBy -like 'user-async-*') 'both parts loaded on the user-async-* pool'
    Assert-Lab ($o.keycloak.loadedBy -ne $o.audit.loadedBy) 'on two different threads'

    if ($SimulateMailOutage) {
        Write-Step '4. Mail server down: the request still succeeds, the failure is logged, the row stays PENDING'
        Invoke-Compose stop mailpit | Out-Null
        $mailpitStopped = $true
        $outage = Invoke-Lab -Method PUT -Path "/api/v1/admin/users/$userId" -Token $token -Body @{
            firstName = 'Async'; lastName = 'Outage'; email = $email; role = 'FOREMAN'; companyId = $script:DemoCompanyId
        }
        Write-Response $outage
        Assert-Lab ($outage.Status -eq 200) 'user updated (200) although SMTP is down'
        Start-Sleep -Seconds 5
        Show-Log 'notification-service' 'Async task NotificationDispatcher.dispatch'
        Show-Outbox $email

        Write-Step "5. Mail server back: Quartz job notification-retry delivers the PENDING row (waiting up to $RetryWaitSeconds s)"
        Invoke-Compose start mailpit | Out-Null
        $mailpitStopped = $false
        $deadline = (Get-Date).AddSeconds($RetryWaitSeconds)
        do {
            Start-Sleep -Seconds 5
            $pending = [int]((@(Invoke-NotificationsDb "SELECT count(*) FROM notification_outbox WHERE recipient = '$email' AND status = 'PENDING'") -join '').Trim())
            Write-Host "  PENDING rows: $pending"
        } while ($pending -gt 0 -and (Get-Date) -lt $deadline)
        Assert-Lab ($pending -eq 0) 'retry job delivered the e-mail'
        Show-Log 'notification-service' 'Notification retry:'
        Show-Outbox $email
    }
} finally {
    if ($mailpitStopped) { Invoke-Compose start mailpit | Out-Null }
    if ($userId) {
        $deleted = Invoke-Lab -Method DELETE -Path "/api/v1/admin/users/$userId" -Token $token
        Write-Host ''
        Write-Host "  cleanup: DELETE $username -> $($deleted.Status)"
    }
}
