# Остаточний аудит: settings IA, persistence та VFX accessibility

Дата: **2026-11-10**.

**Головна рекомендація:** відокремити персональні preferences від адміністрування арени, мати одне джерело персональних налаштувань на клієнті й забезпечити передбачуване вимкнення екранних спотворень.

Аудит виконано в режимі **READ-ONLY**: під час аудиту файлів не змінював, реалізацію не починав, scripts/tests/docs не створював. Цей звіт збережено окремо після прямого дозволу користувача. Висновки перевірено за live source. Minecraft `26.3`, Java `25`.

Усі рекомендовані зміни, нові файли та інтеграційні контракти нижче — **proposed, не implemented**. Зокрема, контракти arena wizard пункту 6 описують погоджений напрям інтеграції, а не реалізовану цим аудитом функціональність.

## 1. Як система працює зараз

### Різні settings мають різний життєвий цикл

| Налаштування | Де зберігається | Reconnect | Перезапуск Minecraft |
|---|---|---|---|
| Accent theme | Статичні поля `UiTheme` | Зберігається в процесі клієнта | Скидається до CYAN |
| Density/noise/distortion | `config/flake-client.properties` | Зберігаються | Завантажуються з файла |
| Effects On/Off | Серверний `HashSet<UUID> disabled` | **Скидається до On** | Не є клієнтською preference |
| Арена | `<world>/flake/arena.properties` | Збережений конфіг залишається | Належить світу, не гравцю |

### Що насправді робить Effects Off

`src/main/java/dev/flake/duel/ShockwaveVisuals.java:248–255`:

1. Додає UUID гравця до `disabled`.
2. Надсилає клієнту фазу `CLEAR`.
3. Прибирає активний post-effect, якщо гравець був у `active`.
4. Подальші відповідні VFX-пакети фільтруються для цього гравця.

Клієнтський `CLEAR` очищає waves, impact і uniform-масив:

`src/client/java/dev/flake/client/ShockwaveRenderState.java:47–48, 98–103`.

Це **не вимикає здібності, damage, knockback, cooldown чи бафи**. Також не вимикає звуки: наприклад, `charge()` та `play()` відтворюють їх до фільтрації доставки VFX — `ShockwaveVisuals.java:52–55, 69–74`.

Off **не скидає** density/noise/distortion або accent. Окремої кнопки відновлення персональних defaults у UI немає.

### Чому reconnect повертає ефекти

`ShockwaveVisuals.java:40–48` видаляє UUID із `disabled` на disconnect і очищає набір при зупинці сервера. `isEnabled()` у `:244–245` повертає `true`, якщо UUID відсутній.

Отже, reconnect не «завантажує неправильний файл»: **збереження цього вибору взагалі немає**.

---

## 2. Підтверджені дефекти

Позначення: **P2 — помітна функціональна проблема; P3 — нижчий пріоритет.** Критичних обходів прав у перевіреній зоні не виявлено.

### S1. Частина settings недоступна на звичайному малому GUI — P2

**Джерело:**
`src/client/java/dev/flake/client/FlakeScreen.java:38–39, 137–149`.

**Trigger:** відкрити settings при логічній висоті GUI `240`, наприклад `320×240`.

**Поведінка:** останній control закінчується на `y + 302`. При `height=240`: `h=224`, `y=8`; distortion займає `290…310`, повністю поза екраном.

**Impact:** налаштування неможливо змінити видимим control. Немає scrolling або responsive-перенесення.

Це статично доведений overflow, а не припущення зі screenshot.

### S2. Effects Off губиться після reconnect — P2

**Джерело:**
`src/main/java/dev/flake/duel/ShockwaveVisuals.java:33, 40–48, 244–255`.

**Trigger:** вимкнути ефекти → від’єднатися → зайти повторно.

**Impact:** ефекти знову дозволені без дії користувача. Для accessibility це особливо небажано: escape hatch не є постійним.

### S3. Увімкнення VFX не відновлює вже активний buff visual — P2

**Джерело:**

- `src/main/java/dev/flake/duel/ShockwaveVisuals.java:167–177, 248–249`;
- `src/main/java/dev/flake/duel/Matadora.java:80–94, 104–113`;
- `src/main/java/dev/flake/duel/CrystalClaw.java:62–72, 83–90`.

**Trigger:** активація Матадори/Crystal Claw відбулася при вимкнених VFX, або користувач вимкнув і знову ввімкнув VFX протягом бафа.

**Поведінка:** Off очищає клієнтський стан; On лише прибирає UUID із `disabled`. Поточні buff states не надсилаються повторно. `MASK_ON`/`CRYSTAL_ON` надсилаються при активації, не регулярно протягом бафа.

**Impact:** UI показує «ефекти увімкнено», але чинна аура/оверлей не повертається. Сам gameplay-баф продовжує працювати правильно.

Відновлювати слід **стан із залишковою тривалістю**, а не повторно програвати activation burst.

### S4. Theme не переживає перезапуск клієнта — P3

**Джерело:**

- `src/client/java/dev/flake/client/ui/UiTheme.java:10–11, 41–44`;
- `src/client/java/dev/flake/client/FlakeScreen.java:137–138`.

**Trigger:** вибрати accent → перезапустити Minecraft.

**Impact:** вибір повертається до CYAN. На звичайному reconnect тема залишається, доки працює той самий процес клієнта.

### S5. UI називає спільні controls налаштуваннями Матадори — P3

**Джерело:**

- `src/main/resources/assets/flake/lang/uk_ua.json:331`;
- `src/client/java/dev/flake/client/ShockwaveRenderState.java:230–276`;
- `src/client/resources/assets/flake/shaders/post/shockwave.fsh:1254–1273`.

**Trigger:** змінити density/noise/distortion, потім активувати Crystal Claw.

**Поведінка:** renderer передає ті самі три параметри для власного оверлею обох предметів.

**Impact:** користувач не бачить справжньої області впливу. Немає потреби автоматично створювати два набори settings: спільний control доречний, але має бути чесно названий.

### S6. Distortion `0%` не прибирає весь екранний зсув оверлею — P2

**Джерело:**
`src/client/resources/assets/flake/shaders/post/shockwave.fsh:1336, 1339–1342`.

**Trigger:** distortion `0%`, vanilla screen-effect scale понад нуль, активація Матадори/Crystal Claw.

**Поведінка:** параметр множить плавний warp. Activation RGB split від нього не залежить.

**Impact:** нуль не є надійним способом вимкнути всі спотворення цього оверлею.

Можливі два чесні виправлення:

- вузьке: перейменувати control на «Плавна деформація»;
- рекомендоване: визначити «Спотворення» як warp **і** RGB separation та забезпечити нуль для обох.

### S7. I/O-помилки приховуються; UI не відрізняє застосоване від збереженого — P3

**Джерело:**
`src/client/java/dev/flake/client/MaskSettings.java:16–21, 31–36`.

**Trigger:** config недоступний для читання/запису, помилка диска або інша `IOException`.

**Impact:** користувач змінює значення, але не знає, що воно діє лише в пам’яті. Після restart воно може зникнути.

Запис також неатомарний: `newOutputStream()` перезаписує цільовий файл без тимчасового файла та atomic replace. Збій запису може пошкодити попередній конфіг.

### S8. Невалідне число перериває завантаження інших незалежних settings — P3

**Джерело:**
`src/client/java/dev/flake/client/MaskSettings.java:19–24`.

**Trigger:** наприклад, валідний `mask.density`, нечисловий `mask.noise`, валідний `mask.distortion`.

**Поведінка:** density вже присвоєно; noise кидає `NumberFormatException`; distortion не читається.

**Impact:** застосовується частково завантажений стан. Помилка одного поля мовчки заважає іншому незалежному полю.

### S9. Пошкоджений Unicode escape у properties не обробляється — P2, robustness

**Джерело:**

- `src/client/java/dev/flake/client/MaskSettings.java:18–21`;
- виклик під час initialization: `src/client/java/dev/flake/client/FlakeClient.java:26`.

**Trigger:** некоректна escape-послідовність `\u…` у properties.

**Поведінка:** `Properties.load()` може кинути `IllegalArgumentException`; поточний catch ловить `IOException | NumberFormatException`, але не цей випадок.

**Impact:** виняток виходить із client initializer замість безпечного fallback. Це висновок за кодом і контрактом Java API; запуск із пошкодженим файлом не виконувався.

### S10. За відсутнього HUD UI стверджує «ефекти увімкнено» — P3

**Джерело:**
`src/client/java/dev/flake/client/FlakeScreen.java:140–143`.

**Trigger:** HUD state ще не отримано або сервер не підтримує відповідний стан.

**Поведінка:** `hud == null` трактується як On. Свіжість HUD тут також не перевіряється.

**Impact:** невідомий стан видається за підтверджений. Потрібен стан очікування/недоступної синхронізації, а не вигаданий On.

---

## 3. Defaults, overrides та межі нинішньої поведінки

### Defaults і parsing

`MaskSettings.java:10–24`:

- defaults усіх трьох полів — `100`;
- відсутнє поле читається як `100`;
- числа обмежуються до `0…200`;
- файл може містити проміжне значення, наприклад `75`;
- UI пропонує лише кроки `0, 50, 100, 150, 200`;
- після `200` наступний клік повертає `0`;
- відсутній файл на startup залишає початкові defaults;
- помилки не показуються користувачу.

`load()` не є функцією reset: за відсутнього файла просто повертається, не присвоюючи defaults повторно.

### Не всі accessibility/quality controls відсутні

Уже є інтеграція з vanilla:

- particle quality → деталізація VFX:
  `ShockwaveRenderState.java:120–126`;
- screen-effect scale → impact і buff overlay scale:
  `:127–133, 258`;
- screen-effect scale використовується також Ghoul renderer:
  `src/client/java/dev/flake/client/GhoulRenderState.java:112`.

Тому не варто створювати дублікати кожної vanilla опції.

### Що означають «duplicate overrides»

**Нині немає двох незалежних persistence-систем для одного accent або mask-поля.** Є інша проблема: різні налаштування розкидані між статичним клієнтським станом, properties і серверним toggle.

Під час реалізації потрібно уникнути чотирьох нових конфліктів:

1. **Новий preferences store проти старого `MaskSettings.save()`.**
   Зараз `save()` створює нові `Properties` лише з трьома mask keys. Якщо просто додати theme/effects keys у той самий файл іншим writer, старий save їх видалятиме.

2. **Локальне бажання Off проти серверного default On після reconnect.**
   Початковий HUD не повинен перезаписувати збережену клієнтську preference до handshake.

3. **Vanilla accessibility проти нового Flake intensity.**
   Новий control не має непомітно підсилювати ефекти понад vanilla обмеження або двічі множити ту саму величину.

4. **Comfort preset проти ручних значень.**
   Не слід мовчки перезаписувати користувацькі sliders при кожному ввімкненні preset. Зберігати базові значення й явно показувати ефективні обмеження.

---

## 4. UX gaps — не всі є bugs

- Персональні preferences змішані з глобальною конфігурацією арени.
- Немає selected-state поточного accent.
- Циклічні числові кнопки приховують напрямок зміни й wrap-around.
- Немає опису різниці між тонуванням, шумом і спотворенням.
- Немає scoped reset, статусу збереження чи безпечного preview.
- Підпис «Ефекти» не пояснює, що toggle не вимикає gameplay та звуки.
- Немає pending feedback для серверного toggle; повторні кліки надсилають повторні інверсії.
- Немає явного способу прибрати екранні спотворення, залишивши просторові бойові VFX.
- Довгі назви кнопок обрізаються без повного пояснення:
  `src/client/java/dev/flake/client/ui/UiButton.java:52–55`.

Водночас **відсотки вже є** в локалізаціях, а `UiButton` уже підтримує keyboard activation, focus outline та narration (`:42, 65–76`). Це варто зберегти.

---

## 5. Рекомендований дизайн

### 5.1. Settings IA

**Особисті налаштування**

Підпис: **«Лише на цьому клієнті. Не змінює правила бою чи конфігурацію сервера».**

1. **Вигляд**
   - Accent.
   - Невелика preview-картка.
   - Reset групи.

2. **VFX і комфорт**
   - Master VFX.
   - Comfort mode.
   - Пояснення використання vanilla accessibility/particle settings.
   - Advanced: спільні buff overlays.
   - Reset групи.

**Арена сервера**

- Read-only status/blockers із пункту 6.
- За відповідною capability — **«Налаштувати арену»**, що відкриває **єдиний wizard пункту 6**.
- Не дублювати `corner/spawn/save` controls у personal settings.

Особисті settings залишаються доступними під час матчу та не блокують Ready.

### 5.2. Controls і defaults

| Control | Рекомендація | Default |
|---|---|---|
| Accent | Шість radio/swatches, назва + ✓ selected | CYAN |
| «Власні VFX Flake» | Toggle з явним On/Off | On для нового профілю |
| «Зменшені екранні ефекти» | Toggle, пояснений effective state | Off |
| «Тонування та затемнення» | Slider/stepper `0…200%` | 100% |
| «Шум оверлею» | Slider/stepper `0…200%` | 100% |
| «Спотворення екрана» | Slider/stepper `0…200%` | 100% |
| Деталізація | Спочатку read-only «Використовує Minecraft → Частинки» | Vanilla |
| Reset | Окремо для Appearance та VFX | Документовані defaults |

Доцільні кроки sliders — `5–10%`, із точним озвученням значення. Збережені проміжні значення не потрібно без причини округляти.

Збереження чинних defaults зменшує ризик зламати відомий вигляд. Новий quality preset або окремі settings для кожного предмета — **не обов’язкові**.

### 5.3. Shared mask controls

Назва групи:

**«Власні оверлеї бафів — Матадора та Crystal Claw».**

Пояснення: controls змінюють власне екранне оформлення, а не силу бафа й не preferences інших гравців.

Не називати density `0%` загальним «Overlay Off»: зараз density відповідає лише частині оформлення, тоді як noise/warp мають окремі параметри.

### 5.4. Zero-distortion semantics

Рекомендований контракт:

> **«Спотворення 0%» означає відсутність sample-coordinate warp та RGB separation власного buff overlay, включно з активацією.**

При цьому можуть залишатися:

- статичне тонування відповідно до окремого control;
- шум відповідно до окремого control;
- просторові аури й геометрія;
- HUD status.

Це не глобальна обіцянка «жодного руху від будь-якого ефекту». Для повного вимкнення цієї сім’ї VFX є master Off; для комфортного комплексного обмеження — comfort mode.

### 5.5. Comfort mode

Мінімальний корисний режим:

- власний screen warp — нуль;
- activation RGB split — нуль;
- animated overlay noise — нуль;
- декоративні UI hover/press transitions — миттєві або суттєво скорочені;
- gameplay status, cooldown і важливі просторові телеграфи залишаються;
- не змінюються серверні таймінги, damage або cooldown.

Початкові sliders зберігати. У UI показувати, наприклад:

**«Шум: ефективно 0% — обмежено режимом комфорту».**

Не називати цей режим медичною гарантією безпеки. Повне Off має залишатися легко доступним.

### 5.6. Persistence і синхронізація

**Рекомендоване джерело персонального бажання — один локальний preferences model.**

- Спільний versioned store для accent, effectsEnabled, comfort і overlay parameters.
- Завантаження до обробки VFX.
- Parsing кожного поля незалежно.
- Unknown enum → default лише цього поля.
- Некоректний файл → повідомлення та безпечний fallback без падіння initialization.
- Атомарне збереження.
- Єдиний writer.
- Save на commit slider, не щокадрово.
- При помилці: **«Застосовано до цієї сесії; не вдалося зберегти»**.

Серверний enabled-state — **поточна доставка VFX**, а не друга незалежна персональна preference.

На reconnect:

1. Клієнт одразу поважає локальний Off.
2. Надсилає точне `setEnabled(false)`, не toggle.
3. Сервер підтверджує застосований стан.
4. Початковий default/HUD не перезаписує бажане значення.

Для `/flake effects`:

- зберегти compatibility toggle;
- додати явні `on|off`;
- зміна через команду має узгоджуватися з локальним preferences model;
- відрізняти unsolicited початковий стан від підтвердженої зміни користувача.

### 5.7. Preview і reset

**Обов’язковий дешевий preview:** theme-картка з кнопкою, selected-state та success/error labels.

**Необов’язковий наступний крок:** короткий opt-in VFX preview. Без реального cast, dummy, damage або server action; без autoplay і нових постійних fullscreen passes.

Reset:

- лише поточна група;
- не зачіпає арену;
- не змінює vanilla options;
- показує, які defaults буде відновлено.

### 5.8. Admin capabilities та інтеграція з пунктом 6

Підтверджені arena-проблеми:

- active admin actions для non-op:
  `FlakeScreen.java:122–135`;
- серверний permission працює:
  `ArenaCommands.java:22–34`;
- поточна чернетка спільна:
  `ArenaCommands.java:19, 37–38`;
- поточні payload-и не містять структурованого arena/capability state.

**Lobby owner ≠ arena admin.** Право на lobby actions не надає права редагувати арену світу.

Settings споживає capabilities, blockers і результати wizard пункту 6. Його per-player drafts, revisions, conflict handling, world-return flow та preflight тут не дублюються.

Спільна модель мережевих помилок:

**typed reason code → локалізація на клієнті → correlated `ActionResult`.**

Локальні config errors використовують аналогічні типізовані результати, але без мережевого round-trip.

---

## 6. Конкретні implementation batches

Усе нижче — **план, не виконані зміни**.

### Batch A — preferences model і надійне сховище

**Existing:**

- `src/client/java/dev/flake/client/MaskSettings.java`
- `src/client/java/dev/flake/client/ui/UiTheme.java`
- `src/client/java/dev/flake/client/FlakeClient.java`

**Proposed new files:**

- `src/client/java/dev/flake/client/config/ClientPreferences.java`
- `src/client/java/dev/flake/client/config/ClientPreferencesStore.java`

Задачі:

- один model/defaults/schema;
- незалежний parsing, atomic save, typed load/save results;
- міграція чинних `mask.*`;
- persistence accent і master Off;
- `MaskSettings` або прибрати після перенесення всіх references, або залишити тимчасовим read-only compatibility facade — **не другим writer**.

### Batch B — personal settings UI

**Existing:**

- `src/client/java/dev/flake/client/FlakeScreen.java`
- `src/client/java/dev/flake/client/ui/UiButton.java`
- `src/main/resources/assets/flake/lang/uk_ua.json`
- `src/main/resources/assets/flake/lang/en_us.json`
- `src/main/resources/assets/flake/lang/ru_ru.json`

**Proposed new file:**

- `src/client/java/dev/flake/client/ui/PersonalSettingsScreen.java`

Задачі:

- Appearance / VFX і комфорт;
- scroll viewport, адаптивна ширина, незмінно доступний Back;
- selected accents, sliders, scoped reset;
- save/error feedback;
- збереження keyboard focus при зміні значення;
- arena entry до wizard пункту 6.

Окремий screen корисний для меж відповідальності, але не є вимогою створювати велику UI-framework.

### Batch C — idempotent VFX preference sync

**Existing:**

- `src/main/java/dev/flake/duel/ShockwaveVisuals.java`
- `src/main/java/dev/flake/build/LoadoutCommands.java`
- `src/client/java/dev/flake/client/FlakeClient.java`
- `src/client/java/dev/flake/client/ui/FlakeUiClient.java`

**Proposed new files:**

- `src/main/java/dev/flake/ui/VisualPreferencesPayload.java`
- `src/main/java/dev/flake/ui/VisualPreferencesStatePayload.java`

Назви попередні; correlated `ActionResult` слід використати спільний із погодженого integration contract, а не створювати ще одну несумісну систему.

Задачі:

- explicit `setEnabled`;
- desired/pending/applied states;
- reconnect handshake;
- command/UI convergence;
- відновлення чинних buff visuals із залишковим часом без повторної активації.

Не додавати preferences до високочастотного HUD broadcast лише заради синхронізації settings.

### Batch D — zero-distortion і comfort rendering

**Existing:**

- `src/client/java/dev/flake/client/ShockwaveRenderState.java`
- `src/client/java/dev/flake/client/GhoulRenderState.java`
- `src/client/resources/assets/flake/shaders/post/shockwave.fsh`
- `src/client/java/dev/flake/client/ui/UiButton.java`
- `src/client/java/dev/flake/client/mixin/PostPassMixin.java`

Задачі:

- єдине обчислення effective comfort parameters;
- чітке охоплення warp/RGB split;
- vanilla scale як чинне accessibility-обмеження;
- не змінювати добрі просторові анімації без потреби;
- не додавати passes, raymarch або новий particle pipeline;
- перевірити, що Off не залишає непотрібний активний post-effect.

### Batch E — інтеграція з arena wizard

**Existing:**

- `src/client/java/dev/flake/client/FlakeScreen.java`
- `src/client/java/dev/flake/client/ui/FlakeUiClient.java`
- `src/main/java/dev/flake/ui/FlakeUiServer.java`

Задачі лише цієї зони:

- read-only arena summary;
- capability-aware entry;
- збереження return context;
- показ typed errors/results із пункту 6.

Не створювати альтернативний arena settings backend.

---

## 7. Acceptance scenarios

### Persistence та defaults

1. Перший запуск без config: CYAN, VFX On, comfort Off, overlay fields `100%`.
2. Старий файл лише з `mask.*` мігрує без втрати значень.
3. Accent і VFX Off переживають restart клієнта.
4. Off переживає reconnect і restart сервера через повторну синхронізацію.
5. Невалідне одне поле не скидає решту валідних.
6. Невідомий accent і некоректний Unicode escape не зривають initialization.
7. Unwritable config: зміна працює в сесії, save failure видимий.
8. Зміна mask slider не видаляє theme/effects keys.
9. Reset однієї групи не зачіпає іншу, арену чи vanilla options.

### Синхронізація

10. Збережений Off діє до приходу першого HUD/VFX packet.
11. Повторна доставка `setEnabled(false)` не вмикає ефекти.
12. Швидкі кліки не створюють неправильний результат через гонку toggle.
13. UI та `/flake effects on|off` сходяться до одного бажаного стану.
14. Невідомий/непідтверджений server state не показується як підтверджений On.
15. Off → On під час бафа відновлює visual state із правильним залишком, без нового burst або продовження бафа.

### Rendering/accessibility

16. Distortion `0%`: немає buff-overlay warp і RGB split при активації та plateau.
17. Noise `0%`: немає відповідного overlay noise; це не змінює damage.
18. Shared controls однаково й явно впливають на власні Матадора/Crystal Claw overlays.
19. Comfort mode прибирає погоджені екранні ефекти, але залишає HUD statuses і gameplay timing.
20. Vanilla screen-effect scale `0%` поважається.
21. Vanilla particle quality продовжує керувати деталізацією без другого суперечливого override.
22. Off не впливає на damage, knockback, cooldown, eligibility та інших гравців.
23. Preview не autoplay, не активує здібність і не змінює світ.

### UI та інтеграція

24. GUI `320×240`, `480×270` і велике вікно: всі controls доступні.
25. Повний шлях виконується клавіатурою; focus не губиться після кожного кроку slider.
26. Narration повідомляє назву, значення, selected/disabled state.
27. Вибір і помилки позначені не лише кольором.
28. Non-op і lobby owner без arena capability не бачать активних admin mutations.
29. Вхід до arena management веде до wizard пункту 6 й повертає в попередній контекст.
30. Зміна permissions обробляється сервером і показується через correlated result.

### Перевірки після дозволу на реалізацію

- Focused unit tests parsing/migration/save і effective comfort parameters.
- Payload round-trip та correlated action scenarios.
- Відповідні Gradle compile tasks для common/client.
- Shader compilation/validation.
- Runtime-перевірка GUI, narration, reconnect і активних бафів.
- GPU-перевірка відсутності зайвих passes та постійного post-effect у Off.

## Межі аудиту

Фактично виконано **scoped читання та статичний аналіз**. Gradle, Minecraft client, shader compilation, screen reader і GPU measurements цим аудитором не запускалися. Скриншотів та benchmark claims немає. Перевірки основного агента не присвоюються цьому аудиту.

Overflow і lifecycle/persistence висновки підтверджені кодом. Контраст, комфорт анімацій, фактичний focus traversal і GPU-вартість потребують runtime-перевірки після реалізації.
