# Pure Status — RAIS

Aplikasi seperti PureStatus, dibuat dari nol oleh RAIS.PROJECT — bukan salinan APK PureStatus.
Video/foto disiapkan 1080p dulu supaya Status WhatsApp / IG / FB tidak blur.

- Package: `com.raisproject.purestatus`, v1.0.0
- Output video: 1080x1920, 30fps, H.264 + AAC, tanpa watermark, tanpa iklan
- Output foto: 1080x1920 JPEG q95, plus opsi foto jadi video 5 detik
- Splitter: WA Status 60 detik/part, IG Story 60 detik/part, FB Story 20 detik/part, Reels 90 detik/part
- Share langsung ke WhatsApp / Instagram / Facebook
- Engine: Media3 Transformer 1.11.1 (on-device, hardware encoder)
- Build: GitHub Actions, fixed debug keystore di `.github/debug.keystore`
  (cert SHA-256 `3EA43D46381133B858642DE4E4793367A4C87CB7CC92427BA3F40FFD80015F55`)

## Build
Actions > Build Debug APK > Run workflow, lalu download artifact PureStatus-debug.
