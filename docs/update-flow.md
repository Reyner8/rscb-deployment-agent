# Alur Update Agent

Diagram mengikuti `architecture.md` §8–§9 dan `design-patterns.md` §23–§27
(repo server). Agent hanya mengeksekusi deployment yang dibuat admin dari
web dashboard — server-centric.

## Alur utama

```
[heartbeat + cek update berkala]
        |
        v
GET /agents/updates ──► hasUpdate=false ──► selesai
        |
        hasUpdate=true & deploymentId ada & status non-terminal
        v
1. lapor DOWNLOADING → unduh artifact ke .deployment/downloads/
2. lapor VERIFYING   → verifikasi ukuran + SHA-256
        |                         |
        | cocok                   | tidak cocok
        |                         v
        |                  lapor FAILED, hapus file,
        |                  instalasi TIDAK disentuh
        v
3. SIMRS berjalan?
        |
   +----+----+
   |         |
  YA        TIDAK
   |         |
   v         |
4. lapor WAITING        (menunggu user menutup SIMRS;
   polling tiap waitPollSeconds — TIDAK ADA force kill)
   |         |
   | timeout maxWaitMinutes ──► lapor FAILED
   v
5. lapor INSTALLING → unzip (guard zip-slip) ke staging →
   backup isi instalasi lama → replace
        |                          |
        | sukses                   | gagal → restore backup
        v                          v
6. lapor STARTING → jalankan launcher kembali
        |                          |
        | proses terdeteksi        | gagal → restore backup
        v
7. state.currentVersion=versi baru
   lapor SUCCESS
```

## Keputusan desain penting

- **Server-centric**: agent tidak pernah membuat release/deployment;
  `hasUpdate=true` tanpa `deploymentId` hanya dicatat sebagai info —
  admin harus membuat deployment dari dashboard.
- **Idempotency (§27)**: bila `deploymentId` sama dengan yang terakhir
  sukses dan versi lokal sudah cocok, agent cukup melapor `SUCCESS` ulang
  tanpa instalasi ulang. Ini menutup kasus laporan `SUCCESS` yang gagal
  terkirim ke server.
- **Update safety (§9/§17)**: SIMRS yang sedang dipakai tidak pernah
  dipaksa mati. Agent masuk `WAITING` dan menunggu; heartbeats tetap jalan
  sehingga device tidak dianggap OFFLINE.
- **Recovery (§25)**: kegagalan saat instalasi atau start mengembalikan
  instalasi sebelumnya dari backup. Kegagalan checksum/jaringan tidak pernah
  menyentuh instalasi.
- **Checksum**: ukuran + SHA-256 diverifikasi setelah download, sebelum
  instalasi. Metadata checksum diambil dari
  `GET /agents/artifacts/:id/download-url`.
- **Download**: URL presigned (MinIO) diunduh tanpa kredensial; path
  relatif (storage lokal) diunduh dengan header `x-api-key` milik device.
- **Laporan status**: dibungkus retry terbatas (3x). Bila tetap gagal,
  proses lokal tetap berjalan; status berikutnya akan mencerminkan kondisi
  terkini (server menolak transisi yang tidak valid — itu aman).
