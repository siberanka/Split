# Split ── Bedrock/Java Dynamic Placeholder Plugin

A lightweight, high-performance, and secure Minecraft plugin for Spigot/Paper servers. Split provides platform-aware values, switch mappings, expression evaluation, and highly configurable adaptive output based on the resolved character count of one or more PlaceholderAPI values.

---

## 🇬🇧 English Documentation

### Features
* **Polymorphic Placeholders:** Supports `simple`, `switch`, `expression`, and `adaptive` types.
* **Dual-Platform Handling (`simple`):** Serves different template values for Bedrock and Java clients.
* **Case-Switch Mapping (`switch`):** Resolves a target placeholder and matches it against custom case keys with a fallback `default` case.
* **Boolean Expression Evaluator (`expression`):** Evaluates mathematical/relational expressions and returns a true or false value.
* **Adaptive Output (`adaptive`):** Counts resolved Unicode characters and returns a bounded number, repeated spaces/symbols/text, or a custom template using direct or inverse scaling.
* **PlaceholderAPI Integration:** Registers custom `%split_<key>%` placeholders and resolves nested placeholders (e.g. `%player_name%`) in the returned values.
* **100% Thread-Safe & Atomic:** Custom configuration loading with atomic volatile swaps. Zero locks are held during placeholder requests.
* **Asynchronous Reloading:** Config reloads happen in a background thread, preventing server lag spikes (disk I/O) on the main thread.
* **Circular Reference Protection:** Safe evaluation using `ThreadLocal` recursion detectors. Prevents admin formatting mistakes from crashing the server with `StackOverflowError`.
* **Safe Custom Expression Parser:** Uses a built-in, lightweight, and 100% secure tokenizer. No scripting engine (like JavaScript Nashorn) is utilized, completely eliminating code-injection exploits.
* **Robust Configuration Reloading:** In the event of a YAML formatting syntax error, the plugin logs the details and maintains the current running configurations instead of crashing.
* **Self-Updating Local Wiki:** A detailed bilingual `plugins/Split/wiki.yml` is restored when deleted and atomically refreshed when the bundled guide changes. Every line is a YAML comment; it is documentation only and is never read as configuration.

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
reload-in-progress: "%prefix%&eA configuration reload is already in progress."
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

# 4. Adaptive Output Type
adaptive_spacing:
  type: "adaptive"
  source:
    - "%vault_eco_balance_formatted%"
    - "%superior_island_level_format%"
  calculation:
    mode: "inverse"
    ratio: 1.0
    base: 32
    minimum: 2
    maximum: 32
    rounding: "nearest"
  source-options:
    separator: ""
    cooldown-milliseconds: 250
    max-cache-entries: 1024
    trim: false
    strip-color-codes: true
    count-whitespace: true
    max-characters: 8192
  result:
    type: "repeat"
    value: " "
    template: "{count}"
    max-length: 8192
```

### Adaptive Placeholder Reference

Use `%split_adaptive_spacing%` for the example above. `source` accepts either one string (backward compatible) or a YAML list of up to 32 strings. Each list item may contain plain text, spaces, punctuation, one placeholder, or adjacent placeholders such as `%placeholder1%%placeholder2%`. List items are resolved individually in order, joined with `source-options.separator`, and counted as Unicode code points, so an emoji is one character.

Adaptive results are cached separately for each player UUID and adaptive key. Every request checks freshness: after the cooldown expires, all configured sources are resolved again and the result is recalculated. Requests inside the cooldown receive the last calculated output unchanged—Split never returns a cooldown notice. Concurrent refreshes for the same player/key are combined into one calculation; the bounded cache is also cleared for a player on quit. If a source expansion temporarily fails, the last successful output (or an empty value before the first success) is served silently until the next retry window.

The calculation formulas are:

```text
direct  = base + (source length × ratio)
inverse = base - (source length × ratio)
result  = rounded and clamped to [minimum, maximum]
```

| Setting | Values / behavior |
| --- | --- |
| `calculation.mode` | `direct` increases the result; `inverse` decreases it as the source grows. |
| `calculation.ratio` | Non-negative decimal multiplier applied per counted character. |
| `calculation.base` | Starting value before the character adjustment. Defaults to `minimum` in direct mode and `maximum` in inverse mode. |
| `calculation.minimum` / `maximum` | Inclusive result range. The hard safety ceiling is 4096. |
| `calculation.rounding` | `floor`, `ceiling`, or `nearest`. |
| `source` | One string or an ordered YAML list of `1..32` strings; total configured limit `8192` Unicode characters. |
| `source-options.separator` | Literal text inserted between resolved list items; default empty text, maximum 128 characters. It is included in the measured source. |
| `source-options.cooldown-milliseconds` | Per-player refresh interval, `100..60000`; default `250`. Cached output is returned normally during the interval. |
| `source-options.max-cache-entries` | Per-adaptive-placeholder UUID cache bound, `1..4096`; default `1024`. |
| `source-options.trim` | Removes leading and trailing whitespace before counting. |
| `source-options.strip-color-codes` | Ignores `&`/`§` legacy and hex color sequences. |
| `source-options.count-whitespace` | Includes or excludes whitespace characters from the measured length. |
| `source-options.max-characters` | Limits source characters inspected; range `1..32768`. |
| `result.type` | `repeat`, `number`, or `template`. |
| `result.value` | Any literal text repeated by `repeat`; it may be a space, symbol, emoji, or multi-character sequence. |
| `result.template` | Template supporting `{count}`, `{length}`, `{source}`, and `{value}`. |
| `result.max-length` | Final output safety limit; range `1..16384` Unicode characters. |

`result.value` is the recommended nested form. For compatibility, top-level `value`, `output-value`, and `space-character` are also accepted. Normal text never needs a Unicode escape: `value: "-"` returns hyphens, while `value: "ABC"` repeats the complete `ABC` text.

Output examples:

```yaml
# Return the calculated number, e.g. "17"
adaptive_count:
  type: "adaptive"
  source: "%player_name%"
  calculation: { mode: "direct", ratio: 1.5, base: 0, minimum: 0, maximum: 100 }
  result: { type: "number" }

# Repeat a custom symbol, e.g. "••••••"
adaptive_symbol:
  type: "adaptive"
  source: "%player_name%"
  calculation: { mode: "direct", ratio: 1, base: 0, minimum: 0, maximum: 32 }
  result: { type: "repeat", value: "•", max-length: 128 }

# Produce a fully formatted result, e.g. "Alex: 4 chars / 12 units"
adaptive_template:
  type: "adaptive"
  source: "%player_name%"
  calculation: { mode: "inverse", ratio: 1, base: 16, minimum: 0, maximum: 16 }
  result:
    type: "template"
    value: "•"
    template: "{source}: {length} chars / {count} units"
```

Adaptive results are returned literally and are not parsed a second time by PlaceholderAPI. This prevents a repeated symbol containing `%` from turning into an accidental placeholder chain and keeps the workload bounded. Invalid ranges, numeric values, or limits make reload fail safely while the last valid configuration remains active.

#### `wiki.yml`

Split creates a complete English/Turkish tutorial at `plugins/Split/wiki.yml`. Every line is a comment, so the file is valid empty YAML with no settings. The guide documents every placeholder type, setting, alias, formula, limit, command, and troubleshooting flow. Split never reads it as configuration. If deleted, edited, or outdated after an update, it is replaced with the current bundled copy on startup or `/split reload`.

---

## 🇹🇷 Türkçe Dokümantasyon

### Özellikler
* **Polimorfik Placeholder'lar:** Dinamik çözümleme için `simple`, `switch`, `expression` ve `adaptive` tiplerini destekler.
* **Platform Ayrımı (`simple`):** Bedrock ve Java istemcileri için farklı şablon çıktıları sağlar.
* **Eşleşme Eşitleme (`switch`):** Belirtilen hedef placeholder değerini çözümler ve tanımlı durumlarla (case) eşleştirir; eşleşme yoksa `default` değerini döndürür.
* **Mantıksal Karşılaştırma (`expression`):** Matematiksel/mantıksal formülleri çözümler ve sonucuna göre true veya false değerini döndürür.
* **Adaptif Çıktı (`adaptive`):** Çözümlenmiş Unicode karakterlerini sayar; doğrudan veya ters ölçeklemeyle sınırlanmış bir sayı, gereken miktarda boşluk/sembol/metin ya da özel şablon döndürür.
* **PlaceholderAPI Entegrasyonu:** Özel `%split_<anahtar>%` placeholder'ları tanımlayabilir ve bunların içindeki diğer placeholder'ları (örn. `%player_name%`) otomatik olarak çözümler.
* **%100 Thread-Safe & Atomik:** Atomik geçişli ve uçucu (`volatile`) değişken yapılandırması sayesinde placeholder sorguları sırasında sunucu üzerinde sıfır kilitlenme (lock contention) oluşturur.
* **Asenkron Yenileme:** Yapılandırma yenileme işlemleri arka planda asenkron olarak gerçekleşir. Bu sayede sunucu ana iş parçacığında (main thread) disk okuma kaynaklı FPS/TPS düşüşleri yaşanmaz.
* **Kısır Döngü Koruması:** `ThreadLocal` tabanlı döngü algılayıcılar sayesinde yönetici hatalarından kaynaklanabilecek circular-reference (iç içe sonsuz döngü) durumlarında sunucunun `StackOverflowError` ile çökmesi veya lag oluşması engellenir.
* **Güvenli Özel Formül Motoru:** JavaScript (`Nashorn`) gibi ağır, kullanımdan kaldırılmış ve uzaktan kod yürütme (`exploit`) riski taşıyan yapılar yerine; tamamen güvenli, yerleşik ve hafif bir metin parçalayıcı kullanılır.
* **Güvenli Yeniden Yükleme:** Konfigürasyon dosyalarında bir YAML sözdizimi hatası olursa, plugin hatayı günlüğe kaydeder ve çalışmasını bozmadan eski kararlı yapılandırmayı bellekte tutmaya devam eder.
* **Kendini Güncelleyen Yerel Wiki:** Ayrıntılı, iki dilli `plugins/Split/wiki.yml` silindiğinde geri oluşturulur ve paket rehberi değiştiğinde atomik biçimde yenilenir. Her satırı YAML yorumudur; yalnızca dokümantasyondur ve hiçbir zaman ayar olarak okunmaz.

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
reload-in-progress: "%prefix%&eBir yapılandırma yenilemesi zaten devam ediyor."
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

# 4. Adaptif Çıktı Tipi
adaptive_spacing:
  type: "adaptive"
  source:
    - "%vault_eco_balance_formatted%"
    - "%superior_island_level_format%"
  calculation:
    mode: "inverse"
    ratio: 1.0
    base: 32
    minimum: 2
    maximum: 32
    rounding: "nearest"
  source-options:
    separator: ""
    cooldown-milliseconds: 250
    max-cache-entries: 1024
    trim: false
    strip-color-codes: true
    count-whitespace: true
    max-characters: 8192
  result:
    type: "repeat"
    value: " "
    template: "{count}"
    max-length: 8192
```

### Adaptif Placeholder Ayarları

Yukarıdaki örnek `%split_adaptive_spacing%` olarak kullanılır. `source`, geriye uyumlu tek metin biçimini veya en fazla 32 metinden oluşan bir YAML listesini kabul eder. Her liste öğesi normal metin, boşluk, noktalama, tek placeholder ya da `%placeholder1%%placeholder2%` gibi bitişik placeholder'lar içerebilir. Öğeler sırayla ayrı ayrı çözümlenir, `source-options.separator` ile birleştirilir ve Unicode code point olarak sayılır; bu nedenle bir emoji tek karakter kabul edilir.

Adaptif sonuçlar her oyuncu UUID'si ve adaptif anahtar için ayrı tutulur. Her çağrıda tazelik kontrol edilir: cooldown dolduktan sonraki ilk çağrıda listedeki bütün kaynaklar yeniden çözümlenir ve hesap yeniden yapılır. Cooldown içindeki çağrılar son hesaplanan çıktıyı aynen alır; Split hiçbir zaman “cooldown'da” benzeri bir metin döndürmez. Aynı oyuncu/anahtar için eşzamanlı yenilemeler tek hesapta birleştirilir, sınırlı cache oyuncu çıkışında da temizlenir. Kaynak çözümleme geçici olarak hata verirse son başarılı çıktı (ilk başarıdan önce boş değer) bir sonraki deneme aralığına kadar sessizce sunulur.

Hesaplama formülleri:

```text
direct  = base + (kaynak uzunluğu × ratio)
inverse = base - (kaynak uzunluğu × ratio)
sonuç   = yuvarlanır ve [minimum, maximum] aralığına sınırlandırılır
```

| Ayar | Değer / davranış |
| --- | --- |
| `calculation.mode` | `direct` kaynak uzadıkça sonucu artırır; `inverse` azaltır. |
| `calculation.ratio` | Sayılan her karakter için uygulanan negatif olmayan ondalık oran. |
| `calculation.base` | Karakter hesabından önceki başlangıç değeri. Girilmezse direct modunda `minimum`, inverse modunda `maximum` kullanılır. |
| `calculation.minimum` / `maximum` | Sonucun dahilî alt/üst sınırı. Güvenlik üst sınırı 4096'dır. |
| `calculation.rounding` | `floor` (aşağı), `ceiling` (yukarı) veya `nearest` (en yakın). |
| `source` | Tek metin veya sıralı `1..32` öğelik YAML listesi; toplam yapılandırılmış sınır `8192` Unicode karakteridir. |
| `source-options.separator` | Çözümlenen liste öğeleri arasına eklenen literal metin; varsayılan boş, en fazla 128 karakterdir ve kaynak sayımına dahildir. |
| `source-options.cooldown-milliseconds` | Oyuncu başına yenileme aralığı, `100..60000`; varsayılan `250`. Aralık içinde cache çıktısı normal biçimde döner. |
| `source-options.max-cache-entries` | Her adaptif placeholder için UUID cache sınırı, `1..4096`; varsayılan `1024`. |
| `source-options.trim` | Sayımdan önce baştaki ve sondaki boşlukları kaldırır. |
| `source-options.strip-color-codes` | `&`/`§` legacy ve hex renk dizilerini sayım dışı bırakır. |
| `source-options.count-whitespace` | Kaynaktaki boşluk karakterlerini sayıma dahil eder veya çıkarır. |
| `source-options.max-characters` | İncelenecek kaynak uzunluğunu sınırlar; aralık `1..32768`. |
| `result.type` | `repeat`, `number` veya `template`. |
| `result.value` | `repeat` modunda tekrarlanan literal değer; boşluk, sembol, emoji veya çok karakterli metin olabilir. |
| `result.template` | `{count}`, `{length}`, `{source}` ve `{value}` alanlarını destekleyen özel çıktı şablonu. |
| `result.max-length` | Nihai çıktı güvenlik sınırı; `1..16384` Unicode karakteri. |

Önerilen kullanım `result.value` biçimidir. Uyumluluk için üst seviyede `value`, `output-value` ve `space-character` da kabul edilir. Normal metin Unicode kaçışına ihtiyaç duymaz: `value: "-"` tire, `value: "ABC"` ise `ABC` metninin tamamını tekrarlar.

Farklı çıktı örnekleri:

```yaml
# Hesaplanan sayıyı döndürür; örneğin "17"
adaptive_count:
  type: "adaptive"
  source: "%player_name%"
  calculation: { mode: "direct", ratio: 1.5, base: 0, minimum: 0, maximum: 100 }
  result: { type: "number" }

# Belirlenen simgeyi gereken sayıda tekrarlar; örneğin "••••••"
adaptive_symbol:
  type: "adaptive"
  source: "%player_name%"
  calculation: { mode: "direct", ratio: 1, base: 0, minimum: 0, maximum: 32 }
  result: { type: "repeat", value: "•", max-length: 128 }

# Tamamen özel bir metin üretir; örneğin "Alex: 4 karakter / 12 birim"
adaptive_template:
  type: "adaptive"
  source: "%player_name%"
  calculation: { mode: "inverse", ratio: 1, base: 16, minimum: 0, maximum: 16 }
  result:
    type: "template"
    value: "•"
    template: "{source}: {length} karakter / {count} birim"
```

Adaptif sonuçlar literal olarak döndürülür ve PlaceholderAPI tarafından ikinci kez ayrıştırılmaz. Böylece `%` içeren bir simgenin yanlışlıkla yeni bir placeholder zincirine dönüşmesi engellenir ve işlem yükü sınırlı kalır. Geçersiz aralık, sayı veya limit içeren bir reload güvenli biçimde reddedilir; son geçerli yapılandırma çalışmaya devam eder.

#### `wiki.yml`

Split, `plugins/Split/wiki.yml` konumunda eksiksiz İngilizce/Türkçe eğitim dosyası oluşturur. Dosyanın her satırı yorumdur; bu nedenle geçerli fakat boş bir YAML'dır ve hiçbir ayar geçerliliği yoktur. Rehber tüm placeholder tiplerini, ayarları, alias'ları, formülleri, sınırları, komutları ve sorun giderme akışlarını açıklar. Split bu dosyayı yapılandırma olarak okumaz. Dosya silinirse, düzenlenirse veya plugin güncellemesinden sonra eski kalırsa açılışta ya da `/split reload` sırasında güncel paket kopyasıyla değiştirilir.
