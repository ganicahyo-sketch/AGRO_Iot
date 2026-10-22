# AGRO-IoT Android v3.1.4 — perbaikan statistik harian

## Yang diperbaiki
- Statistik mengambil hingga 8.000 feed terakhir dari channel ThingSpeak, lalu menyaring rekaman hari berjalan berdasarkan zona `Asia/Jakarta`. Ini menghindari kegagalan query statistik akibat parameter rentang waktu.
- Parameter `offset` tidak lagi dipakai sebagai pagination karena pada API ThingSpeak parameter tersebut berfungsi sebagai offset zona waktu, bukan nomor halaman.
- Pembacaan `created_at` menerima format ISO 8601 bertanda zona waktu maupun format `yyyy-MM-dd HH:mm:ss`.
- Nilai min, maks, dan rerata dihitung per field hanya dari nilai numerik valid di tanggal WIB berjalan.
- Cache statistik versi lama otomatis dianggap kedaluwarsa agar statistik dihitung ulang setelah pembaruan aplikasi.
- Error permintaan statistik disimpan lokal untuk diagnosis dan tidak menghentikan pembaruan realtime.

## Build
Gunakan GitHub Actions workflow `Build AGRO-IoT APK`. Artefak debug bernama `agro-iot-v3.1.4-apk`.
