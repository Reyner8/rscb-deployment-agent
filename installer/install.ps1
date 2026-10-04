# Memasang RSCB Deployment Agent sebagai Windows Service.
# Jalankan di PowerShell sebagai Administrator dari folder installer\.
#
# Payload yang diharapkan sudah disiapkan (lihat installer\README.md):
#   ..\build\libs\rscb-deployment-agent-<versi>-all.jar  (gradlew shadowJar)
#   ..\runtime\jlink-image\                              (build-runtime.ps1)
#   ..\installer\tools\WinSW-x64.exe                     (unduh manual)
#   ..\config\config.yml                                 (config final unit)

param(
    [string]$InstallDir = "$env:ProgramData\RSCB\DeploymentAgent",
    [string]$JarPath = "..\build\libs",
    [string]$RuntimeDir = "..\runtime\jlink-image",
    [string]$ConfigPath = "..\config\config.yml"
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path "$PSScriptRoot\.."

# 1. Susun folder instalasi
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

$jar = Get-ChildItem $JarPath -Filter "rscb-deployment-agent-*-all.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Write-Error "Fat jar tidak ditemukan di $JarPath (jalankan: gradlew shadowJar)" }
if (-not (Test-Path "$RuntimeDir\bin\java.exe")) {
    Write-Error "Runtime jlink tidak ditemukan di $RuntimeDir (jalankan runtime\build-runtime.ps1)"
}
if (-not (Test-Path $ConfigPath)) { Write-Error "config.yml tidak ditemukan di $ConfigPath" }

Copy-Item $jar.FullName "$InstallDir\agent.jar" -Force
Copy-Item $RuntimeDir "$InstallDir\runtime\jlink-image" -Recurse -Force
Copy-Item $ConfigPath "$InstallDir\config.yml" -Force

# 2. Siapkan WinSW (executable service)
$winSw = "$PSScriptRoot\tools\WinSW-x64.exe"
if (-not (Test-Path $winSw)) {
    Write-Error "WinSW-x64.exe tidak ditemukan di $PSScriptRoot\tools\. " +
        "Unduh dari https://github.com/winsw/winsw/releases lalu letakkan di folder tools\."
}
Copy-Item $winSw "$InstallDir\RSCBDeploymentAgent.exe" -Force
Copy-Item "$PSScriptRoot\service.xml" "$InstallDir\RSCBDeploymentAgent.xml" -Force

# 3. Pasang dan nyalakan service
Push-Location $InstallDir
try {
    .\RSCBDeploymentAgent.exe install
    .\RSCBDeploymentAgent.exe start
    .\RSCBDeploymentAgent.exe status
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Agent terpasang di $InstallDir dan service RSCBDeploymentAgent berjalan."
Write-Host "Log service: $InstallDir\logs\service*.log ; log agent: $InstallDir\logs\agent.log"
