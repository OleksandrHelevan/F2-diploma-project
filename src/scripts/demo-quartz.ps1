<#
.SYNOPSIS
  Lab 6: the Quartz schedule lives in PostgreSQL, survives restarts of every replica, and a redeploy with a
  changed definition updates it instead of silently ignoring or duplicating it.

.DESCRIPTION
    1. Cluster: every notification-service replica is a Quartz node (qrtz_scheduler_state).
    2. Jobs and triggers stored in the JDBC job store: names, groups, job keys, misfire, time zone, data.
    3. docker compose restart notification-service: all nodes go down at once; the cron triggers keep their
       next fire time and nothing is duplicated.
    4. -ChangeDefinition: a "new version" with NOTIFICATION_RETRY_INTERVAL=30s is deployed; the reconciler
       reschedules exactly that trigger, then the original 60 s definition is restored the same way.

.EXAMPLE
  .\scripts\demo-quartz.ps1
  .\scripts\demo-quartz.ps1 -ChangeDefinition
#>
param(
    [switch]$ChangeDefinition
)

. "$PSScriptRoot\lab-common.ps1"

$misfireNames = @{
    '-1' = 'IGNORE_MISFIRE'; '0' = 'SMART_POLICY'
    'SIMPLE|1' = 'FIRE_NOW'; 'SIMPLE|4' = 'RESCHEDULE_NEXT_WITH_REMAINING_COUNT'
    'CRON|1' = 'FIRE_ONCE_NOW'; 'CRON|2' = 'DO_NOTHING'
}

function Get-Triggers {
    @(Invoke-NotificationsDb @'
SELECT t.trigger_group || '.' || t.trigger_name, t.job_group || '.' || t.job_name, t.trigger_type, t.misfire_instr,
       coalesce(c.cron_expression || ' ' || c.time_zone_id, 'every ' || (s.repeat_interval / 1000) || ' s'),
       to_char(to_timestamp(t.next_fire_time / 1000) AT TIME ZONE 'Europe/Kyiv', 'YYYY-MM-DD HH24:MI:SS'),
       t.trigger_state
FROM qrtz_triggers t
LEFT JOIN qrtz_cron_triggers c USING (sched_name, trigger_name, trigger_group)
LEFT JOIN qrtz_simple_triggers s USING (sched_name, trigger_name, trigger_group)
ORDER BY 1
'@) | ForEach-Object {
        $f = $_ -split '\|'
        $misfire = $misfireNames["$($f[2])|$($f[3])"]
        if (-not $misfire) { $misfire = $misfireNames[$f[3]] }
        [pscustomobject]@{
            Trigger          = $f[0]
            Job              = $f[1]
            Schedule         = $f[4]
            Misfire          = $misfire
            'Next fire (Kyiv)' = $f[5]
            State            = $f[6]
        }
    }
}

function Show-Triggers($Triggers) {
    $Triggers | Format-Table -AutoSize | Out-String -Width 220 | Write-Host
}

function Wait-NotificationServiceHealthy {
    foreach ($id in (Get-ReplicaMap 'notification-service').Values) { Wait-ContainerHealthy $id 180 }
}

function Show-ReconcilerLog {
    @(Invoke-Compose logs --since 3m notification-service) |
        Where-Object { $_ -match 'Quartz (schedule reconciled|trigger|job)' } |
        ForEach-Object { Write-Host "    $($_.Trim())" }
}

Write-Step '1. Every notification-service replica is a node of one clustered Quartz scheduler'
Invoke-NotificationsDb "SELECT instance_name, to_char(to_timestamp(last_checkin_time / 1000), 'HH24:MI:SS'), checkin_interval FROM qrtz_scheduler_state ORDER BY 1" |
    ForEach-Object { $f = $_ -split '\|'; Write-Host "  node $($f[0])  last check-in $($f[1]) UTC  every $($f[2]) ms" }
$replicas = (Get-ReplicaMap 'notification-service').Count
$nodes = [int]((@(Invoke-NotificationsDb 'SELECT count(*) FROM qrtz_scheduler_state') -join '').Trim())
Assert-Lab ($nodes -ge $replicas) "$nodes Quartz node(s) for $replicas replica(s)"

Write-Step '2. Jobs and triggers in the JDBC job store (notifications_db, QRTZ_* tables created by Liquibase changeset 002)'
Invoke-NotificationsDb "SELECT job_group || '.' || job_name, job_class_name, is_nonconcurrent, requests_recovery FROM qrtz_job_details ORDER BY 1" |
    ForEach-Object { $f = $_ -split '\|'; Write-Host "  job $($f[0])  $($f[1].Split('.')[-1])  nonconcurrent=$($f[2]) recovery=$($f[3])" }
$before = Get-Triggers
Show-Triggers $before
Assert-Lab ($before.Count -eq 3) '3 triggers: notification-retry has two, notification-retention one'

Write-Step '3. Restart every replica at once: the stored schedule survives, nothing is duplicated'
Invoke-Compose restart notification-service | Out-Null
Wait-NotificationServiceHealthy
$after = Get-Triggers
Show-Triggers $after
Show-ReconcilerLog
Assert-Lab ($after.Count -eq 3) 'still 3 triggers after the restart'
foreach ($cron in $before | Where-Object Schedule -notlike 'every*') {
    $same = $after | Where-Object Trigger -eq $cron.Trigger
    Assert-Lab ($same.'Next fire (Kyiv)' -eq $cron.'Next fire (Kyiv)') "$($cron.Trigger) keeps its next fire time $($cron.'Next fire (Kyiv)')"
}

if ($ChangeDefinition) {
    Write-Step '4. Deploy a "new version" with the frequent retry trigger every 30 s'
    $env:NOTIFICATION_RETRY_INTERVAL = '30s'
    try {
        Invoke-Compose up -d notification-service | Out-Null
        Wait-NotificationServiceHealthy
    } finally {
        Remove-Item Env:NOTIFICATION_RETRY_INTERVAL
    }
    $changed = Get-Triggers
    Show-Triggers $changed
    Show-ReconcilerLog
    $frequent = $changed | Where-Object Trigger -like '*notification-retry-frequent'
    Assert-Lab ($frequent.Schedule -eq 'every 30 s') 'changed trigger was rescheduled to 30 s'
    Assert-Lab ($changed.Count -eq 3) 'and not duplicated'

    Write-Step '5. Roll back to the 60 s definition'
    Invoke-Compose up -d notification-service | Out-Null
    Wait-NotificationServiceHealthy
    $restored = Get-Triggers
    Show-Triggers $restored
    Assert-Lab ((($restored | Where-Object Trigger -like '*notification-retry-frequent').Schedule) -eq 'every 60 s') 'back to every 60 s'
}
