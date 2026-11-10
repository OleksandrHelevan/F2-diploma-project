<#
.SYNOPSIS
  Lab 2: cross-instance consistency and instance loss for user-service.

.DESCRIPTION
  Runs POST -> GET -> PUT -> GET -> DELETE for a throw-away user through the load balancer and checks that
  consecutive steps were served by different replicas (X-Instance-ID), that the data is consistent and that
  no session cookie is ever issued. With -SimulateFailure it kills the replica that handled the PUT
  (docker kill = SIGKILL) and verifies that the surviving replica keeps serving the same data.

.EXAMPLE
  .\scripts\demo-stateless.ps1
  .\scripts\demo-stateless.ps1 -SimulateFailure
#>
param(
    [switch]$SimulateFailure,
    [string]$AdminUser = 'superadmin',
    [string]$AdminPassword = 'superadmin'
)

. "$PSScriptRoot\lab-common.ps1"

$token = Get-LabToken $AdminUser $AdminPassword
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$responses = New-Object System.Collections.Generic.List[object]
$userId = $null
$victim = $null

function Track($result) {
    $responses.Add($result)
    Write-Response $result
    $result
}

try {
    Write-Step '1. POST creates a user (state goes to the shared PostgreSQL)'
    $created = Track (Invoke-Lab -Method POST -Path '/api/v1/admin/users' -Token $token -Body @{
        username  = "lab2-$stamp"
        email     = "lab2-$stamp@bricklayers.local"
        firstName = 'Stateless'
        lastName  = 'Demo'
        role      = 'BUILDER'
        companyId = $script:DemoCompanyId
    })
    Assert-Lab ($created.Status -eq 201) 'user created (201)'
    $userId = $created.Body.id

    Write-Step '2. GET on a different replica sees the new user'
    $read = Track (Invoke-OnOtherInstance -Path "/api/v1/admin/users/$userId" -Token $token -NotInstance $created.Instance)
    Assert-Lab ($read.Status -eq 200 -and $read.Body.username -eq "lab2-$stamp") "replica $($read.Instance) returns the user created on $($created.Instance)"

    Write-Step '3. PUT changes the user'
    $updated = Track (Invoke-Lab -Method PUT -Path "/api/v1/admin/users/$userId" -Token $token -Body @{
        firstName = 'Updated'
        lastName  = 'Demo'
        email     = "lab2-$stamp@bricklayers.local"
        role      = 'BUILDER'
        companyId = $script:DemoCompanyId
    })
    Assert-Lab ($updated.Status -eq 200) 'user updated (200)'

    Write-Step '4. GET on another replica returns the updated data'
    $reread = Track (Invoke-OnOtherInstance -Path "/api/v1/admin/users/$userId" -Token $token -NotInstance $updated.Instance)
    Assert-Lab ($reread.Body.firstName -eq 'Updated') "replica $($reread.Instance) sees the PUT made on $($updated.Instance)"

    if ($SimulateFailure) {
        Write-Step "5. docker kill (SIGKILL) of replica $($updated.Instance)"
        $replicas = Get-ReplicaMap 'user-service'
        $victim = $replicas[$updated.Instance]
        if (-not $victim) { throw "No container with hostname $($updated.Instance)" }
        & docker kill $victim | Out-Null
        # HAProxy marks a server DOWN after 2 failed checks 1 s apart.
        Start-Sleep -Seconds 3

        $survivors = @()
        for ($i = 0; $i -lt 6; $i++) {
            $after = Track (Invoke-Lab -Path "/api/v1/admin/users/$userId" -Token $token)
            Assert-Lab ($after.Status -eq 200 -and $after.Body.firstName -eq 'Updated') 'request served after the kill, data intact'
            $survivors += $after.Instance
        }
        Assert-Lab (-not ($survivors -contains $updated.Instance)) "killed replica $($updated.Instance) no longer receives traffic"
    }

    Write-Step 'Summary'
    $instances = @($responses | ForEach-Object Instance | Sort-Object -Unique)
    Assert-Lab ($instances.Count -ge 2) "requests were served by $($instances.Count) replicas: $($instances -join ', ')"
    Assert-Lab (-not ($responses | Where-Object SetCookie)) 'no response carried Set-Cookie (no sticky session)'
} finally {
    if ($victim) {
        Write-Step "Restarting $($updated.Instance)"
        & docker start $victim | Out-Null
        Wait-ContainerHealthy $victim
        Write-Host '  replica is healthy again'
    }
    if ($userId) {
        $deleted = Invoke-Lab -Method DELETE -Path "/api/v1/admin/users/$userId" -Token $token
        Write-Response $deleted
    }
}
