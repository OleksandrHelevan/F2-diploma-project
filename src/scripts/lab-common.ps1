# Shared helpers for the Lab 2-4 demo scripts. Dot-source it: . "$PSScriptRoot\lab-common.ps1"
# Works in Windows PowerShell 5.1 and PowerShell 7.

$ErrorActionPreference = 'Stop'

$script:SrcDir = Split-Path -Parent $PSScriptRoot
if (-not $script:BaseUrl) { $script:BaseUrl = 'http://localhost:8080' }
if (-not $script:TokenUrl) { $script:TokenUrl = 'http://localhost:8084/realms/bricklayers/protocol/openid-connect/token' }
# Optional compose project name (-p); empty means the default project of the src directory.
if (-not $script:ComposeProject) { $script:ComposeProject = $env:LAB_COMPOSE_PROJECT }
$script:DemoCompanyId = '5f0c8f3e-6d2a-4b7e-9c41-2a7d3e8b1c05'

function Invoke-Compose {
    $composeArgs = @('compose', '--project-directory', $script:SrcDir)
    if ($script:ComposeProject) { $composeArgs += @('-p', $script:ComposeProject) }
    $composeArgs += $args
    # docker compose writes progress to stderr; Windows PowerShell 5.1 would turn it into terminating errors.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker @composeArgs 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($LASTEXITCODE -ne 0) {
        $output | ForEach-Object { Write-Host "  $_" }
        throw "docker $($composeArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
    $output | Where-Object { $_ -notmatch '^\s*(Container|Network|Volume) ' }
}

# Runs SQL in the PostgreSQL of a compose service and returns rows as 'a|b|c' strings.
# Use single quotes only inside $Sql: Windows PowerShell 5.1 mangles double quotes passed to native commands.
$script:DbCredentials = @{}
function Invoke-ServiceDb([string]$Service, [string]$Sql) {
    if (-not $script:DbCredentials.ContainsKey($Service)) {
        $credentialSource = if ($Service -like 'postgres-users-replica*') { 'postgres-users' } else { $Service }
        $script:DbCredentials[$Service] = @(
            (@(Invoke-Compose exec -T $credentialSource printenv POSTGRES_USER) -join '').Trim(),
            (@(Invoke-Compose exec -T $credentialSource printenv POSTGRES_DB) -join '').Trim())
    }
    $user, $db = $script:DbCredentials[$Service]
    Invoke-Compose exec -T $Service psql -U $user -d $db -At -F '|' -c $Sql
}

function Invoke-UsersDb([string]$Sql) { Invoke-ServiceDb 'postgres-users' $Sql }

# notification-service: notification_outbox + QRTZ_* (Labs 5-6)
function Invoke-NotificationsDb([string]$Sql) { Invoke-ServiceDb 'notifications-db' $Sql }

function Get-LabToken([string]$Username, [string]$Password) {
    $body = @{ grant_type = 'password'; client_id = 'bricklayers-load-test'; username = $Username; password = $Password }
    (Invoke-RestMethod -Method Post -Uri $script:TokenUrl -Body $body -ContentType 'application/x-www-form-urlencoded').access_token
}

function Get-HeaderValue($Headers, [string]$Name) {
    if ($null -eq $Headers) { return $null }
    $value = $Headers[$Name]
    if ($value -is [array]) { return ($value -join ',') }
    return $value
}

# Sends one request and returns status, X-Instance-ID, X-Cache, Set-Cookie, elapsed time and parsed body.
function Invoke-Lab {
    param(
        [string]$Method = 'GET',
        [Parameter(Mandatory)][string]$Path,
        [string]$Token,
        $Body
    )
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$($script:BaseUrl)$Path"; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5))
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    if ($PSVersionTable.PSVersion.Major -ge 7) { $params['SkipHttpErrorCheck'] = $true }

    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $responseHeaders = $response.Headers
        $content = $response.Content
    } catch [System.Net.WebException] {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) { throw }
        $status = [int]$errorResponse.StatusCode
        $responseHeaders = $errorResponse.Headers
        $content = (New-Object IO.StreamReader($errorResponse.GetResponseStream())).ReadToEnd()
    }
    $watch.Stop()

    $parsed = $null
    if ($content) {
        if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
        try { $parsed = $content | ConvertFrom-Json } catch { $parsed = $content }
    }
    [pscustomobject]@{
        Method    = $Method
        Path      = $Path
        Status    = $status
        Instance  = Get-HeaderValue $responseHeaders 'X-Instance-ID'
        Cache     = Get-HeaderValue $responseHeaders 'X-Cache'
        SetCookie = Get-HeaderValue $responseHeaders 'Set-Cookie'
        Ms        = [math]::Round($watch.Elapsed.TotalMilliseconds, 1)
        Body      = $parsed
    }
}

function Assert-Lab([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "CHECK FAILED: $Message" }
    Write-Host "  ok  $Message" -ForegroundColor Green
}

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "== $Text" -ForegroundColor Cyan
}

function Write-Response($Result) {
    $cache = if ($Result.Cache) { " X-Cache=$($Result.Cache)" } else { '' }
    Write-Host ("  {0,-6} {1,-48} -> {2}  X-Instance-ID={3}{4}  {5} ms" -f `
        $Result.Method, $Result.Path, $Result.Status, $Result.Instance, $cache, $Result.Ms)
}

# Hostname (= X-Instance-ID) -> container id for every replica of a compose service.
function Get-ReplicaMap([string]$Service) {
    $map = [ordered]@{}
    foreach ($id in @(Invoke-Compose ps -q $Service)) {
        if (-not $id) { continue }
        $hostname = (& docker inspect --format '{{.Config.Hostname}}' $id).Trim()
        $map[$hostname] = $id.Trim()
    }
    return $map
}

function Wait-ContainerHealthy([string]$ContainerId, [int]$TimeoutSeconds = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerId).Trim()
        if ($state -eq 'healthy' -or $state -eq 'running') { return }
        Start-Sleep -Seconds 2
    }
    throw "Container $ContainerId did not become healthy within $TimeoutSeconds s"
}

# Repeats GET until a replica other than $NotInstance answers, so the read provably crosses instances.
function Invoke-OnOtherInstance([string]$Path, [string]$Token, [string]$NotInstance, [int]$Attempts = 10) {
    for ($i = 0; $i -lt $Attempts; $i++) {
        $result = Invoke-Lab -Path $Path -Token $Token
        if ($result.Instance -ne $NotInstance) { return $result }
    }
    throw "Only $NotInstance answered $Attempts requests to $Path; is a second replica running?"
}
