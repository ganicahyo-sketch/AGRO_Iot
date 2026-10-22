# AGRO-IoT Android v3.1.1

Proyek Android native untuk dashboard stasiun cuaca ThingSpeak.

## Fitur pada versi ini

- Ekspor histori ThingSpeak ke CSV: pilih rentang tanggal menggunakan kalender Android atau unduh seluruh histori.
- Delapan kartu dashboard dapat dipetakan ke parameter masing-masing melalui ikon/judul kartu. Ikon dan satuan mengikuti pilihan parameter.
- Preset parameter: suhu udara, kelembapan udara, tekanan udara, delta tekanan udara, altitude, intensitas cahaya, PAR, VPD, lama penyinaran matahari, ET₀, kecepatan angin, arah angin, curah hujan, dan status hujan/tidak hujan.
- Parameter kustom mendukung nama dan satuan yang diisi sendiri melalui pilihan Manual/Kustom atau Pengaturan.
- Nilai kartu juga dapat dimasukkan secara manual dengan mengetuk angka pada kartu. Tekan **Kembali Live** untuk kembali menampilkan data ThingSpeak; nilai manual hanya menjadi override tampilan lokal di perangkat dan tidak ditulis kembali ke ThingSpeak.
- Mendukung lima profil/channel ThingSpeak dengan Read API Key opsional.

## Cara membangun APK lewat GitHub Actions

1. Unggah seluruh isi folder `AGRO-IoT-Android` ke direktori utama repository GitHub (termasuk folder `.github` dan `app`).
2. Buka tab **Actions**.
3. Jalankan workflow **Build AGRO-IoT APK** atau lakukan push ke branch `main`.
4. Jika build berhasil, unduh artefak **`agro-iot-v3.1.1-apk`** dari run tersebut. APK berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan ekspor CSV

Tanggal awal dipakai mulai pukul 00:00:00 dan tanggal akhir hingga pukul 23:59:59 berdasarkan pilihan kalender. Ekspor mengambil data asli dari ThingSpeak; nilai manual yang hanya tampil di dashboard tidak mengubah histori CSV di server.

## Pembaruan v3.1.1

- VPD: ikon tetesan air + termometer, satuan `kPa`.
- Lama penyinaran matahari: ikon matahari + jam, satuan `jam`.
- Ikon berpasangan menggunakan ukuran lebih kecil agar muat dalam kartu dashboard.
