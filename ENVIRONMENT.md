# NOXA — Konfigurasi Environment

NOXA membaca konfigurasi dari file `.env` di root proyek melalui
**Secrets Gradle Plugin** (`app/build.gradle.kts` → blok `secrets { ... }`),
lalu mengeksposnya sebagai field `BuildConfig` (dibaca lewat
`com.example.data.remote.Secrets`).

`.env` **tidak boleh di-commit** (sudah ada di `.gitignore`).

## Sumber musik (v1.8)

NOXA 1.8 memakai empat layanan publik. Semuanya **opsional** — aplikasi tetap
berfungsi penuh pada instalasi baru tanpa konfigurasi apa pun:

| Layanan | Dipakai untuk | Audio | Perlu key? |
| --- | --- | --- | --- |
| **Deezer** (`api.deezer.com`) | Katalog utama, termasuk musik Indonesia (Dewa 19, Sheila On 7, Iwan Fals, Tulus, NOAH, dll) dan chart global | Klip **preview 30 detik** resmi dari label (batas legal Deezer) | Tidak |
| **Audius** (`api.audius.co`) | Lagu penuh dari artis independen | **Lagu penuh** | Tidak |
| **YouTube** (search: YouTube Data API v3 opsional + instance Piped/Invidious; audio & MV: resolusi via instance Piped/Invidious saat play) | Katalog mainstream terluas | **Lagu penuh** (via instance) | Tidak |
| **LRCLIB** (`lrclib.net`) | Lirik sinkron (berpenanda waktu `[mm:ss.xx]`) | — | Tidak |

Di UI, lagu Deezer ditandai **`PREVIEW 30s`**, lagu Audius **`FULL TRACK`**,
dan lagu YouTube **`YOUTUBE • FULL`**. Pencarian menyatukan ketiganya, dengan
chip filter **Semua / YouTube / Deezer / Audius** di layar Search.

### Cara kerja sumber YouTube (v1.8)

Bagian yang tidak bisa dilakukan API resmi Google adalah **audio**: YouTube Data
API v3 hanya metadata dan tidak pernah mengembalikan URL audio. Karena itu NOXA
memakai arsitektur berikut, tanpa server apa pun:

1. **Pencarian** — YouTube Data API v3 bila `YOUTUBE_API_KEY` diisi (100 unit
   kuota Google per pencarian), plus selalu instance publik **Piped**
   (`/search?filter=videos`) dan **Invidious** (`/api/v1/search`) yang tidak
   butuh kunci. Hasil digabung & dide-duplikasi per video id.
2. **Audio saat play** — URL audio diresolusi lewat instance **Piped**
   (diprioritaskan; URL-nya di-proxy instance sehingga stabil) dan jatuh balik
   ke **Invidious**. Bila semua instance gagal, aplikasi menampilkan pesan
   error yang jujur — tidak pernah memutar rekayasa.
3. **Video MV** — pemutar penuh punya toggle **Foto / Video MV**. Video MV
   diputar di pemutar terpisah yang **selalu senyap**, jadi audio utama,
   equalizer, dan kontrol notifikasi tidak pernah terganggu. Kualitas video
   diarahkan ke ~360p (hemat kuota).
4. **Kebijakan penggunaan** — sebelum pemutaran YouTube pertama, dialog
   kebijakan ditampilkan sekali (disimpan di SharedPreferences).
5. **URL kedaluwarsa** — URL stream instance hanya hidup beberapa jam; NOXA
   meng-cache in-memory 4 jam dan menresolvi ulang saat dibutuhkan.

Instance publik bisa mati kapan saja. Daftar default bawaan aplikasi bisa
dioverride lewat `PIPED_INSTANCE` / `INVIDIOUS_INSTANCE` (pisahkan beberapa
dengan koma). `STREAM_RESOLVER_URL` (backend milik Anda, mis. yt-dlp sendiri)
tetap **didahulukan** untuk semua lagu bila diisi.

Catatan jujur: proyek ekstraksi audio YouTube (yt-dlp dsb.) adalah perangkat
lunak yang berubah hampir tiap minggu (signature cipher, PO Token) dan umumnya
berjalan di server Python — itulah sebabnya NOXA memakai instance Piped/
Invidious yang sudah menjalankannya, bukan mengimplementasikannya di dalam APK.
Hak cipta lagu tetap milik pencipta/label; kebijakan penggunaan ditampilkan ke
pengguna sebelum pemutaran pertama.

### Profil artis, album & audio utuh (v1.9)

* **Profil artis** — mengetuk nama artis (di daftar lagu mana pun atau di
  pemutar penuh) membuka halaman artis: foto, nama, centang verifikasi,
  jumlah pendengar, lagu teratas, dan rak album. Mengetuk album membuka
  **halaman album** berisi daftar lengkap lagu album itu. Sumber data:
  endpoint publik `artist/{id}`, `artist/{id}/albums`, `album/{id}` dan
  `album/{id}/tracks` dari Deezer (tanpa kunci).
* **Centang verifikasi** — **biru** untuk artis terverifikasi biasa,
  **merah** untuk artis besar dengan ≥ 1.000.000 pendengar (mis. Justin
  Bieber). Ambangnya berbasis data `nb_fan` dari Deezer, bukan daftar
  hardcoded. Jumlah pendengar juga ditampilkan apa adanya dari `nb_fan`.
* **Audio utuh untuk lagu preview** — saat play ditekan pada lagu ber-`PREVIEW 30s`
  dan kebijakan penggunaan sudah diterima, NOXA mencocokkan lagu itu ke
  video YouTube (pencarian judul + artis + durasi lewat instance; skor murni
  yang menolak karaoke, playlist, kompilasi, dan terlalu jauh durasinya),
  lalu menresolvi audionya lewat rantai Piped/Invidious yang sama dengan
  sumber YouTube. Contoh: Dewa 19 — Kangen diputar **utuh**, bukan 30 detik.
  Bila tidak ada kecocakan meyakinkan, aplikasi jujur memutar preview 30
  detik (badge tetap tampil); audio yang salah tidak pernah diputar diam-diam.
  Pengguna yang menolak kebijakan tidak ditanya lagi dan lagu tetap preview.
  `STREAM_RESOLVER_URL`, bila diisi, tetap didahulukan untuk semua lagu.

### v1.9.1 — audio utuh tanpa instance & perbaikan UI

* **NewPipeExtractor (ekstraksi di perangkat)** — library open-source (GPL-3)
  yang mengekstrak URL audio YouTube **langsung dari HP**, tanpa server
  Piped/Invidious. Urutan resolusi audio kini: `STREAM_RESOLVER_URL` →
  ekstraktor perangkat → Piped → Invidious. Pencarian YouTube juga punya
  jaring pengaman ekstraktor perangkat saat semua instance pencarian mati.
* **Semua lagu utuh, tanpa dialog** — kebijakan penggunaan kini diterima
  bawaan (keputusan pemilik: distribusi privat, tidak masuk Play Store).
  Lagu Deezer diputar **utuh**; badge menampilkan sumber ("DEEZER"), bukan
  "PREVIEW 30s". Bila kecocokan benar-benar gagal, lagu tetap diputar
  apa adanya — tidak pernah memutar audio yang salah.
* **Perbaikan UI** — halaman artis/album tidak lagi tertutup status bar
  (inset otomatis semua perangkat); navigasi bawah di-hoist agar selalu
  aktif dari setiap halaman.
* **Catatan teknis** — seluruh jaringan resolusi dipindah ke thread I/O;
  sebelumnya sebagian berjalan di main thread sehingga sentuhan bisa
  membeku seketika dan fallback gagal diam-diam ke preview 30 detik.

### v1.10 — Pin by Sky, akun email + username unik, pemutar lebih hidup

* **Kata Sky (pinned)** — "ada lagu yang cuma berdurasi beberapa menit, tapi
  bisa membawa kita kembali ke masa yang udah ga mungkin diulang." Lagu pin
  (Dewa 19 — Kangen) mendapat **rak khusus `📌 Pinned by Sky`** di Home lengkap
  dengan quote-nya, badge `⭐ Pinned` pada kartunya, bintang di pemutar penuh &
  antrian, serta blok quote "PINNED BY SKY" di bawah kontrol pemutar penuh.
* **Username unik (Firestore)** — pendaftaran email/password mendaftarkan
  username unik lewat Firestore: nama diklaim sebagai document id
  `usernames/{nama-huruf-kecil}` **di dalam transaksi**, jadi dua orang tidak
  bisa memilih nama yang sama. Masuk/daftar dengan email+password tanpa Google.
  **API key tambahan tidak diperlukan** — SDK Firestore pakai konfigurasi
  Firebase project yang sama dengan Auth. Bila Firestore belum di-setup atau
  offline, pendaftaran tetap jalan (username disimpan sebagai display name)
  dan aplikasi tidak pernah crash.
* **Lirik karaoke** — di pemutar penuh, baris lirik sinkron tampil di atas
  artwork (baris sekarang warna lime, baris berikutnya redup di bawahnya).
  Mengikuti posisi playback sungguhan; bisa dimatikan di Pengaturan.
* **Autoplay lagu serupa** — ~15 detik sebelum lagu terakhir sebuah antrian
  habis, sampai 5 lagu teratas artis yang sama ditambahkan ke antrian. Audio
  sudah diresolusi dulu, jadi yang masuk antrian benar-benar bisa diputar.
* **Rekap mingguan mini** — kartu "Rekap Minggu Ini" di Profile: jumlah lagu
  yang diputar 7 hari terakhir, artis teratas, dan lagu paling sering. Data dari
  tabel Room baru `play_records` (migrasi `MIGRATION_2_3` — favorit, history dan
  playlist lama tetap aman; record lebih tua dari 30 hari dibersihkan otomatis).
* **Pengaturan pemutar (baru)** — kualitas video MV (360p / 480p / 720p, pilihan
  mengikuti stream terdekat dan di-cache per kualitas), toggle **Autoplay lagu
  serupa**, toggle **Lirik karaoke**, dan toggle **Mulai otomatis saat dibuka**
  (melanjutkan sesi terakhir begitu aplikasi dibuka).
* **MV untuk lagu yang di-match** — video MV kini juga tersedia untuk lagu
  Deezer yang audionya sudah cocok ke video YouTube (`Song.matchedVideoId`);
  kualitasnya mengikuti pilihan di Pengaturan. Toggle MV kini rail vertikal di
  samping artwork (Foto / MV).
* **5 lagu pin Sky** — Kangen (Dewa 19), Pupus (Dewa 19), Dan (Sheila On 7),
  Terlalu Manis (Slank), dan Laskar Pelangi (Nidji), masing-masing dengan
  kata-kata Sky sendiri yang tampil di rak pin, di kartu, dan di pemutar penuh.

### v1.11 — kelima pin tampil, ikon vektor, akun tanpa Firebase, browse bergambar

* **Semua 5 lagu pin kembali tampil** — pencarian pin kini menemukan lagu lewat
  *top tracks* artis (kemudian fallback search), jadi "Dan" (Sheila On 7) dan
  "Laskar Pelangi" (Nidji) tidak lagi tenggelam di hasil pencarian umum.
  Quote kini menempel pada semua lagu pin berkat satu aturan matching yang
  dipakai bersama (judul dengan akhiran remaster/live/bracket tetap cocok).
* **Ikon vektor, bukan emoji** — rak pin, badge `Pinned`, bintang di pemutar
  penuh & antrian, ikon rekap mingguan, dan badge pada kartu share kini
  memakai ikon Material (PushPin/Star) dan star path hasil gambar sendiri,
  bukan glyph emoji.
* **Akun selalu bisa dibuat (tanpa keys)** — build rilis GitHub tidak
  membawa `google-services.json`, sehingga Firebase Auth/Firestore tidak
  tersedia dan pendaftaran gagal dengan pesan "belum dikonfigurasi". Kini
  ada **akun bawaan aplikasi**: email + password (hash PBKDF2-HMAC-SHA256,
  tidak ada password tersimpan), username unik **per perangkat**, sesi
  bertahan setelah app ditutup. Firebase tetap dipakai otomatis pada build
  yang membawa konfigurasinya (lengkap dengan registry Firestore).
* **Animasi ala iOS** — kartu rak pin & shelf masuk bertahap (fade + naik
  lembut), tombol play pemutar penuh & mini player memantul lembut saat
  ditekan, ikon play/pause pop saat berganti, mini player meluncur dengan
  spring.
* **Bagikan gambar diperbaiki** — URI kartu share kini ikut ditaruh di
  `ClipData` (pola resmi Android untuk meneruskan izin baca URI ke aplikasi
  tujuan); banyak share target/OEM mengabaikan `EXTRA_STREAM` semata, yang
  dulu bikin "Bagikan sebagai gambar" gagal. Setiap kegagalan kini juga
  tercatat di logcat (`ShareCard`) supaya mudah didiagnosis.
* **Explore Genres bergambar** — kartu browse di Search kini memakai foto
  artis asli dari Deezer + cover Global Top 50 dengan scrim gradasi gelap
  ala Spotify; kartu yang belum punya gambar tetap memakai warna temanya.
* **Package baru `com.noxa.music`** — embel-embel `aistudio` dilepas dari
  `applicationId`. Karena identitas instalasi berubah, APK baru terpasang
  sebagai app baru (uninstall app lama lebih dulu); data lokal (akun, Riwayat
  & Room) tidak ikut pindah.
* **MV lebih tangguh** — resolusi stream kini mencoba kualitas pilihan, lalu
  jatuh ke 360p dan 240p; kalau semua gagal dicoba ulang sekali setelah jeda.
  Bila stream keburu expired di tengah pemutaran, pemutar video memicu
  resolusi ulang otomatis (satu kali) alih-alih meninggalkan layar hitam.
* **Tiga fitur baru** — shelf **Dengar Terakhir** di Home (loncat cepat ke
  lagu yang baru didengar), tombol **Mix Sky** (antrian acak dari lagu pin +
  favorit + riwayat), dan **long-press pada baris lagu** mana pun untuk
  membuka menu cepat (antrian, playlist, bagikan) tanpa mencari tombol ⋮.

### Berbagi lagu: link NOXA & kartu canvas (v1.10)

Sebelumnya membagikan lagu mengirim link Deezer (`deezer.com/track/…`). Sejak
v1.10 tombol bagikan memakai sheet NOXA sendiri dengan dua pilihan:

1. **Bagikan sebagai gambar** — kartu canvas ala Spotify (artwork membulat,
   judul, artis, badge ⭐ untuk lagu pin, branding NOXA + skyvibes.my.id)
   dirender sebagai gambar 1080×1080, **otomatis tersimpan ke galeri**
   (`Pictures/NOXA`, tanpa izin di Android 10+), lalu dikirim lewat share
   sheet sistem (WhatsApp dll) memakai FileProvider.
2. **Salin link NOXA** — menyalin deep link `https://skyvibes.my.id/track/<id>`
   (contoh: `…/track/deezer%3A76056788`).

Link tersebut dibuka **langsung di pemutar NOXA** lewat intent-filter
(`https://skyvibes.my.id/track/*` dan skema cadangan `noxa://play/<id>`,
`launchMode=singleTask` supaya app yang sedang terbuka tidak dibuat ulang).
Lagu di-resolve ulang berdasarkan id: `deezer:<id>` lewat `GET /track/{id}`,
`audius:<id>` lewat `GET /v1/tracks/{id}`, dan `youtube:<id>` lewat ekstraktor
di perangkat (judul/artis ikut terbawa).

> Catatan Android: untuk https tanpa verifikasi App Links, Android menampilkan
> pilihan aplikasi (chooser) saat link diketuk — pilih NOXA dan ingat pilihan
> itu. Agar tanpa chooser, host `assetlinks.json` di `skyvibes.my.id`
> (package `com.noxa.music`) sesuai docs Android App Links.
> Pesan teks share tetap memakai format `🎵 Judul — Artis` + link +
> `Didengarkan lewat NOXA Music`.

Aturan Firestore yang disarankan untuk registry username (console → Firestore
→ Rules) — pembuatan nama hanya untuk pemiliknya, nama yang sudah diklaim tidak
bisa diambil alih:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /usernames/{name} {
      allow read: if true;
      allow create: if request.auth != null &&
        request.resource.data.uid == request.auth.uid;
      allow update, delete: if request.auth != null &&
        resource.data.uid == request.auth.uid;
    }
  }
}
```

> Catatan jujur: Firestore menjamin uniqueness lewat document id dalam
> transaksi, sehingga registry tetap benar bahkan dengan rules test-mode;
> rules di atas hanya mencegah orang lain mengubah/menghapus nama yang sudah
> diklaim. Tanpa `google-services.json`, email/password juga tidak tersedia
> (build tetap jalan sebagai mode tamu).

## Variabel `.env` (semuanya opsional)

| Variabel | Wajib | Keterangan |
| --- | --- | --- |
| `AUDIUS_API_KEY` | Tidak | Kunci developer Audius. Tanpa ini pun Audius tetap bisa dibaca (anonim). Bila diisi, dikirim sebagai header `x-api-key`. |
| `STREAM_RESOLVER_URL` | Tidak | Endpoint milik Anda sendiri. Bila diisi, setiap lagu yang mau diputar ditawarkan ke resolver ini **didahulukan** untuk mendapatkan URL audio **utuh** (lihat di bawah). |
| `YOUTUBE_API_KEY` | Tidak | Kunci YouTube Data API v3 untuk pencarian (kuota 100 unit/pencarian). Tanpa ini pencarian YouTube tetap jalan lewat instance Piped/Invidious. |
| `PIPED_INSTANCE` | Tidak | Override instance Piped (dipisah koma). Bila kosong, memakai daftar default bawaan aplikasi. |
| `INVIDIOUS_INSTANCE` | Tidak | Override instance Invidious (dipisah koma). Bila kosong, memakai daftar default bawaan aplikasi. |

Contoh isi `.env`:

```dotenv
AUDIUS_API_KEY=
STREAM_RESOLVER_URL=https://backend-anda.example/api/resolve
YOUTUBE_API_KEY=
PIPED_INSTANCE=
INVIDIOUS_INSTANCE=
```

> Catatan: nama variabel baru di atas ditambahkan ke `.env.example` secara
> manual bila dibutuhkan; Secrets Plugin membaca `.env` Anda, dan bila sebuah
> nama tidak ada di BuildConfig aplikasi tetap aman (kembali ke string kosong).

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

> Tanggung jawab Anda: apa pun yang Anda taruh di balik endpoint itu. Bila
> `STREAM_RESOLVER_URL` kosong, lagu YouTube ditandai dan dibatalkan (dengan
> pesan error yang jujur); lagu Deezer/Audius tetap diputar dari sumber
> resminya.

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
```Yang dibangun secara default adalah **APK release yang sudah ditandatangani**
dengan keystore repositori (`my-upload-key.jks`, ter-commit di repo privat ini),
sehingga bisa langsung dipasang tanpa setelan apa pun. `codemagic.yaml` tetap
disimpan, tetapi sudah tidak diperlukan.

### Update di tempat & Play Protect (v1.8+)

Mulai v1.8, **setiap** build CI memakai SATU keystore yang sama. Akibatnya:

* APK baru terpasang **menimpa** versi lama — tidak perlu hapus aplikasi
  lama lagi (data, favorit, playlist ikut terjaga).
* **Transisi sekali saja:** APK ≤1.7 dipasang dengan kunci debug sementara CI,
  jadi saat pertama kali naik ke v1.8 Anda masih harus menghapus APK lama
  satu kali. Setelah itu tidak lagi.
* Play Protect memblokir/memperingatkan APK debug-signed. APK release bertanda
  tangan keystore tetap, non-debuggable, jauh lebih jarang diperingatkan.
  (Aplikasi sideload tetap akan *dipindai* Play Protect — itu perilaku normal;
  yang hilang adalah flag utama "app debug".)

Jangan commit keystore lain atau mengganti `my-upload-key.jks`: fingerprint
aplikasi harus tetap sama agar update di tempat terus berfungsi.

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
yang sudah ditandatangani dengan keystore tersebut. Tanpa secret pun APK release
selalu dibangun memakai keystore repositori. Perlu diingat: keystore yang
berbeda = aplikasi yang tidak bisa saling menimpa — kalau Anda pernah memakai
`KEYSTORE_BASE64`, terus pakai keystore yang sama untuk update berikutnya.

> Repositori ini privat, sehingga halaman Release hanya bisa dibuka setelah Anda
> masuk ke GitHub dengan akun yang punya akses ke repositori.

## Catatan tentang kontrol di notifikasi

Kontrol pemutar di notifikasi / lock screen berasal dari **MediaSession** milik
Media3. Agar muncul, aplikasi butuh izin notifikasi (`POST_NOTIFICATIONS`, diminta
saat pertama dibuka). Bila izin itu ditolak, suara tetap jalan tetapi kontrol
tidak akan tampil sampai izin diberikan di Pengaturan Android.

Sejak v1.8 tombol **acak (shuffle)** dan **ulang (repeat)** di notifikasi memakai
custom session command — satu-satunya mekanisme yang benar-benar dirender
`DefaultMediaNotificationProvider` (tombol berbasis player command disaring keluar
oleh Media3). Ikonnya mengikuti state: shuffle-on, repeat-all, repeat-one. Progress
bar & tombol previous/play/next berasal dari pemutar itu sendiri.
