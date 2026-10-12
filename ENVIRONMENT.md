# NOXA — Konfigurasi Environment

NOXA membaca konfigurasi dari file `.env` di root proyek melalui
**Secrets Gradle Plugin** (`app/build.gradle.kts` → blok `secrets { ... }`),
lalu mengeksposnya sebagai field `BuildConfig` (dibaca lewat
`com.example.data.remote.Secrets`).

`.env` **tidak boleh di-commit** (sudah ada di `.gitignore`).

## Sumber musik: tidak butuh API key sama sekali

Versi 1.6 memakai tiga layanan publik yang **tidak memerlukan API key, akun,
atau pendaftaran apa pun**, jadi aplikasi langsung berfungsi pada instalasi baru:

| Layanan | Dipakai untuk | Audio | Perlu key? |
| --- | --- | --- | --- |
| **Deezer** (`api.deezer.com`) | Katalog utama, termasuk musik Indonesia (Dewa 19, Sheila On 7, Iwan Fals, Tulus, NOAH, dll) dan chart global | Klip **preview 30 detik** resmi dari label (batas legal Deezer) | Tidak |
| **Audius** (`api.audius.co`) | Lagu penuh dari artis independen | **Lagu penuh** | Tidak |
| **LRCLIB** (`lrclib.net`) | Lirik sinkron (berpenanda waktu `[mm:ss.xx]`) | — | Tidak |

Di UI, lagu Deezer ditandai **`PREVIEW 30s`** dan lagu Audius ditandai
**`FULL TRACK`**, supaya jelas kenapa sebagian lagu hanya 30 detik. Itu batas
legal Deezer, bukan bug.

### Mengapa YouTube API tidak dipakai untuk audio

YouTube Data API v3 adalah **API metadata**. Ia mengembalikan judul, artis,
thumbnail, dan durasi, tetapi **tidak pernah** mengembalikan URL audio. Jadi
`YOUTUBE_API_KEYS` tidak bisa membuat lagu bisa diputar — memakainya hanya
menambah data tanpa suara. Selain itu mengambil audio YouTube lewat scraping
melanggar Terms of Service dan cepat rusak. Karena itu kunci YouTube tidak
disertakan lagi sebagai kebutuhan.

Kalau Anda punya backend streaming berlisensi sendiri, kirim URL audio langsung
pada field `Song.streamUrl` — pemutar Media3 akan memakainya apa adanya.

## Variabel `.env` (semuanya opsional)

| Variabel | Wajib | Keterangan |
| --- | --- | --- |
| `AUDIUS_API_KEY` | Tidak | Kunci developer Audius. Tanpa ini pun Audius tetap bisa dibaca (anonim). Bila diisi, dikirim sebagai header `x-api-key`. |
| `STREAM_RESOLVER_URL` | Tidak | Endpoint milik Anda sendiri. Bila diisi, setiap lagu yang mau diputar ditawarkan ke resolver ini untuk mendapatkan URL audio **utuh** (lihat di bawah). |

Contoh isi `.env`:

```dotenv
AUDIUS_API_KEY=
STREAM_RESOLVER_URL=https://backend-anda.example/api/resolve
```

### Cara kerja `STREAM_RESOLVER_URL` (jalur lagu utuh)

Deezer hanya mengizinkan klip 30 detik, dan Audius tidak punya katalog
mainstream Indonesia. Satu-satunya jalur legal untuk memutar lagu **utuh** dari
katalog mainstream adalah backend milik Anda sendiri (mis. CDN berlisensi atau
layanan berlangganan Anda). NOXA tidak menyertakan scraper apa pun; ia hanya
menanyakan URL ke endpoint yang Anda tentukan.

Saat pengguna menekan play, NOXA memanggil:

```
GET <STREAM_RESOLVER_URL>?id=<song id>&source=<AUDIUS|DEEZER>&title=<judul>
                         &artist=<artis>&durationMs=<ms>&remoteId=<id provider>
Accept: application/json
```

Resolver menjawab salah satu dari ini:

```json
{ "streamUrl": "https://cdn.anda/lagu-utuh.mp3" }
```

```json
{ "url": "https://cdn.anda/lagu-utuh.mp3" }
```

Kalau resolver membalas 404, badan kosong, atau tanpa URL, lagu tetap diputar
dari sumber aslinya (preview Deezer / Audius). URL harus `https://` dan
langsung bisa diputar Media3 (mp3/aac/ogg; HLS `.m3u8` juga didukung ExoPlayer).

Karena pencarian menunggu jawaban resolver sebelum mulai memutar, buat resolver
Anda cepat (di bawah ~1 detik). Batas waktu di aplikasi: 6 detik koneksi,
10 detik baca.

> Tanggung jawab Anda: apa pun yang Anda taruh di balik endpoint itu. NOXA tidak
> mengambil audio dari YouTube/Deezer/Spotify secara langsung — itu melanggar
> Terms of Service mereka dan hak cipta, dan juga cepat rusak.

## Membaca konfigurasi dengan aman

`com.example.data.remote.Secrets` membaca field `BuildConfig` secara reflektif.
Dengan begitu, bila `.env` tidak ada (mis. clone baru) proyek tetap dapat
di-build, dan aplikasi menampilkan status "belum dikonfigurasi" alih-alih gagal
kompilasi.

## Mendapatkan file APK

APK dibangun otomatis oleh GitHub Actions pada setiap push ke `main`
(`.github/workflows/android-apk.yml`) dan diterbitkan sebagai **GitHub Release**,
sehingga selalu ada tautan unduh yang menetap:

```
https://github.com/skyyy0/noxa-app/releases/latest
```

Yang dibangun secara default adalah **APK debug** (sudah ditandatangani dengan
kunci debug Android), jadi bisa langsung dipasang di perangkat tanpa setelan apa
pun. `codemagic.yaml` tetap disimpan, tetapi sudah tidak diperlukan.

### APK release bertanda tangan sendiri (opsional)

Tambahkan secret berikut di **Settings -> Secrets and variables -> Actions**:

| Secret | Isi |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 my-upload-key.jks` |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | alias kunci |
| `KEY_PASSWORD` | password kunci |
| `GOOGLE_SERVICES_JSON_BASE64` | `base64 -w0 app/google-services.json` |

Selama `KEYSTORE_BASE64` ada, workflow juga membangun dan menerbitkan APK release
yang sudah ditandatangani. Perlu diingat: APK debug dan APK release memakai kunci
berbeda, jadi keduanya **tidak bisa** saling menimpa — pasang salah satu saja,
atau hapus pemasangan sebelumnya bila ingin berpindah.

> Repositori ini privat, sehingga halaman Release hanya bisa dibuka setelah Anda
> masuk ke GitHub dengan akun yang punya akses ke repositori.

## Catatan tentang kontrol di notifikasi

Kontrol pemutar di notifikasi / lock screen berasal dari **MediaSession** milik
Media3. Agar muncul, aplikasi butuh izin notifikasi (`POST_NOTIFICATIONS`, diminta
saat pertama dibuka). Bila izin itu ditolak, suara tetap jalan tetapi kontrol
tidak akan tampil sampai izin diberikan di Pengaturan Android.
