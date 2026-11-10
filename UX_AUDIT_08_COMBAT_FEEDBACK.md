# Пункт 8 — бойовий UX, feedback, HUD і відповідність описів

Дата аудиту: **2026-11-10**.

**Read-only аудит завершено. Під час аудиту файлів не змінював, фіксів, scripts/tests/docs не створював.** Цей файл збережено згодом за прямим дорученням користувача; source/gameplay/UI не змінювалися.

Перевірено відповідні розділи `.kilo/context/repo-map.md`, `architecture.md`, live implementation та scoped `AGENTS.md` — вкладених правил немає. Build підтверджує Minecraft **26.3**, Java **25**, Fabric API **0.165.2+26.3**.

**Головний висновок:** найбільша проблема — не нестача ефектів, а відсутність єдиного достовірного бойового стану. HUD частково використовує серверні дані, частково базові metadata й клієнтські припущення. Через це відображення може суперечити реальним cooldown, buff і результатам дії.

Пріоритети:
- **P1** — істотне втручання в гру або сторонніх гравців.
- **P2** — неправильна бойова інформація чи незрозуміла відмова.
- **P3** — локальна проблема зрозумілості/доступності.

Усі запропоновані нижче контракти й пакети реалізації — **проєктування, не виконані зміни**. Зокрема, shared action-result envelope, узгоджений із пунктом 6, — **proposed, не implemented**.

## 1. Підтверджені дефекти

### 1.1. P1 — бойовий HUD активується стороннім гравцям

**Джерела:**
- `src/main/java/dev/flake/ui/FlakeUiServer.java:95–101,159–164` — HUD розсилається всім із глобальною фазою матчу.
- `src/client/java/dev/flake/client/ui/FlakePlayerHud.java:21–30` — заміна ванільного HUD залежить лише від `COUNTDOWN/FIGHTING`, не від участі.
- `src/client/java/dev/flake/client/FlakeClient.java:29–31` — через цей самий predicate закривається інвентар.

**Тригер:** двоє починають дуель; третій гравець перебуває поза нею.

**Вплив:** сторонній отримує бойову заміну hotbar/health/food/XP, а його інвентар закривається. Інформація про чужий матч помилково перетворюється на власний бойовий режим.

**Напрям:** серверний `viewerRole = PARTICIPANT | SPECTATOR | NONE`; лише participant активує повну бойову заміну. Spectator може мати окрему match bar без втручання у власний інвентар/HUD.

---

### 1.2. P2 — неправильний timing заряджання дисонансу та землетрусу

**Джерела:**
- `src/main/java/dev/flake/ability/AbilitySlots.java:12–13`: active duration дисонансу **0 ticks**, землетрусу **100 ticks**.
- `src/main/java/dev/flake/duel/Shockwave.java:10`: справжній charge **22 ticks**.
- `src/main/java/dev/flake/duel/ElectricQuake.java:24`: справжнє поширення **10 ticks**.
- `src/client/java/dev/flake/client/ui/FlakeHudOverlay.java:243,252,269–273`: overlay використовує enum durations, а не timing відповідної серверної фази.

**Тригер:** застосувати H/R Контуженого.

**Вплив:**
- Дисонанс протягом заряджання показує приблизно **«0.0 с»** та неправильний прогрес.
- Землетрус отримує відлік від приблизно **5 с**, який обривається після реального завершення за ~0.5 с.

**Напрям:** окремі `CHARGING` і `PROPAGATING/ACTIVE`, з фактичними duration/remaining із сервера. Не дублювати timing у renderer.

---

### 1.3. P2 — modified cooldown має неправильний denominator; молот — ще й неправильні секунди

**Джерела:**
- `src/main/java/dev/flake/duel/Cooldowns.java:10–18`: Аметист множить cooldown на **0.8**.
- `src/main/java/dev/flake/ability/ClassSkills.java:100` — застосовує modifier.
- `src/main/java/dev/flake/build/LoadoutCommands.java:395–396,485–486` — те саме для Контуженого.
- `src/main/java/dev/flake/ui/FlakeUiServer.java:151–158` — total береться базовий.
- `src/client/java/dev/flake/client/ui/FlakeHudOverlay.java:252,277` — renderer також використовує базовий total.
- `src/main/java/dev/flake/duel/ControlHammer.java:117–119` — молот отримує фактичний скорочений cooldown.
- `src/client/java/dev/flake/client/ui/FlakeItemHud.java:40–49` — percentage множиться на базовий total registry.

**Тригер:** використовувати Плавлячий аметист у бою.

**Вплив:**
- Для класових здібностей remaining правильний, але смуга стартує приблизно з **20% готовності**.
- Для молота одразу показується приблизно **10 с замість фактичних 8 с**; подальші секунди теж завищені.

**Напрям:** передавати duration конкретного розпочатого cooldown. Не переобчислювати старий cooldown із поточного білда.

---

### 1.4. P2 — Blood Lunge мовчки відмовляє; блокування не має причини

**Джерела:**
- `src/main/java/dev/flake/ability/ClassSkills.java:138–139,99` — немає цілі → `false` → `0`.
- `src/main/java/dev/flake/ability/ClassSkills.java:88–89` — blocked/cooldown також повертають `0`.
- `src/main/java/dev/flake/build/LoadoutCommands.java:409` — dead/spectator/blocked відхиляються без відповіді.
- `src/main/java/dev/flake/ui/HudStatePayload.java:34,215` — тільки `abilityBlocked`.
- `src/main/resources/assets/flake/lang/uk_ua.json:278` — blocked названо **«Очікування»**.

**Тригер:** Lunge без валідної видимої цілі; натискання під stun або під час cooldown.

**Вплив:** гравець не розуміє, чи це проблема наведення, дальності, затримки мережі або стану персонажа. Stun подається як безпричинне «Очікування».

**Зберегти:** failed target cast зараз не витрачає cooldown.

**Напрям:** типізований бойовий `CastResult`, сумісний зі спільною моделлю action results основного агента.

---

### 1.5. P2 — описи Упиря не відповідають механіці

**Джерела:**
- `src/main/resources/assets/flake/lang/uk_ua.json:33–34`;
- `src/main/resources/assets/flake/lang/en_us.json:33–34`;
- `src/main/resources/assets/flake/lang/ru_ru.json:33–34`.
- `src/main/java/dev/flake/ability/ClassSkills.java:138,150`.

**Розходження:**
- Lunge описаний як **5.5 м**, але вибір цілі допускає **9 м**.
- Bloodlust обіцяє **Speed II**, реально amplifier **0**, тобто **Speed I**.

**Тригер:** читання опису й подальше використання здібності.

**Вплив:** неправильне очікування дистанції та сили buff.

**Уточнення:** 9 м — дальність вибору цілі, не гарантована довжина переміщення. Ривок реалізовано velocity, тому не слід замінювати текст на безумовне «ривок на 9 м».

---

### 1.6. P2 — опис і metadata міни застарілі

**Джерела:**
- `src/main/resources/assets/flake/lang/{uk_ua,en_us,ru_ru}.json:40` — **2.4 м, 12 с**.
- `src/main/java/dev/flake/ability/ClassSkills.java:159` — arming через **20 ticks**, expiry через **400 ticks**.
- `src/main/java/dev/flake/ability/ClassSkills.java:279` — trigger radius **1.7 м**.
- `src/main/java/dev/flake/ability/AbilitySlots.java:18` — metadata duration **240 ticks**.

**Тригер:** планування пастки за описом.

**Вплив:** гравець неправильно оцінює радіус і час існування.

**Факти:** озброєння **1 с**, життя **20 с**, радіус спрацювання **1.7 м**. Не змінювати баланс під старий опис без окремого рішення.

---

### 1.7. P2 — ванільні status bars завжди повні; частина ефектів зникає

**Джерела:**
- `src/main/java/dev/flake/ui/FlakeUiServer.java:194–199` — infinite effects пропускаються; для решти `totalTicks = remaining`.
- `src/client/java/dev/flake/client/ui/StatusHud.java:87` — progress дорівнює `remaining/total`.
- `src/client/java/dev/flake/client/ui/mixin/EffectIconsMixin.java:14–16` — custom Status HUD приховує всі ванільні effect icons.
- `src/main/java/dev/flake/ui/HudStatePayload.java:237` — amplifier відсутній.

**Тригер:** короткий ефект одночасно з постійним пасивним ефектом.

**Вплив:**
- Смуга короткого ефекту на кожному snapshot знову має **100%**.
- Постійний ефект відсутній у custom list, але його ванільна іконка теж приховується.
- Speed I/II, Resistance I/II тощо не можна відрізнити.

Додатково є cap **12** без overflow-позначки та гарантованого пріоритету небезпечних станів.

---

### 1.8. P2 — після stun buff HUD суперечить залишковій механіці

**Джерела:**
- `src/main/java/dev/flake/ability/ClassSkills.java:124–130,150–151` — початкові vanilla effects та окремі buff maps.
- `src/main/java/dev/flake/ability/ClassSkills.java:257–270` — stun прибирає buff maps і надсилає OFF, але не прибирає раніше видані vanilla effects.

**Тригер:** оглушення під Bulwark/Bloodlust.

**Вплив:**
- Bloodlust втрачає lifesteal/status/ауру, але Speed продовжує діяти.
- Bulwark втрачає іменний стан/ауру та KB resistance, проте видані Resistance/Slowness/Absorption не очищаються цим завершенням.

Це **підтверджена неузгодженість**, але правильна політика не очевидна: stun має завершувати весь buff чи лише забороняти нові дії? Потрібне gameplay-рішення, а не прихований cosmetic fix.

---

### 1.9. P2 — item HUD показує buff, який уже завершився

**Джерела:**
- `src/client/java/dev/flake/client/ui/FlakeItemHud.java:44–46` — buff вираховується з cooldown percentage.
- `src/main/java/dev/flake/duel/Matadora.java:109–112` — buff може завершитися достроково через stun.
- `src/main/java/dev/flake/duel/CrystalClaw.java:88–90` — аналогічно.

**Тригер:** застосувати Матадору/Кіготь і отримати stun у перші п’ять секунд.

**Вплив:** реальний buff уже прибрано, але hotbar ще показує його «активний» таймер.

**Напрям:** cooldown та buff — незалежні серверні стани. Один не є доказом іншого.

---

### 1.10. P2 — Паладин рахує спроби атаки, не підтверджені влучання

**Джерела:**
- `src/main/java/dev/flake/duel/VoidPaladin.java:89–95,141–150` — лічильник змінюється в `AttackEntityCallback`.
- `src/main/java/dev/flake/duel/VoidPaladin.java:98–105` — crit feedback, на відміну від лічильника, чекає підтвердження шкоди.
- `src/main/resources/assets/flake/lang/uk_ua.json:103` — «кожен 4-й удар».

**Тригер:** атакувати валідну ціль, але отримати блок або відмову шкоди, зокрема зарядженою четвертою атакою.

**Вплив:** заряд може витратитися без підтвердженого криту; формулювання не пояснює правило.

**Напрям:** окремо погодити «четверта спроба» чи «четверте підтверджене влучання». UI має відповідати обраному правилу.

---

### 1.11. P2 — Шипи мають незадокументований minimum damage

**Джерела:**
- `src/main/java/dev/flake/ability/ClassSkills.java:315` — `max(0.5, taken * 0.2)`.
- `src/main/resources/assets/flake/lang/uk_ua.json:134` — просто **20%**.

**Тригер:** отримати від суперника менше **2.5 HP** шкоди.

**Вплив:** повертається більше заявлених 20%; наприклад, з 1 HP — 0.5 HP.

**Напрям:** описати мінімум або окремо погодити зміну механіки.

---

### 1.12. P3 — «предмет використано» пропускає останній витрачений предмет

**Джерела:**
- `src/client/java/dev/flake/client/ui/FlakeItemHud.java:95–105` — порожній stack пропускається до порівняння зі старим count.

**Тригер:** використати останнє яблуко/перлину/зілля у stack.

**Вплив:** перехід `1 → 0` не потрапляє у feed. Натомість саме зменшення count не доводить використання: зменшення stack іншою дією також може виглядати як use.

**Напрям:** feed будувати з підтвердженої події використання, не зі знімків інвентаря.

---

### 1.13. P3 — hardcoded hotbar keys і застарілі переклади статусів

**Клавіші:**
- `src/client/java/dev/flake/client/ui/FlakeItemHud.java:83` малює `1…9` і `F`.
- **Тригер:** rebind.
- **Вплив:** підказка суперечить реальній клавіші.
- H/R уже правильно використовують mapping: `src/client/java/dev/flake/client/FlakeClient.java:59–61`.

**Мова:**
- `src/client/java/dev/flake/client/ui/StatusHud.java:96–114` кешує переклад лише за status ID.
- **Тригер:** змінити мову після першого показу статусу.
- **Вплив:** старі назви залишаються в попередній мові.

## 2. UX gaps — окремо від підтверджених багів

### Немає чіткого розрізнення фаз

`HudStatePayload.SkillState.CHARGING` (`src/main/java/dev/flake/ui/HudStatePayload.java:57,213–218`) представляє і заряд, і активний buff, і поширення хвилі.

Важливе уточнення: renderer не пише буквально «Заряджання» для всіх buff. Проблема — однакова модель прогресу, pulse та відсутність чіткої назви фази.

### Міна не має стану власника

`src/main/java/dev/flake/ability/ClassSkills.java:58–64` не повідомляє active state slot 1 міни. Немає зрозумілих:
- «Озброюється»;
- «Встановлена»;
- «Спрацювала»;
- «Замінена»;
- «Зникла».

Готовність повторного cast не повинна приховувати існування попередньої міни.

### Accepted cast змішаний із confirmed outcome

`boolean/int` не розрізняє:
1. запит прийнято;
2. здібність активовано;
3. ціль знайдено;
4. шкода пройшла;
5. лікування реально збільшило HP.

Наприклад, `src/main/java/dev/flake/duel/DarkCube.java:74–76` відправляє bolt після виклику `hurtServer`, не перевіряючи його результат. Це не означає, що слід автоматично прибрати slow/knockback при rejected damage: такі правила треба визначати окремо.

### Предмети важко впізнати до використання

- Ванільний held-item tooltip вимикається: `src/client/java/dev/flake/client/ui/FlakePlayerHud.java:29`.
- Інвентар у бойовому режимі закривається.
- Hotbar має маленькі іконки, а feed називає предмет уже після дії.

Потрібна коротка назва вибраного предмета при перемиканні, без постійної великої панелі.

### Шкода, HP та лікування показані неоднорідно

- Верхня fighter card отримує `health + absorption`, але звичайний `maxHealth`: `src/main/java/dev/flake/ui/FlakeUiServer.java:209–210`.
- Смуга clamp’иться: `src/client/java/dev/flake/client/ui/FlakeHudOverlay.java:142`.

Тому поглинання краще показувати окремо, а не маскувати під HP.

`src/client/java/dev/flake/client/ui/FlakePlayerHud.java:54–62,111` показує оцінку net HP/s, а не серверну суму лікування. Особливо невдалий мінімум «+0.1/с» за наявності Regeneration: це не точне підтверджене число.

### Відсутній явний stale state

`src/client/java/dev/flake/client/ui/FlakeUiClient.java:49–51`:
- freshness — **5 с**;
- extrapolation — максимум **0.25 с**.

При затримці snapshot таймер може довго виглядати звичайним, хоча вже заморожений. Потрібна тихо позначена втрата актуальності, не вигадане «готово».

### Читабельність і доступність

- Дві здібності класу часто мають однакову класову іконку.
- Довгі назви обрізаються у вузьких картках.
- Багато постійної пульсації/shimmer.
- Red/green не повинні бути єдиними ознаками стану.
- При заміні hotbar необхідно runtime-перевірити видимість attack-charge indicator, зокрема коли користувач обрав hotbar-режим.

## 3. Цілісна рекомендована feedback model

### 3.1. Спільний контракт, але різні домени

Використати **сумісний зі спільними action results** envelope:
- `requestId`;
- domain/result type;
- `reasonCode`;
- типізовані аргументи.

**Локалізація на клієнті**, не готові серверні повідомлення.

Розмежування:
- **PreparationResult** — зона основного агента: готовність, арена, кіт, recovery.
- **CastResult** — бойова спроба: accepted/cooldown/no target/stunned/already active/interrupted.
- Lifecycle-відмова використовує спільний reason code, а не дублює preparation logic у здібностях.

`requestId` — тільки кореляція. Сервер сам визначає клас, здібність, ціль, eligibility, cooldown та числа.

### 3.2. Стан здібності — не один взаємовиключний enum

Рекомендовано незалежні компоненти:

| Компонент | Приклад |
|---|---|
| Фаза | `READY`, `CHARGING`, `ACTIVE`, `ARMING`, `ARMED`, `PROPAGATING` |
| Cooldown | actual duration + remaining |
| Блокування | reason + remaining, якщо є |
| Поточний результат спроби | accepted/rejected/interrupted |

Приклад картки:
> **Жага крові**
> Активно · 3.2 с
> Відкат 11.2 с

При stun:
> **Оглушення · 0.4 с**
> Відкат 8.1 с

Блокування не має обнуляти або приховувати справжній cooldown. Active buff і cooldown можуть існувати одночасно.

### 3.3. Три рівні інформації

1. **Постійний стан** — компактна картка/status.
2. **Результат власної спроби** — короткий текст біля відповідного слота.
3. **Підтверджений наслідок** — hit/heal cue та оновлення HP/status.

Не перетворювати кожен рівень на окреме велике notification.

### 3.4. Конкретні короткі тексти

| Причина | Текст |
|---|---|
| Немає валідної видимої цілі | «Немає цілі в прицілі» |
| Відома вибрана ціль далеко | «Ціль надто далеко» |
| Відома вибрана ціль закрита | «Ціль за перешкодою» |
| Cooldown | «Ще 2.4 с» |
| Stun | «Оглушення · 0.4 с» |
| Already active | «Уже активно» |
| Already charging | «Уже накопичується» |
| Interrupt | «Перервано оглушенням» |
| Міна | «Озброюється · 0.8 с» / «Міна встановлена» |

**Не розкривати прихованих суперників:** «далеко/за перешкодою» — лише для легітимно відомої або явно вибраної цілі. Інакше «Немає видимої цілі».

### 3.5. Low-noise правила

- Відмова: **0.9–1.3 с**, максимум одна transient-підказка на слот.
- Повтор однакового reason: не запускати нову анімацію частіше ніж приблизно раз на **750 мс**.
- Зміна причини: показувати одразу.
- Cooldown spam: підсвітити існуючий таймер; без чату, feed та повторних beep.
- Успішний cast: короткий accent **150–250 мс**; не дублювати назву в окремому feed без потреби.
- READY: один слабкий cue, без повторення від кожного snapshot.
- Антиспам стосується **показу**, не відкидання легітимних бойових запитів.
- Чат — для командних/адміністративних повідомлень, не для H/R/ПКМ.

### 3.6. Damage/heal feedback

- Тільки серверно підтверджені величини.
- Для лікування — **фактичний приріст HP**, не requested heal.
- На повному HP не показувати фальшиве `+3.5`.
- Швидкі однотипні події можна агрегувати у вікні **100–200 мс**.
- Не створювати постійний потік чисел над усіма гравцями.
- Власне лікування краще коротко відобразити біля HP; підтверджене влучання — компактним cue, доступним і без shader.
- Звуки допоміжні; вимкнений звук не прибирає інформацію.

### 3.7. Status model

Передавати:
- status ID;
- amplifier;
- remaining і stable total;
- infinite flag;
- source/instance, якщо потрібне коректне replacement/refresh;
- reason завершення для важливих buff.

Пріоритет:
1. stun та інший контроль;
2. небезпечні негативні стани;
3. активні бойові buff;
4. постійні пасиви.

Якщо місця мало — `+N`, а не мовчазне зникнення. Vanilla icons приховувати лише коли replacement повноцінний.

### 3.8. Item-use та controls

- Feed використання — із підтвердженої серверної події.
- Inventory/count зміни — не доказ use.
- Cooldown percentage можна лишити для коректної ванільної маски, але не перетворювати його на секунди через базовий total.
- Buff timer — із status state.
- Hotbar keys — із `keyHotbarSlots[]`; offhand label — із mapping обміну рук.
- Не подавати offhand swap key як клавішу активації предмета.
- При перемиканні коротко показувати локалізовану назву предмета.

### 3.9. Participant scope та effects-off

- `PARTICIPANT`: власний combat HUD.
- `SPECTATOR`: окремий необов’язковий match HUD.
- `NONE`: не замінювати ванільний HUD.

`/flake effects` зараз фактично керує custom wave/post VFX:
- `src/main/java/dev/flake/duel/ShockwaveVisuals.java:231–234,244–255`;
- текст команди: `src/main/java/dev/flake/build/LoadoutCommands.java:205`.

Це **не загальний перемикач усіх частинок/звуків**. Не оголошую збереження іншого feedback багом цього перемикача.

Рекомендація:
- чітко назвати його декоративним VFX toggle;
- завжди зберігати cooldown, status, cast reason і підтвердження влучання;
- reduced-motion відокремити від effects-off;
- не переписувати shaders заради цього пакета.

## 4. Пакети реалізації

### Batch A — авторитетність HUD

**Файли:**
- `src/main/java/dev/flake/ui/HudStatePayload.java`
- `src/main/java/dev/flake/ui/FlakeUiServer.java`
- `src/main/java/dev/flake/ability/AbilitySlots.java`
- `src/main/java/dev/flake/slices/classes/ClassSlices.java`
- `src/client/java/dev/flake/client/ui/FlakeHudOverlay.java`
- `src/client/java/dev/flake/client/ui/FlakePlayerHud.java`
- `src/client/java/dev/flake/client/ui/FlakeItemHud.java`
- `src/client/java/dev/flake/client/FlakeClient.java`

**Контракти:** viewer role, actual cooldown duration, phase timing; renderer не відновлює authoritative state з metadata.

### Batch B — бойові результати на спільному action-result envelope

**Файли:**
- `src/main/java/dev/flake/ability/ClassSkills.java`
- `src/main/java/dev/flake/build/LoadoutCommands.java`
- `src/main/java/dev/flake/shared/FlakeActiveItem.java`
- targeted handlers:
  - `src/main/java/dev/flake/duel/ControlHammer.java`
  - `src/main/java/dev/flake/duel/DarkCube.java`
  - `src/main/java/dev/flake/duel/Gadgets.java`
  - `src/main/java/dev/flake/duel/Matadora.java`
  - `src/main/java/dev/flake/duel/CrystalClaw.java`
- спільна networking-реєстрація та клієнтський presenter.

**Контракти:** typed `CastResult`; accepted ≠ hit; reason codes/client localization/requestId сумісні з основним агентом. Команди та keybinds проходять однаковий combat dispatcher.

### Batch C — status/buff lifecycle та item-use

**Файли:**
- `src/main/java/dev/flake/ability/ClassSkills.java`
- `src/main/java/dev/flake/duel/Matadora.java`
- `src/main/java/dev/flake/duel/CrystalClaw.java`
- `src/main/java/dev/flake/ui/FlakeUiServer.java`
- `src/main/java/dev/flake/ui/HudStatePayload.java`
- `src/client/java/dev/flake/client/ui/StatusHud.java`
- `src/client/java/dev/flake/client/ui/FlakeItemHud.java`
- `src/client/java/dev/flake/client/ui/mixin/EffectIconsMixin.java`

**Передумова:** погодити семантику stun. Потім синхронізувати механіку, status, hotbar та VFX OFF.

### Batch D — описи, керування та доступність

**Файли:**
- `src/main/resources/assets/flake/lang/uk_ua.json`
- `src/main/resources/assets/flake/lang/en_us.json`
- `src/main/resources/assets/flake/lang/ru_ru.json`
- відповідні specs і HUD renderers.

**Результат:** правильні числа, динамічні key labels, назва вибраного предмета, amplifier, language-cache invalidation, відмінність фаз не лише кольором.

Paladin counting та Thorns floor — **окремі погоджені gameplay-рішення**, не приховані зміни в перекладах.

## 5. Acceptance tests

### Автоматизовані — вузькі, за зміненими контрактами

1. **Timing:** дисонанс 22 ticks, quake 10; production snapshot відповідає механіці.
2. **Modified cooldown:** Аметист змінює actual total; молот показує 8 с, не 10.
3. **Cast failures:** кожна реальна відмова має reason; no-target не витрачає cooldown.
4. **States:** active buff і cooldown одночасно; stun не затирає таймер.
5. **Payload round-trip:** viewer role, phases, reasons, amplifier, infinite status.
6. **Buff cancellation:** механіка/status/hotbar узгоджені після переривання.
7. **Item events:** останній витрачений предмет дає use event; просте зменшення stack не видається за use.
8. **Feedback suppression:** однаковий reason не накопичується; новий reason показується одразу.
9. **Локалізація:** усі три мови узгоджені з параметрами; перемикання мови оновлює status labels.
10. **Participant scope:** сторонньому не встановлюється combat replacement.

Наявні тести:
- `src/test/java/dev/flake/ability/AbilitySlotsTest.java`
- `src/test/java/dev/flake/ui/HudStatePayloadTest.java`

Важлива прогалина: ручний payload у `src/test/java/dev/flake/ui/HudStatePayloadTest.java:38` уже використовує **22/10**, тоді як production path отримує **0/100**. Поточний round-trip не ловить це розходження.

### Runtime acceptance

- **2 дуелянти + третій сторонній:** сторонній зберігає ванільний HUD та інвентар.
- **Lunge:** порожній приціл, край range, перешкода, cooldown, stun; зрозуміла відповідь без chat flood.
- **Buff → stun:** Bulwark/Bloodlust/Матадора/Кіготь; перевірка реальних ефектів, status та hotbar.
- **Міна:** arming, trigger, expiry, replacement.
- **Паладин:** четверта атака у щит/invulnerability та наступне підтверджене влучання.
- **Статуси:** permanent passive + короткий buff + негативний стан; amplifier видно, progress не завжди 100%.
- **Healing:** full HP, частковий heal, одночасні damage/heal; немає вигаданих чисел.
- **Controls:** переназначені hotbar/offhand/ability keys.
- **Доступність:** усі локалі, малий GUI, вимкнений звук, reduced-motion, attack indicator.
- **Effects-off:** необхідний feedback залишається без custom shader.
- **Мережа:** затримка snapshot, reconnect/world change; stale state зрозумілий, READY не видається за серверне підтвердження.

## Межі перевірки

Виконано **статичний аудит коду та читання наявних тестів**. Gradle, клієнт, multiplayer runtime й visual inspection не запускалися. Screenshots, performance та runtime-поведінку не заявляю перевіреними.

Detailed preparation/countdown/post-round journey навмисно не дублюється — це зона пункту 6 основного агента.
