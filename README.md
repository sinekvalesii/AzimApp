# AZİM 3.0 · Sağlık ve Alışkanlık (Android)

Yapım: **selcukyus** · Paket: `com.selcuk.azim` · Kotlin, Jetpack Compose, MVVM, Room, Firebase Authentication, Health Connect, Play Billing

## Android Studio'da açma
1. **File > Open** ile bu klasörü aç, Gradle senkronizasyonunu bekle.
2. ▶ **Run**. Proje Firebase dosyası olmadan da derlenir ve **misafir moduyla** çalışır.

## Firebase girişini açma (ücretsiz Spark planı)
1. https://console.firebase.google.com > proje oluştur > Android uygulaması ekle, paket adı `com.selcuk.azim`.
2. İndirdiğin `google-services.json` dosyasını `app/` klasörüne koy. Bu dosya `.gitignore` içinde; repoya yüklenmez.
3. Firebase Console > Authentication > Sign-in method > **E-posta/Şifre**'yi etkinleştir.
4. Yeniden derle. Giriş, kayıt, şifre sıfırlama ve hesap silme ekranları kendiliğinden açılır.

## Mimari
| Katman | Dosya |
|---|---|
| Veri (Room) | `data/Db.kt`: Quest, DayLog, HealthDay tabloları · `data/Repo.kt`: depo + `Difficulty` algoritması |
| Sağlık | `health/HealthRepo.kt`: Health Connect'ten adım ve nabız okur (salt okunur) |
| Kimlik | `auth/AuthRepo.kt`: Firebase Authentication + misafir modu |
| Abonelik | `billing/Billing.kt`: Google Play abonelikleri |
| ViewModel | `ui/MainViewModel.kt`: tek `UiState` akışı |
| Arayüz | `ui/MainActivity.kt`: Compose (Bugün, Sağlık, Profil) · `ui/Reminder.kt`: akşam hatırlatıcısı |

**Zorluk algoritması:** Son 7 günde tamamlama oranı %85 ve üzeriyse ve son 3 gün üst üste tamamlandıysa hedef yaklaşık %10 artar; oran %40 ve altındaysa yaklaşık %15 azalır. Hedef, değiştikten sonra en az 3 gün sabit kalır. Ortalama nabız kişisel ortalamanın %8 üzerindeyse artış ertelenir.

## Abonelik (AZİM Plus)
Ücretsiz: tüm görevler, uyarlanabilir zorluk, seri, seviye, günlük adım ve nabız. Plus: 7 günlük grafik ve sağlık-başarı analizi.
Play Console > Para kazanma > Abonelikler bölümünde iki ürün oluştur (kimlikler birebir aynı olmalı):
- `azim_plus_monthly` (öneri: ₺19,99/ay)
- `azim_plus_yearly` (öneri: ₺149,99/yıl)

Fiyatı Play Console'dan istediğin zaman değiştirebilirsin; uygulama fiyatı oradan okur. Sunucu gerekmez.

## Maliyet
Firebase Authentication (Spark planı), Health Connect, GitHub Pages ve GitHub Actions ücretsiz. Tek zorunlu ödeme Google Play geliştirici hesabı (25 $, tek sefer). Abonelik satışlarından Google komisyon keser.

## Play Console'da dikkat edilecekler
- **Sağlık uygulamaları beyanı** ve **Health Connect izin beyanı** formlarını doldur: READ_STEPS ve READ_HEART_RATE; amaç "fitness ve sağlıklı yaşam".
- **Veri güvenliği formu:** e-posta adresi (hesap yönetimi, Firebase) toplanıyor; sağlık verisi cihazda işleniyor. Formu Google'ın güncel rehberine göre doldur.
- **Gizlilik politikası ve hesap silme adresi:** https://sinekvalesii.github.io/AzimApp/privacy.html
- İmzalı AAB: **Build > Generate Signed App Bundle**. Ayrıntı için `keystore.properties.example` dosyasına bak.
- `docs/` klasöründeki web sayfası eski 2.0 web sürümüdür; native uygulamadan bağımsızdır.
