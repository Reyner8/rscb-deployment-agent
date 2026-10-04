# Membangun image runtime JRE minimal dengan jlink (JDK 21, Windows).
# Jalankan dari folder runtime\ dengan JDK 21 di PATH.
#
# Hasil: runtime\jlink-image\ (dipakai installer sebagai runtime privat
# agent — komputer unit tidak memerlukan Java terpasang global).

param(
    [string]$JarPath = "..\build\libs\rscb-deployment-agent-0.1.0-all.jar",
    [string]$OutputDir = ".\jlink-image"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command jlink -ErrorAction SilentlyContinue)) {
    Write-Error "jlink tidak ditemukan di PATH. Pastikan JDK 21 terpasang."
}

# Saran modul dari jdeps (opsional, untuk verifikasi manual):
if (Test-Path $JarPath) {
    Write-Host "== jdeps (ringkasan modul yang disarankan) =="
    jdeps --print-module-deps --ignore-missing-deps $JarPath 2>$null
}

$modules = "java.base,java.logging,java.xml,jdk.crypto.ec"

Write-Host "Membangun image jlink -> $OutputDir"
jlink --no-header-files --no-man-pages --strip-debug --compress=2 `
    --add-modules $modules `
    --output $OutputDir

Write-Host "Selesai. Runtime privat tersedia di: $OutputDir"
Write-Host "Contoh menjalankan agent:"
Write-Host "  $OutputDir\bin\java.exe -jar <agent.jar> --config <config.yml>"
