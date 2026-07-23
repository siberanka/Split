# Split ── Bedrock/Java Dynamic Placeholder Plugin

A lightweight, high-performance, and secure Minecraft plugin for Spigot, Paper, and Folia servers. Split provides platform-aware values, target-player parsing, switch mappings, expression evaluation, and highly configurable adaptive output.

---

## 🇬🇧 English Documentation

### Features
* **Polymorphic Placeholders:** Supports `simple`, `parse`, `switch`, `expression`, and `adaptive` types.
* **Dual-Platform Handling (`simple`):** Serves different template values for Bedrock and Java clients.
* **Target-Player Parsing (`parse`):** Resolves a player name from one placeholder, then evaluates a Java/Bedrock template using that exact online or known offline player as the PlaceholderAPI context.
* **Case-Switch Mapping (`switch`):** Resolves a target placeholder and matches it against custom case keys with a fallback `default` case.
* **Boolean Expression Evaluator (`expression`):** Evaluates mathematical/relational expressions and returns a true or false value.
* **Adaptive Output (`adaptive`):** Counts resolved Unicode characters and returns a bounded number, repeated spaces/symbols/text, or a custom template using direct, reverse, or linear map scaling—including negative numeric results.
* **PlaceholderAPI Integration:** Registers custom `%split_<key>%` placeholders and resolves nested placeholders (e.g. `%player_name%`) in the returned values.
* **Native Folia Scheduling:** Declares Folia support and routes asynchronous I/O, console replies, and player replies through Folia's async, global-region, and entity schedulers respectively while retaining Bukkit/Paper fallback behavior.
* **Thread-Safe & Atomic:** Configuration reloads use atomic immutable snapshots; adaptive and parse refreshes are bounded and same-key concurrent work is coalesced.
* **Asynchronous Reloading:** Configuration disk I/O runs outside tick threads on Bukkit/Paper and Folia.
* **Circular Reference Protection:** Safe evaluation using `ThreadLocal` recursion detectors. Prevents admin formatting mistakes from crashing the server with `StackOverflowError`.
* **Safe Custom Expression Parser:** Uses a built-in, lightweight, and 100% secure tokenizer. No scripting engine (like JavaScript Nashorn) is utilized, completely eliminating code-injection exploits.
* **Robust Configuration Reloading:** In the event of a YAML formatting syntax error, the plugin logs the details and maintains the current running configurations instead of crashing.
* **Self-Updating Local Wiki:** A detailed bilingual `plugins/Split/wiki.yml` is restored when deleted and atomically refreshed when the bundled guide changes. Every line is a YAML comment; it is documentation only and is never read as configuration.

### Commands & Permissions
* `/split reload` ── Reloads the plugin configuration files (`config.yml`, `messages.yml`, `placeholders.yml`).
  * **Permission:** `split.admin`

### Server Compatibility

Split requires Java 21 and targets the Spigot 1.20.4 API. The same JAR supports Spigot/Paper-compatible servers and Folia. The build uses PlaceholderAPI 2.12.2; Folia installations must use a Folia-capable PlaceholderAPI release (2.11.7 or newer). On Folia, `folia-supported: true` is declared in `plugin.yml`; reload file I/O uses the Folia async scheduler, console-like responses use the global-region scheduler, and player responses use the player's entity scheduler so the task follows that player across regions. On Bukkit/Paper, the equivalent Bukkit scheduler paths are used.

Plugin shutdown stops accepting new work and cancels owned async/global tasks. A response for a player who disconnected or whose entity scheduler retired is dropped safely. Placeholder evaluation remains synchronous in the caller's valid context because PlaceholderAPI expansions have synchronous return contracts. A `parse` result targeting another Folia region is refreshed non-blockingly on the target player's entity scheduler and served from its bounded cache; Split never blocks one Folia region while waiting for another.

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

# 4. Target-Player Parse Type
example_target:
  type: "parse"
  parse: "%example_player%"
  java: "%example_java%"
  bedrock: "%example_bedrock%"
  parse-options:
    allow-offline: true
    cooldown-milliseconds: 250
    max-cache-entries: 1024
    max-output-length: 4096

# 5. Adaptive Output Type
adaptive_spacing:
  type: "adaptive"
  source:
    - "%vault_eco_balance_formatted%"
    - "%superior_island_level_format%"
  calculation:
    mode: "reverse"
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

### Parse Placeholder Reference

Use `%split_example_target%` for the parse example. Split first resolves `parse` with the requesting player, trims and validates the result, then prefers an exact online-player match. When `allow-offline` is enabled, a known player who has joined the server before is also accepted. Unknown profiles, malformed names, and failed resolutions return an empty string.

After finding the target, Split checks that target through Floodgate and resolves either `java` or `bedrock` exactly once with the target player as PlaceholderAPI context. The produced text is returned literally instead of being parsed again, preventing placeholder-output injection and accidental recursive chains. `%split_*%` references deliberately configured inside `java`/`bedrock` still pass through Split's existing recursion/depth guards during that one parse.

| Setting | Values / behavior |
| --- | --- |
| `type` | Must be `parse`; when `type` is omitted, the presence of `parse` selects this mode. |
| `parse` | Required non-empty selector text. It may contain placeholders and must resolve to an exact online or known offline player name; resolved names are limited to 64 Unicode code points and safe player-name characters. |
| `java` | Template resolved once using the target Java player. |
| `bedrock` | Template resolved once using the target Bedrock player. Without Floodgate, targets use `java`. |
| `parse-options.allow-offline` | Allows exact, previously known offline targets; default `true`. Set to `false` to require an online target. |
| `parse-options.cooldown-milliseconds` | Per-target refresh interval, `100..60000`; default `250`. |
| `parse-options.max-cache-entries` | Per-parse-placeholder target/result and positive/negative lookup bound, `1..4096`; default `1024`. Once the offline lookup ceiling is reached, new names fail closed until reload. |
| `parse-options.max-output-length` | Final output limit in Unicode code points, `1..16384`; default `4096`. |

Known offline targets are evaluated synchronously as `OfflinePlayer` contexts. Whether a third-party placeholder can return offline data depends on that PlaceholderAPI expansion; unsupported values usually remain empty or use that expansion's fallback. Online targets are immediate on Spigot/Paper's primary thread. If invoked outside that thread, Split schedules safely and returns the last cached result. On Folia, online-target evaluation uses the target player's entity scheduler, so the first uncached request may be empty for one scheduler cycle. Cooldown hits never return a status message.

### Adaptive Placeholder Reference

Use `%split_adaptive_spacing%` for the example above. `source` accepts either one string (backward compatible) or a YAML list of up to 32 strings. Each list item may contain plain text, spaces, punctuation, one placeholder, or adjacent placeholders such as `%placeholder1%%placeholder2%`. List items are resolved individually in order, joined with `source-options.separator`, and counted as Unicode code points, so an emoji is one character.

Adaptive results are cached separately for each player UUID and adaptive key. Every request checks freshness: after the cooldown expires, all configured sources are resolved again and the result is recalculated. Requests inside the cooldown receive the last calculated output unchanged—Split never returns a cooldown notice. Concurrent refreshes for the same player/key are combined into one calculation; the bounded cache is also cleared for a player on quit. If a source expansion temporarily fails, the last successful output (or an empty value before the first success) is served silently until the next retry window.

The calculation formulas are:

```text
direct  = base + (source length × ratio)
reverse = base - (source length × ratio)
map     = minimum + ((source length - source-minimum) / (source-maximum - source-minimum)) × (maximum - minimum)
result  = rounded and clamped between the two configured output endpoints
```

| Setting | Values / behavior |
| --- | --- |
| `calculation.mode` | `direct` increases, `reverse` decreases, and `map` linearly maps the configured source range to the result range. `inverse` remains a compatibility alias for `reverse`. |
| `calculation.ratio` | Non-negative decimal multiplier used by `direct`/`reverse`; ignored by `map`. |
| `calculation.base` | Starting value used by `direct`/`reverse`; ignored by `map`. Defaults to `minimum` in direct mode and `maximum` in reverse mode. |
| `calculation.minimum` / `maximum` | Output endpoints, each independently limited to `-4096..4096`. In `direct`/`reverse`, `minimum <= maximum` is required. In `map`, either order is valid: `minimum: 100` and `maximum: -100` creates a descending map. |
| `calculation.source-minimum` / `source-maximum` | Input character range used only by `map`; defaults to `0` and `source-options.max-characters`. The lower source endpoint maps to `minimum`, and the upper endpoint maps to `maximum`, regardless of their numeric order. |
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
  calculation: { mode: "reverse", ratio: 1, base: 16, minimum: 0, maximum: 16 }
  result:
    type: "template"
    value: "•"
    template: "{source}: {length} chars / {count} units"

# Descending map: 0 characters -> 100, 10 -> 0, 20 or more -> -100
adaptive_mapped_number:
  type: "adaptive"
  source: "%player_name%"
  calculation:
    mode: "map"
    source-minimum: 0
    source-maximum: 20
    minimum: 100
    maximum: -100
    rounding: "nearest"
  result: { type: "number" }
```

Adaptive results are returned literally and are not parsed a second time by PlaceholderAPI. Negative values are visible in `number` and `{count}` template output. Because text cannot be repeated a negative number of times, `repeat` returns an empty string when the calculated value is zero or negative. This prevents a repeated symbol containing `%` from turning into an accidental placeholder chain and keeps the workload bounded. Invalid ranges, numeric values, or limits make reload fail safely while the last valid configuration remains active.

#### `wiki.yml`

Split creates a complete English/Turkish tutorial at `plugins/Split/wiki.yml`. Every line is a comment, so the file is valid empty YAML with no settings. The guide documents every placeholder type, setting, alias, formula, limit, command, and troubleshooting flow. Split never reads it as configuration. If deleted, edited, or outdated after an update, it is replaced with the current bundled copy on startup or `/split reload`.

---

## 🇹🇷 Türkçe Dokümantasyon

### Özellikler
* **Polimorfik Placeholder'lar:** Dinamik çözümleme için `simple`, `parse`, `switch`, `expression` ve `adaptive` tiplerini destekler.
* **Platform Ayrımı (`simple`):** Bedrock ve Java istemcileri için farklı şablon çıktıları sağlar.
* **Hedef Oyuncu Ayrıştırma (`parse`):** Bir placeholder'dan oyuncu adını çözümler, ardından Java/Bedrock şablonunu tam eşleşen çevrimiçi veya bilinen çevrimdışı hedef oyuncu bağlamında çalıştırır.
* **Eşleşme Eşitleme (`switch`):** Belirtilen hedef placeholder değerini çözümler ve tanımlı durumlarla (case) eşleştirir; eşleşme yoksa `default` değerini döndürür.
* **Mantıksal Karşılaştırma (`expression`):** Matematiksel/mantıksal formülleri çözümler ve sonucuna göre true veya false değerini döndürür.
* **Adaptif Çıktı (`adaptive`):** Çözümlenmiş Unicode karakterlerini sayar; direct, reverse veya doğrusal map ölçeklemesiyle negatif olabilen sayı, gereken miktarda boşluk/sembol/metin ya da özel şablon döndürür.
* **PlaceholderAPI Entegrasyonu:** Özel `%split_<anahtar>%` placeholder'ları tanımlayabilir ve bunların içindeki diğer placeholder'ları (örn. `%player_name%`) otomatik olarak çözümler.
* **Doğal Folia Scheduler Desteği:** Folia desteğini bildirir; asenkron I/O, konsol cevapları ve oyuncu cevaplarını sırasıyla Folia async, global-region ve entity scheduler üzerinden yürütürken Bukkit/Paper geri dönüş yolunu korur.
* **Thread-Safe & Atomik:** Yapılandırma reload'ları atomik ve değişmez snapshot kullanır; adaptif ve parse yenilemeleri sınırlıdır ve aynı anahtardaki eşzamanlı işler tek hesapta birleştirilir.
* **Asenkron Yenileme:** Yapılandırma disk I/O işlemleri Bukkit/Paper ve Folia tick thread'lerinin dışında çalışır.
* **Kısır Döngü Koruması:** `ThreadLocal` tabanlı döngü algılayıcılar sayesinde yönetici hatalarından kaynaklanabilecek circular-reference (iç içe sonsuz döngü) durumlarında sunucunun `StackOverflowError` ile çökmesi veya lag oluşması engellenir.
* **Güvenli Özel Formül Motoru:** JavaScript (`Nashorn`) gibi ağır, kullanımdan kaldırılmış ve uzaktan kod yürütme (`exploit`) riski taşıyan yapılar yerine; tamamen güvenli, yerleşik ve hafif bir metin parçalayıcı kullanılır.
* **Güvenli Yeniden Yükleme:** Konfigürasyon dosyalarında bir YAML sözdizimi hatası olursa, plugin hatayı günlüğe kaydeder ve çalışmasını bozmadan eski kararlı yapılandırmayı bellekte tutmaya devam eder.
* **Kendini Güncelleyen Yerel Wiki:** Ayrıntılı, iki dilli `plugins/Split/wiki.yml` silindiğinde geri oluşturulur ve paket rehberi değiştiğinde atomik biçimde yenilenir. Her satırı YAML yorumudur; yalnızca dokümantasyondur ve hiçbir zaman ayar olarak okunmaz.

### Komutlar ve Yetkiler
* `/split reload` ── Eklentinin yapılandırma dosyalarını (`config.yml`, `messages.yml`, `placeholders.yml`) yeniden yükler.
  * **Yetki:** `split.admin`

### Sunucu Uyumluluğu

Split Java 21 gerektirir ve Spigot 1.20.4 API'sini hedefler. Aynı JAR Spigot/Paper uyumlu sunucuları ve Folia'yı destekler. Build PlaceholderAPI 2.12.2 kullanır; Folia kurulumunda Folia destekli PlaceholderAPI sürümü (2.11.7 veya üzeri) kullanılmalıdır. Folia üzerinde `plugin.yml` içinde `folia-supported: true` bildirilir; reload dosya I/O işlemi Folia async scheduler, konsol benzeri cevaplar global-region scheduler, oyuncu cevaplarıysa bölgeler arasında oyuncuyu takip eden entity scheduler üzerinden çalışır. Bukkit/Paper üzerinde eşdeğer Bukkit scheduler yolları kullanılır.

Plugin kapanırken yeni görev kabulü durdurulur ve sahip olunan async/global işler iptal edilir. Sunucudan ayrılmış veya entity scheduler'ı retired olmuş oyuncunun cevabı güvenli biçimde bırakılır. PlaceholderAPI expansion'larının senkron dönüş sözleşmesi nedeniyle placeholder hesaplaması çağıranın geçerli bağlamında senkron kalır. Başka bir Folia bölgesindeki oyuncuyu hedefleyen `parse` sonucu, hedef oyuncunun entity scheduler'ında bloklamadan yenilenir ve sınırlı cache üzerinden sunulur; Split başka bir region thread'ini beklemez.

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

# 4. Hedef Oyuncu Parse Tipi
example_target:
  type: "parse"
  parse: "%example_player%"
  java: "%example_java%"
  bedrock: "%example_bedrock%"
  parse-options:
    allow-offline: true
    cooldown-milliseconds: 250
    max-cache-entries: 1024
    max-output-length: 4096

# 5. Adaptif Çıktı Tipi
adaptive_spacing:
  type: "adaptive"
  source:
    - "%vault_eco_balance_formatted%"
    - "%superior_island_level_format%"
  calculation:
    mode: "reverse"
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

### Parse Placeholder Ayarları

Parse örneği `%split_example_target%` olarak kullanılır. Split önce `parse` alanını placeholder'ı isteyen oyuncu bağlamında çözümler, sonucu kırpıp doğrular ve öncelikle tam eşleşen çevrimiçi oyuncuyu arar. `allow-offline` açıksa sunucuya daha önce katılmış bilinen çevrimdışı oyuncular da kabul edilir. Bilinmeyen profil, bozuk ad veya çözümleme hatası boş metin döndürür.

Hedef bulunduktan sonra platformu Floodgate ile kontrol edilir ve `java` ya da `bedrock` şablonu hedef oyuncu bağlamında yalnızca bir kez PlaceholderAPI'den geçirilir. Üretilen çıktı yeniden parse edilmeden literal döner; böylece placeholder çıktısı enjeksiyonu ve istemsiz recursive zincir engellenir. `java`/`bedrock` içine yönetici tarafından bilerek yazılan `%split_*%` referansları, bu tek parse sırasında mevcut recursion/derinlik korumasından geçer.

| Ayar | Değer / davranış |
| --- | --- |
| `type` | `parse` olmalıdır; `type` yazılmazsa `parse` alanının bulunması bu modu seçer. |
| `parse` | Zorunlu ve boş olmayan seçici metindir. Placeholder içerebilir ve tam çevrimiçi veya bilinen çevrimdışı oyuncu adına çözülmelidir; ad en fazla 64 Unicode code point ve güvenli oyuncu-adı karakterleri içerebilir. |
| `java` | Hedef Java oyuncusu bağlamında bir kez çözümlenen şablon. |
| `bedrock` | Hedef Bedrock oyuncusu bağlamında bir kez çözümlenen şablon. Floodgate yoksa `java` kullanılır. |
| `parse-options.allow-offline` | Tam eşleşen, sunucunun daha önce gördüğü çevrimdışı hedeflere izin verir; varsayılan `true`. Yalnızca çevrimiçi hedef için `false` yapılır. |
| `parse-options.cooldown-milliseconds` | Hedef başına yenileme aralığı `100..60000`; varsayılan `250`. |
| `parse-options.max-cache-entries` | Her parse placeholder için hedef/sonuç ve olumlu/olumsuz arama sınırı `1..4096`; varsayılan `1024`. Çevrimdışı arama tavanı dolduğunda yeni adlar reload'a kadar güvenli biçimde reddedilir. |
| `parse-options.max-output-length` | Unicode code point cinsinden nihai çıktı sınırı `1..16384`; varsayılan `4096`. |

Bilinen çevrimdışı hedefler `OfflinePlayer` bağlamında senkron çözülür. Üçüncü taraf bir placeholder'ın çevrimdışı veri döndürüp döndürememesi ilgili PlaceholderAPI expansion'ına bağlıdır; desteklenmeyen değer genellikle boş veya expansion'ın fallback sonucudur. Çevrimiçi hedef Spigot/Paper ana thread'inde anında üretilir. Ana thread dışında scheduler'a taşınır. Folia'da çevrimiçi hedef kendi entity scheduler'ında hesaplanır; ilk cache'siz istek bir scheduler döngüsü boyunca boş olabilir. Cooldown sırasında durum mesajı dönmez.

### Adaptif Placeholder Ayarları

Yukarıdaki örnek `%split_adaptive_spacing%` olarak kullanılır. `source`, geriye uyumlu tek metin biçimini veya en fazla 32 metinden oluşan bir YAML listesini kabul eder. Her liste öğesi normal metin, boşluk, noktalama, tek placeholder ya da `%placeholder1%%placeholder2%` gibi bitişik placeholder'lar içerebilir. Öğeler sırayla ayrı ayrı çözümlenir, `source-options.separator` ile birleştirilir ve Unicode code point olarak sayılır; bu nedenle bir emoji tek karakter kabul edilir.

Adaptif sonuçlar her oyuncu UUID'si ve adaptif anahtar için ayrı tutulur. Her çağrıda tazelik kontrol edilir: cooldown dolduktan sonraki ilk çağrıda listedeki bütün kaynaklar yeniden çözümlenir ve hesap yeniden yapılır. Cooldown içindeki çağrılar son hesaplanan çıktıyı aynen alır; Split hiçbir zaman “cooldown'da” benzeri bir metin döndürmez. Aynı oyuncu/anahtar için eşzamanlı yenilemeler tek hesapta birleştirilir, sınırlı cache oyuncu çıkışında da temizlenir. Kaynak çözümleme geçici olarak hata verirse son başarılı çıktı (ilk başarıdan önce boş değer) bir sonraki deneme aralığına kadar sessizce sunulur.

Hesaplama formülleri:

```text
direct  = base + (kaynak uzunluğu × ratio)
reverse = base - (kaynak uzunluğu × ratio)
map     = minimum + ((kaynak uzunluğu - source-minimum) / (source-maximum - source-minimum)) × (maximum - minimum)
sonuç   = yuvarlanır ve yapılandırılan iki çıktı uç değeri arasında sınırlandırılır
```

| Ayar | Değer / davranış |
| --- | --- |
| `calculation.mode` | `direct` artırır, `reverse` azaltır, `map` ise kaynak aralığını sonuç aralığına doğrusal eşler. `inverse`, `reverse` için geriye uyumlu alias olarak kalır. |
| `calculation.ratio` | `direct`/`reverse` tarafından kullanılan negatif olmayan oran; `map` modunda yok sayılır. |
| `calculation.base` | `direct`/`reverse` başlangıç değeri; `map` modunda yok sayılır. Varsayılan direct için `minimum`, reverse için `maximum` değeridir. |
| `calculation.minimum` / `maximum` | Her biri bağımsız olarak `-4096..4096` ile sınırlı çıktı uç değerleridir. `direct`/`reverse` için `minimum <= maximum` zorunludur. `map` modunda iki sıra da geçerlidir: `minimum: 100`, `maximum: -100` azalan/tersinir eşleme oluşturur. |
| `calculation.source-minimum` / `source-maximum` | Yalnızca `map` için giriş karakter aralığı; varsayılan `0` ve `source-options.max-characters`. Alt kaynak ucu, sayısal sıralamadan bağımsız biçimde `minimum` değerine; üst kaynak ucu `maximum` değerine eşlenir. |
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
  calculation: { mode: "reverse", ratio: 1, base: 16, minimum: 0, maximum: 16 }
  result:
    type: "template"
    value: "•"
    template: "{source}: {length} karakter / {count} birim"

# Azalan eşleme: 0 karakter -> 100, 10 -> 0, 20 ve üzeri -> -100
adaptive_map_sayi:
  type: "adaptive"
  source: "%player_name%"
  calculation:
    mode: "map"
    source-minimum: 0
    source-maximum: 20
    minimum: 100
    maximum: -100
    rounding: "nearest"
  result: { type: "number" }
```

Adaptif sonuçlar literal olarak döndürülür ve PlaceholderAPI tarafından ikinci kez ayrıştırılmaz. Negatif değerler `number` ve template içindeki `{count}` çıktısında korunur. Metin negatif sayıda tekrar edilemeyeceği için hesap sıfır veya negatifse `repeat` boş metin döndürür. Böylece `%` içeren bir simgenin yanlışlıkla yeni bir placeholder zincirine dönüşmesi engellenir ve işlem yükü sınırlı kalır. Geçersiz aralık, sayı veya limit içeren bir reload güvenli biçimde reddedilir; son geçerli yapılandırma çalışmaya devam eder.

#### `wiki.yml`

Split, `plugins/Split/wiki.yml` konumunda eksiksiz İngilizce/Türkçe eğitim dosyası oluşturur. Dosyanın her satırı yorumdur; bu nedenle geçerli fakat boş bir YAML'dır ve hiçbir ayar geçerliliği yoktur. Rehber tüm placeholder tiplerini, ayarları, alias'ları, formülleri, sınırları, komutları ve sorun giderme akışlarını açıklar. Split bu dosyayı yapılandırma olarak okumaz. Dosya silinirse, düzenlenirse veya plugin güncellemesinden sonra eski kalırsa açılışta ya da `/split reload` sırasında güncel paket kopyasıyla değiştirilir.
