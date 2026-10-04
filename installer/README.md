# Installer Agent Windows

Panduan memasang agent sebagai Windows Service dengan runtime Java
ter-bundel (komputer unit tidak perlu Java terpasang global).

## Artefak yang diperlukan

| Artefak | Cara mendapatkan | Lokasi yang diharapkan |
| --- | --- | --- |
| Fat jar agent | `gradlew shadowJar` | `build\libs\rscb-deployment-agent-*-all.jar` |
| Runtime jlink | `runtime\build-runtime.ps1` (butuh JDK 21 di Windows) | `runtime\jlink-image\` |
| WinSW v2 | Unduh manual [winsw release](https://github.com/winsw/winsw/releases) → `WinSW-x64.exe` | `installer\tools\WinSW-x64.exe` |
| Config final unit | Salin `config\config.example.yml` → isi `baseUrl` dan `application.path` unit | `config\config.yml` |

Folder `installer\tools\` dan `runtime\jlink-image\` di-gitignore —
keduanya dihasilkan/diunduh saat proses build, bukan disimpan di repo.

## Langkah pemasangan di komputer unit

1. Siapkan artefak di atas di mesin build (Windows).
2. Salin folder repo (atau paket hasil build) ke komputer unit.
3. Buka PowerShell **sebagai Administrator**, masuk ke folder `installer\`.
4. Jalankan:

   ```powershell
   .\install.ps1
   ```

   Script menyalin payload ke `%ProgramData%\RSCB\DeploymentAgent\`,
   memasang service `RSCBDeploymentAgent` (start otomatis, delayed),
   dan langsung menyalakannya.

5. Verifikasi:

   ```powershell
   Get-Service RSCBDeploymentAgent
   Get-Content "$env:ProgramData\RSCB\DeploymentAgent\logs\agent.log" -Tail 20
   ```

   Di dashboard deployment, device harus muncul berstatus ONLINE.

## Melepas

```powershell
.\uninstall.ps1              # service dilepas, config/state dipertahankan
.\uninstall.ps1 -PurgeData   # service + seluruh data dihapus
```

## Troubleshooting

- **Service langsung mati**: cek `logs\service*.log` (log WinSW) — biasanya
  path runtime/jar salah atau `config.yml` tidak valid.
- **Device tidak muncul di dashboard**: pastikan `server.baseUrl` benar dan
  port backend terjangkau dari komputer unit (`Test-NetConnection <host>
  -Port 3000`).
- **Registrasi ulang berkala**: token dirotasi tiap register — pastikan
  hanya satu instance agent per unit yang berjalan.
