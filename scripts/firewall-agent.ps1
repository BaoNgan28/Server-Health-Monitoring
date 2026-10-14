# ============================================================================
#  Opens the inbound firewall rule an agent needs for its control channel.
#  Group 10 - Server Health Monitoring System
#
#  Run this on each machine that hosts an AGENT, once, from a PowerShell window
#  started with "Run as Administrator". The build never runs it: changing a
#  firewall is the operator's decision, not a side effect of compiling.
#
#  Only the TCP control port needs opening. The agent's telemetry goes outbound
#  over UDP, and Windows allows outbound traffic by default.
#
#  Usage:
#      powershell -ExecutionPolicy Bypass -File .\firewall-agent.ps1
#      powershell -ExecutionPolicy Bypass -File .\firewall-agent.ps1 -Port 5001
#      powershell -ExecutionPolicy Bypass -File .\firewall-agent.ps1 -Remove
# ============================================================================

[CmdletBinding()]
param(
    [int]    $Port     = 5000,
    [string] $RuleName = 'Group10 Server Health Monitoring - TCP control',
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
    -Description 'Inbound TCP control channel from the monitoring client (PING, AGENT_INFO, CONFIG_UPDATE).' `
    -Direction   Inbound `
    -Action      Allow `
    -Protocol    TCP `
    -LocalPort   $Port `
    -Profile     Domain,Private | Out-Null

Write-Host "Allowed inbound TCP $Port for the agent."
Write-Host ''
Write-Host 'Verify from the monitoring client machine with:'
Write-Host "    Test-NetConnection <this-machine-ip> -Port $Port"
