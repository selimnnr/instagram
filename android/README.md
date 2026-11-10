# Instagram Unfollowers — Android APK

Bu klasör, `cobanov/instagram` (`selimnnr/instagram`) projesinin **Android APK** uygulamasını içerir.

## Özellikler

1. **Canlı Panel (`Instagram` Sekmesi)**
   - Uygulama içindeki WebView üzerinden doğrudan `https://www.instagram.com/` hesabınıza giriş yapabilirsiniz.
   - Üst bardaki **`✦ Paneli Aç`** butonuna dokunduğunuzda `dist/instagram-unfollower.one-line.js` betiği aktif sekmeye enjekte edilir.
   - Tarama veya takipten çıkma işlemi sırasında telefon ekranının kapanıp zamanlayıcıları durdurmaması için ekran açık tutulur (`KEEP_SCREEN_ON`).
   - Sonuç listesindeki herhangi bir kullanıcı adına (`@kullanici`) dokunduğunuzda, ana tarama paneliniz kapanmadan **modal profil önizleme penceresi** açılır.

2. **Çevrimdışı ZIP / JSON Veri Analizi (`ZIP Analiz` Sekmesi)**
   - Instagram'ın "Bilgilerini İndir" (`Download your information`) sayfasından aldığınız `.zip`, `.json` veya `.html` dosyalarını telefonunuzdan seçerek **Instagram'a hiçbir istek göndermeden** geri takip etmeyenleri bulabilirsiniz.
   - Analiz tamamlandığında **`Panelle takipten çık`** butonuna dokunarak bulunan kullanıcı listesini tek dokunuşla canlı panele aktarabilir ve güvenli hızda takipten çıkabilirsiniz.

## Derleme (Build)

Önce kök dizinde web varlıklarını güncelleyin:

```sh
npm run build
```

Ardından Android projesini Gradle ile derleyin:

```sh
cd android
gradle assembleRelease
```

İmzalanmış APK dosyası şu dizinde oluşturulur:
`android/app/build/outputs/apk/release/app-release.apk`
