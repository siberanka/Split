# Split ── Bedrock/Java Dynamic Placeholder Plugin

A lightweight, high-performance, and secure Minecraft plugin designed for Spigot/Paper servers. It interfaces with the **Floodgate API** to serve different placeholder results depending on whether the player is using **Minecraft: Java Edition** or **Minecraft: Bedrock Edition** (via Geyser), alongside advanced switch-case logic and math/logical expression evaluations.

---

## 🇬🇧 English Documentation

### Features
* **Polymorphic Placeholders:** Supports `simple`, `switch`, and `expression` types for dynamic evaluation.
* **Dual-Platform Handling (`simple`):** Serves different template values for Bedrock and Java clients.
* **Case-Switch Mapping (`switch`):** Resolves a target placeholder and matches it against custom case keys with a fallback `default` case.
* **Boolean Expression Evaluator (`expression`):** Evaluates mathematical/relational expressions and returns a true or false value.
* **PlaceholderAPI Integration:** Registers custom `%split_<key>%` placeholders and resolves nested placeholders (e.g. `%player_name%`) in the returned values.
* **100% Thread-Safe & Atomic:** Custom configuration loading with atomic volatile swaps. Zero locks are held during placeholder requests.
* **Asynchronous Reloading:** Config reloads happen in a background thread, preventing server lag spikes (disk I/O) on the main thread.
* **Circular Reference Protection:** Safe evaluation using `ThreadLocal` recursion detectors. Prevents admin formatting mistakes from crashing the server with `StackOverflowError`.
* **Safe Custom Expression Parser:** Uses a built-in, lightweight, and 100% secure tokenizer. No scripting engine (like JavaScript Nashorn) is utilized, completely eliminating code-injection exploits.
* **Robust Configuration Reloading:** In the event of a YAML formatting syntax error, the plugin logs the details and maintains the current running configurations instead of crashing.

### Commands & Permissions
* `/split reload` ── Reloads the plugin configuration files (`config.yml`, `messages.yml`, `placeholders.yml`).
  * **Permission:** `split.admin`

### Configuration Files

#### `config.yml`
General settings.
```yaml
# Enable debug logging in the console
debug: false
# Fallback to Java values if a player is null/offline
default-to-java-on-null: true
```

#### `messages.yml`
Custom messages, supports legacy color codes (`&`) and modern Hex color codes (`&#ffffff`).
```yaml
prefix: "&8[&bSplit&8] &r"
no-permission: "%prefix%&cYou do not have permission to execute this command!"
only-players: "%prefix%&cThis command can only be executed by players!"
reload-success: "%prefix%&aConfiguration files reloaded successfully."
reload-failure: "%prefix%&cAn error occurred while reloading the configuration files! Check console."
invalid-usage: "%prefix%&cInvalid usage! &fUsage: /split reload"
```

#### `placeholders.yml`
Define your placeholders and their corresponding evaluation rules:
```yaml
# 1. Simple Platform Split Type
example:
  type: "simple"
  java: "Java"
  bedrock: "Bedrock"

# 2. Switch Case Type
example_switch:
  type: "switch"
  switch: "%luckperms_highest_group_by_weight%"
  case:
    "coal": "Coal"
    default: "Player"

# 3. Relational/Boolean Expression Type
# Supported operators: >, <, >=, <=, ==, !=, &&, ||, AND, OR.
# Alternative comparison symbols: >> (greater than), << (less than), <> (not equal).
example_expression:
  type: "expression"
  formule: "%player_ping% >> 60 && %player_ping% << 120"
  true: "Ping is stable"
  false: "Ping is not stable"
```

---

## 🇹🇷 Türkçe Dokümantasyon

### Özellikler
* **Polimorfik Placeholder'lar:** Dinamik çözümleme için `simple`, `switch` ve `expression` tiplerini destekler.
* **Platform Ayrımı (`simple`):** Bedrock ve Java istemcileri için farklı şablon çıktıları sağlar.
* **Eşleşme Eşitleme (`switch`):** Belirtilen hedef placeholder değerini çözümler ve tanımlı durumlarla (case) eşleştirir; eşleşme yoksa `default` değerini döndürür.
* **Mantıksal Karşılaştırma (`expression`):** Matematiksel/mantıksal formülleri çözümler ve sonucuna göre true veya false değerini döndürür.
* **PlaceholderAPI Entegrasyonu:** Özel `%split_<anahtar>%` placeholder'ları tanımlayabilir ve bunların içindeki diğer placeholder'ları (örn. `%player_name%`) otomatik olarak çözümler.
* **%100 Thread-Safe & Atomik:** Atomik geçişli ve uçucu (`volatile`) değişken yapılandırması sayesinde placeholder sorguları sırasında sunucu üzerinde sıfır kilitlenme (lock contention) oluşturur.
* **Asenkron Yenileme:** Yapılandırma yenileme işlemleri arka planda asenkron olarak gerçekleşir. Bu sayede sunucu ana iş parçacığında (main thread) disk okuma kaynaklı FPS/TPS düşüşleri yaşanmaz.
* **Kısır Döngü Koruması:** `ThreadLocal` tabanlı döngü algılayıcılar sayesinde yönetici hatalarından kaynaklanabilecek circular-reference (iç içe sonsuz döngü) durumlarında sunucunun `StackOverflowError` ile çökmesi veya lag oluşması engellenir.
* **Güvenli Özel Formül Motoru:** JavaScript (`Nashorn`) gibi ağır, kullanımdan kaldırılmış ve uzaktan kod yürütme (`exploit`) riski taşıyan yapılar yerine; tamamen güvenli, yerleşik ve hafif bir metin parçalayıcı kullanılır.
* **Güvenli Yeniden Yükleme:** Konfigürasyon dosyalarında bir YAML sözdizimi hatası olursa, plugin hatayı günlüğe kaydeder ve çalışmasını bozmadan eski kararlı yapılandırmayı bellekte tutmaya devam eder.

### Komutlar ve Yetkiler
* `/split reload` ── Eklentinin yapılandırma dosyalarını (`config.yml`, `messages.yml`, `placeholders.yml`) yeniden yükler.
  * **Yetki:** `split.admin`

### Yapılandırma Dosyaları

#### `config.yml`
Genel eklenti ayarları.
```yaml
# Konsolda detaylı hata/durum loglamasını etkinleştirir
debug: false
# Çevrimdışı/null oyuncu sorgularında varsayılan olarak Java değerlerine döner
default-to-java-on-null: true
```

#### `messages.yml`
Özelleştirilebilir mesajlar. Standart renk kodlarını (`&`) ve modern Hex renk kodlarını (`&#ffffff`) destekler.
```yaml
prefix: "&8[&bSplit&8] &r"
no-permission: "%prefix%&cBu komutu kullanmak için yetkiniz yok!"
only-players: "%prefix%&cBu komut sadece oyuncular tarafından kullanılabilir!"
reload-success: "%prefix%&aYapılandırma dosyaları başarıyla yenilendi."
reload-failure: "%prefix%&cYapılandırma dosyaları yenilenirken bir hata oluştu! Detaylar için konsola göz atın."
invalid-usage: "%prefix%&cGeçersiz kullanım! &fKullanım: /split reload"
```

#### `placeholders.yml`
Placeholder tanımlamaları ve platform/koşul kuralları:
```yaml
# 1. Basit Platform Ayrım Tipi
example:
  type: "simple"
  java: "Java"
  bedrock: "Bedrock"

# 2. Değer Eşleştirme (Switch) Tipi
example_switch:
  type: "switch"
  switch: "%luckperms_highest_group_by_weight%"
  case:
    "coal": "Coal"
    default: "Player"

# 3. Matematiksel/Mantıksal Koşul (Expression) Tipi
# Desteklenen işlemler: >, <, >=, <=, ==, !=, &&, ||, AND, OR.
# Alternatif karşılaştırma sembolleri: >> (büyüktür), << (küçüktür), <> (eşit değildir).
example_expression:
  type: "expression"
  formule: "%player_ping% >> 60 && %player_ping% << 120"
  true: "Ping is stable"
  false: "Ping is not stable"
```
