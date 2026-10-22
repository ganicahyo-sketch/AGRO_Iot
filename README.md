# AGRO-IoT 3.0.0 — Industrial Weather Station Dashboard

Nama aplikasi Android: **AGRO-IoT**. Versi: **3.0.0**. Kredit: **ganicahyo@2026**. Package aplikasi baru: `id.ganicahyo.agroiot`, sehingga dapat dipasang berdampingan dengan versi lama.

Project Android native (Java) untuk memantau data terbaru ThingSpeak dengan dashboard bergaya industrial yang meniru referensi visual: banner stasiun cuaca, panel hari/tanggal/jam dan timestamp data, baris mode jaringan/RSSI/IP, delapan kartu data berwarna, ringkasan kondisi, indikator koneksi data, dan status/refresh di bagian bawah.

## Fitur
- Judul dashboard dapat diubah melalui **Pengaturan** dan tersimpan lokal.
- Lima profil ThingSpeak tersimpan; pilih profil aktif dari strip sumber data.
- Setiap profil memiliki nama stasiun, Channel ID, Read API Key, serta nama dan satuan 8 field.
- Read API Key tampak saat pengaturan dibuka dan dapat disembunyikan.
- Nama field mencoba mengambil metadata channel ThingSpeak jika tersedia; nama/satuan manual bisa meng-override metadata. ThingSpeak tidak menyediakan satuan field sebagai metadata standar, jadi satuan diatur manual.
- 8 kartu berwarna dengan angka, label, satuan, dan indeks field.
- Waktu WIB real-time; waktu data terakhir dari `created_at`; waktu akses terakhir aplikasi.
- Refresh manual dan otomatis setiap 30 detik.
- Status ONLINE / OFFLINE / CACHE dan cache feed terakhir per profil.
- Unduh feed ke CSV melalui pemilih file Android: rentang waktu `start/end` atau semua histori, diproses bertahap per 8.000 entry agar penggunaan RAM tidak menumpuk.
- Tampilan tidak memakai library UI pihak ketiga.

## Build APK AGRO-IoT
Memerlukan JDK 17, Android SDK Platform 36 dan Build Tools 36.0.0. Untuk build lokal dengan Gradle 8.13:

```bash
gradle assembleDebug
```

APK debug akan dihasilkan di `app/build/outputs/apk/debug/app-debug.apk`.

Workflow `.github/workflows/build-apk.yml` dapat dijalankan dari GitHub Actions (`Build AGRO-IoT APK`) atau otomatis ketika perubahan dipush ke branch `main`. Hasilnya diunggah sebagai artifact bernama `agro-iot-v3-apk`.

## Konfigurasi ThingSpeak
Di dashboard, tekan ikon pengaturan. Isi hingga 5 Channel ID/Read API Key dan pilih profil aktif. Read API Key bisa kosong untuk channel publik. Kosongkan nama field untuk mencoba menggunakan nama field dari metadata channel; kosongkan satuan untuk memakai satuan default aplikasi.

Unduh CSV tersedia dari tombol `↓ CSV` di banner. Rentang waktu memakai format ISO lokal `YYYY-MM-DDTHH:MM:SS` (WIB); centang `UNDUH SEMUA HISTORY` untuk mengabaikan rentang dan mengambil histori channel aktif bertahap.

## Identitas aplikasi
- Nama launcher: AGRO-IoT
- Versi: 3.0.0 (versionCode 3)
- Kredit: ganicahyo@2026
- Judul stasiun pada banner dashboard masih dapat diubah melalui Pengaturan.
