# Konfigurasi Agent

Semua konfigurasi dibaca dari file YAML — tidak ada nilai yang hard-coded
(AGENTS.md bagian Client Installation Path). Salin
`config/config.example.yml` ke lokasi konfigurasi lalu sesuaikan.

## Lokasi file config

| Prioritas | Lokasi | Keterangan |
| --- | --- | --- |
| 1 | argumen `--config /path/config.yml` | override eksplisit saat menjalankan |
| 2 | `%PROGRAMDATA%\RSCB\DeploymentAgent\config.yml` | default Windows |
| 3 | `./config.yml` | fallback (development) |

File state agent (`agent-state.json`) dibuat otomatis di folder yang sama
dengan file config; log di `logs/agent.log` (rotasi 3 x 5 MB).

## Daftar kunci

### server

| Kunci | Default | Keterangan |
| --- | --- | --- |
| `baseUrl` | — (wajib) | Base URL backend deployment, tanpa `/api/v1`. Trailing slash dibuang otomatis. |
| `timeoutSeconds` | `30` | Timeout HTTP per request, termasuk koneksi. |

### intervals

| Kunci | Default | Keterangan |
| --- | --- | --- |
| `heartbeatSeconds` | `60` | Jarak heartbeat. Server menganggap device OFFLINE setelah 5 menit tanpa heartbeat — jangan lebih lambat dari itu. |
| `updateCheckSeconds` | `300` | Jarak cek update. Ditambah jitter acak 0–30 detik agar device tidak menyerang server bersamaan. |

### application

| Kunci | Default | Keterangan |
| --- | --- | --- |
| `path` | — (wajib) | Folder instalasi SIMRS di komputer unit, mis. `C:\RSCB\SIMRS`. |
| `launcher` | `run.bat` | File launcher yang dijalankan kembali setelah instalasi. |

### deployment

| Kunci | Default | Keterangan |
| --- | --- | --- |
| `maxWaitMinutes` | `120` | Batas waktu agent menunggu SIMRS ditutup user saat status `WAITING`. Setelah lewat, deployment dilaporkan `FAILED` dan akan diproses ulang pada siklus berikutnya. |
| `waitPollSeconds` | `30` | Interval polling memeriksa apakah SIMRS masih berjalan. |

### backup

| Kunci | Default | Keterangan |
| --- | --- | --- |
| `keep` | `1` | Jumlah backup instalasi sebelumnya yang dipertahankan. Backup lama dihapus otomatis setelah instalasi sukses. |

## File state (`agent-state.json`)

Dikelola agent, jangan diedit manual:

| Field | Keterangan |
| --- | --- |
| `deviceId` | Identitas device yang dibuat saat pertama kali jalan (UUID lokal). |
| `token` | Token device dari registrasi. Dirotasi server saat register ulang — agent otomatis register ulang bila menerima 401. |
| `currentVersion` | Versi SIMRS yang terpasang menurut agent. |
| `lastDeploymentId` / `lastDeploymentResult` / `lastDeploymentVersion` | Hasil deployment terakhir — dipakai untuk idempotency: deployment yang sudah sukses tidak diinstal ulang. |

File ditulis secara atomik (tulis ke `.tmp` lalu rename). Bila file corrupt
saat startup, agent mengarsipkannya sebagai `agent-state.json.corrupt-*`,
mendaftar ulang sebagai device baru, dan melanjutkan operasi.
