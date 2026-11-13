# ScanPDF – Aplikasi Scan Dokumen ke PDF (Android)

Aplikasi pemindai dokumen seperti CamScanner. Foto dokumen langsung jadi **PDF**.

## Fitur
- Kamera scan dengan **deteksi tepi otomatis** dan **crop / luruskan perspektif**
- **Filter** (hitam-putih, grayscale, warna otomatis), **putar**, hapus bayangan/noda
- **Banyak halaman** dalam satu PDF (hingga 100 halaman)
- **Impor dari galeri**
- Hasil langsung disimpan sebagai PDF, juga otomatis disalin ke **Download/ScanPDF** (Android 10+)
- Daftar dokumen dengan thumbnail, jumlah halaman, tanggal, ukuran
- **Penampil PDF bawaan**, plus Bagikan (WhatsApp, email, Drive), Ganti nama, Hapus

Mesin scan memakai Google ML Kit Document Scanner (gratis, berjalan di perangkat).
Syarat ponsel: Android 8.0+, ada Google Play Services, RAM ≥ 1,7 GB.
Saat pertama kali menekan **Scan**, ponsel perlu internet sebentar untuk mengunduh modul scanner.

---

## Cara mendapatkan file APK

### Opsi A – Lewat GitHub (gratis, tanpa install apa pun)
1. Buat akun di https://github.com lalu buat repository baru (mis. `ScanPDF`).
2. Klik **"uploading an existing file"**, lalu seret **seluruh isi** folder ini
   (termasuk folder `.github`, `app`, `gradle`, dan file `gradlew`). Klik **Commit changes**.
   > Folder `.github` tersembunyi di beberapa komputer. Pastikan ikut terunggah,
   > atau buat manual file `.github/workflows/build-apk.yml` lewat **Add file → Create new file**.
3. Buka tab **Actions**. Proses "Build APK" berjalan otomatis (±5–8 menit).
4. Setelah centang hijau, klik hasil build → bagian **Artifacts** → unduh **ScanPDF-apk**.
5. Ekstrak zip-nya, kirim `ScanPDF.apk` ke ponsel, lalu instal
   (izinkan **"Instal dari sumber tidak dikenal"** jika diminta).

### Opsi B – Android Studio
1. Buka folder ini di Android Studio (File → Open).
2. Tunggu sinkronisasi Gradle selesai.
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. APK ada di `app/build/outputs/apk/`.

Atau lewat terminal: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

> Catatan: APK ditandatangani dengan debug key agar bisa langsung diinstal.
> Untuk rilis ke Play Store, buat keystore sendiri.
