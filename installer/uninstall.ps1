# Melepas Windows Service RSCB Deployment Agent.
# Jalankan di PowerShell sebagai Administrator.

param(
    [string]$InstallDir = "$env:ProgramData\RSCB\DeploymentAgent",
    [switch]$PurgeData  # hapus juga config, state, dan log
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path "$InstallDir\RSCBDeploymentAgent.exe")) {
    Write-Error "Service tidak ditemukan di $InstallDir"
}

Push-Location $InstallDir
try {
    .\RSCBDeploymentAgent.exe stop
    .\RSCBDeploymentAgent.exe uninstall
} finally {
    Pop-Location
}

if ($PurgeData) {
    Remove-Item $InstallDir -Recurse -Force
    Write-Host "Service dilepas dan seluruh data dihapus."
} else {
    Write-Host "Service dilepas. Folder $InstallDir dipertahankan (config + state)."
    Write-Host "Gunakan -PurgeData untuk menghapus semuanya."
}
