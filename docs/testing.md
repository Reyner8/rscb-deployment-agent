# Testing

## Unit test

```bash
./gradlew test
```

Cakupan:

| Kelas | Yang diuji |
| --- | --- |
| `VersionsTest` | perbandingan versi numeric-dot (port dari backend), fallback kesetaraan |
| `ConfigLoaderTest` | parsing YAML, default, kunci wajib |
| `StateStoreTest` | roundtrip state, tulis atomik, recovery file corrupt |
| `ApiClientTest` | kontrak request/response via HttpServer palsu JDK: field persis, header `x-api-key`, 401, metadata checksum, download streaming |
| `RetryUtilTest` | retry terbatas, backoff, 4xx tidak diulang |
| `ChecksumVerifierTest` | SHA-256 + ukuran, kasus tidak cocok |
| `FileManagerTest` | unzip, zip-slip, backup, restore, prune, cleanup |
| `DeploymentManagerTest` | urutan status, WAITING, kegagalan checksum/unzip/launcher + restore, idempotency |

Keterbatasan: implementasi `WindowsProcessDetector` tidak dapat diuji di
Linux — yang diuji adalah kontrak interface-nya; deteksi proses Windows
divalidasi pada smoke test di komputer Windows nyata.

## Smoke test manual (end-to-end dengan server)

Prasyarat: stack server jalan (`docker compose up -d --build` di repo
`desktop-deployment-system`), dashboard bisa diakses.

1. Siapkan folder SIMRS dummy berisi `run.bat` yang menampilkan sesuatu
   lalu tetap hidup (mis. `timeout` loop), plus file `application.jar`.
2. Buat `config.yml` dari `config/config.example.yml`:
   - `server.baseUrl: http://localhost:3000`
   - `application.path: <folder dummy>`
   - interval pendek untuk mempermudah observasi.
3. Jalankan agent: `./gradlew run --args="--config /path/config.yml"`.
4. Verifikasi di dashboard: device muncul dengan status ONLINE.
5. Di dashboard: Releases → New Release → upload ZIP dummy (isi beda
   versi) → Publish.
6. Di dashboard: Deployments → buat deployment release tsb. ke device.
7. Amati log agent: DOWNLOADING → VERIFYING → INSTALLING → STARTING →
   SUCCESS, dan isi folder dummy berubah sesuai ZIP.
8. Cek di dashboard: deployment SUCCESS, versi device ter-update.
9. Uji jalur WAITING: jalankan `run.bat` dummy (proses hidup) sebelum
   membuat deployment baru → agent harus melapor WAITING, bukan memaksa
   mati; tutup proses → instalasi lanjut.
10. Uji ketahanan server: matikan container backend saat agent berjalan →
    log menunjukkan retry/kegagalan yang tertangani, agent tidak mati;
    nyalakan lagi → operasi berlanjut.
