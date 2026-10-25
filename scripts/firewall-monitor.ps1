# ============================================================================
#  Opens the inbound firewall rule the monitoring client needs.
#  Group 10 - Server Health Monitoring System
#
#  Run this on the machine that hosts the MONITORING CLIENT, once, from a
#  PowerShell window started with "Run as Administrator". The build never runs
#  it: changing a firewall is the operator's decision, not a side effect of
#  compiling.
#
#  Usage:
#      powershell -ExecutionPolicy Bypass -File .\firewall-monitor.ps1
#      powershell -ExecutionPolicy Bypass -File .\firewall-monitor.ps1 -Port 6000
#      powershell -ExecutionPolicy Bypass -File .\firewall-monitor.ps1 -Remove
# ============================================================================

[CmdletBinding()]
param(
    [int]    $Port        = 6000,
    [string] $RuleName    = 'Group10 Server Health Monitoring - UDP telemetry',
    [switch] $Remove
)

$ErrorActionPreference = 'Stop'

function Test-Administrator {
    $identity  = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrator)) {
    Write-Warning 'This script changes the Windows firewall and needs administrator rights.'
    Write-Host   'Close this window, right-click PowerShell, choose "Run as Administrator", then run it again.'
    exit 1
}

if ($Remove) {
    $existing = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
    if ($null -eq $existing) {
        Write-Host "No rule named '$RuleName' exists; nothing to remove."
    } else {
        Remove-NetFirewallRule -DisplayName $RuleName
        Write-Host "Removed the firewall rule '$RuleName'."
    }
    exit 0
}

$existing = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    Write-Host "The rule '$RuleName' already exists; leaving it as it is."
    Get-NetFirewallRule -DisplayName $RuleName | Get-NetFirewallPortFilter |
        Format-Table Protocol, LocalPort -AutoSize
    exit 0
}

New-NetFirewallRule `
    -DisplayName $RuleName `
    -Description 'Inbound UDP telemetry and heartbeats from monitored agents.' `
    -Direction   Inbound `
    -Action      Allow `
    -Protocol    UDP `
    -LocalPort   $Port `
    -Profile     Domain,Private | Out-Null

Write-Host "Allowed inbound UDP $Port for the monitoring client."
Write-Host ''
Write-Host 'Note: the rule covers the Domain and Private profiles only. If the machine'
Write-Host 'is on a network Windows classes as Public, either change that network to'
Write-Host 'Private or add -Profile Any yourself, knowing what that exposes.'
