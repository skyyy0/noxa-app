# NOXA — Konfigurasi Environment

NOXA membaca konfigurasi dari file `.env` di root proyek melalui
**Secrets Gradle Plugin** (`app/build.gradle.kts` → blok `secrets { ... }`),
lalu mengeksposnya sebagai field `BuildConfig`.

`.env` **tidak boleh di-commit** (sudah ada di `.gitignore`).

## Variabel yang dibutuhkan

| Variabel | Wajib | Keterangan |
| --- | --- | --- |
| `YOUTUBE_API_KEYS` | Ya | Satu atau beberapa YouTube Data API v3 key, dipisahkan koma. Dipakai untuk pencarian & trending. Key dirotasi otomatis bila kena kuota (HTTP 403/429). |
| `STREAM_RESOLVER_URL` | Untuk audio lagu YouTube | Endpoint resolver milik Anda yang mengembalikan URL audio langsung untuk sebuah videoId. |

Contoh isi `.env`:

```dotenv
YOUTUBE_API_KEYS=KEY_1,KEY_2,KEY_3,KEY_4
STREAM_RESOLVER_URL=https://backend-anda.example/api/stream
```

## Catatan penting soal audio

YouTube Data API v3 adalah **API metadata**: ia mengembalikan judul, artis,
thumbnail, dan durasi, tetapi **tidak pernah** mengembalikan URL audio. Karena
itu:

- `Song.streamUrl` sengaja dibiarkan **kosong** untuk hasil dari YouTube.
  Aplikasi tidak lagi mengarang URL audio.
- Pemutaran audio asli memakai salah satu dari dua jalur berikut:
  1. **`STREAM_RESOLVER_URL`** → resolver Anda mengembalikan URL audio langsung
     (format respons: `{"streamUrl":"https://..."}` atau `{"url":"https://..."}`).
     Jalur ini memakai engine Media3/ExoPlayer sehingga notifikasi media,
     lock screen, dan background playback aktif.
  2. **Video YouTube** → dimainkan lewat engine YouTube IFrame (audio asli dari
     video terkait). Jalur ini **tidak** dapat mengekspos MediaSession, jadi
     notifikasi/lock screen sistem tidak muncul untuk lagu YouTube.

Resolver menerima permintaan berikut:

```
GET <STREAM_RESOLVER_URL>?videoId=<videoId>
Accept: application/json
```

## Membaca konfigurasi dengan aman

`com.example.data.remote.Secrets` membaca field `BuildConfig` secara reflektif.
Dengan begitu, bila `.env` tidak ada (mis. clone baru) proyek tetap dapat
di-build, dan aplikasi menampilkan status "belum dikonfigurasi" alih-alih gagal
kompilasi.

> Jika Anda membangun lewat Google AI Studio, tambahkan kedua variabel di atas
> pada panel **Secrets** dengan nama yang sama.
