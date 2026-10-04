# RSCB Deployment Agent

Client Deployment Agent — aplikasi Java 21 yang terpasang di komputer unit
untuk menerima dan mengeksekusi update SIMRS dari RSCB Deployment Server
(repo: `desktop-deployment-system`). Agent **tidak** menyimpan credential
database/MinIO dan hanya berkomunikasi melalui Deployment API.

## Arsitektur singkat

```
Agent
├── api/       ApiClient — klien Deployment API (register, heartbeat, updates,
│              status, download) dengan autentikasi header x-api-key
├── config/    AgentConfig + ConfigLoader — konfigurasi YAML
├── state/     AgentState + StateStore — identitas & state lokal (JSON atomik)
├── device/    DeviceInfoProvider — hostname, IP, OS
├── update/    UpdateHandler — menerima hasil cek update berkala
├── deploy/    Mesin instalasi: checksum, deteksi proses, file manager (Task 2)
└── util/      Versions — perbandingan versi numeric-dot (port dari backend)
```

Prinsip yang dipegang (lihat AGENTS.md repo server):

- **Server-centric**: agent hanya mengeksekusi deployment yang dibuat admin;
  tidak pernah membuat release/mengubah metadata.
- **Offline tolerant**: server mati tidak menghentikan agent dan tidak
  mengganggu SIMRS; semua kegagalan dicoba ulang pada siklus berikutnya.
- **Update safety**: SIMRS yang sedang berjalan tidak pernah dipaksa mati —
  agent melaporkan `WAITING` dan menunggu sampai aplikasi ditutup.

## Build

```bash
./gradlew build          # compile + test
./gradlew test           # unit test saja
./gradlew installDist    # distribusi dengan script start di build/install/
```

Butuh JDK 21.

## Menjalankan

1. Salin `config/config.example.yml` ke lokasi konfigurasi:
   - Windows: `%PROGRAMDATA%\RSCB\DeploymentAgent\config.yml`
   - Lainnya: `./config.yml`
2. Isi `server.baseUrl` dan `application.path` (lokasi instalasi SIMRS).
3. Jalankan:

```bash
./gradlew run --args="--config /path/ke/config.yml"
```

Agent akan register otomatis saat pertama kali jalan (deviceId dibuat lokal
lalu disimpan bersama token di `agent-state.json` sebelah file config).

## Status

| Fase | Isi | Status |
| --- | --- | --- |
| 1 | Skeleton: config, state, API client, registrasi, loop heartbeat + cek update | selesai |
| 2 | Mesin deployment: download, verifikasi SHA-256, instalasi aman | selesai |
| 3 | Ketahanan: retry, re-register, logging file, docs operasional | selesai |
| 4 | Packaging Windows: fat jar, jlink runtime, WinSW service | selesai |
