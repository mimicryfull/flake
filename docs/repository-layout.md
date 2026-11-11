# Призначення папок Flake

Перевірка розміщення: 2026-11-11. Це навігація, не твердження, що вся поведінка
перевірена у грі. Актуальні імена файлів — у `.kilo/context/repo-map.md`.

## Правило розміщення

- Файл конкретної фічі лежить у її папці, навіть якщо це actor, widget або packet.
- Спільний механізм має більше одного споживача або явну загальну відповідальність.
- `features/` — список фіч, а не папка для довільних helper-ів.
- Не вводити `utils/`, `misc/`, `managers/` чи обов’язкові підпапки на кожен тип класу.
- Кількість файлів сама по собі не робить папку god-folder. Важлива єдність задачі.

## Common/server (`src/main/java/dev/flake/`)

| Папка | Що тут належить |
|---|---|
| корінь пакета | Тільки спільна ідентичність моду `Flake` |
| `bootstrap/` | Entry point, явний список фіч та інтеграційна реєстрація мережі |
| `content/` | Каталог, metadata-контракти та реєстрація предметів/звуків |
| `runtime/` | Загальне server-lifetime storage, без правил окремих фіч |
| `combat/` | Модифікатори, статуси, stun, cooldown, feedback, PvP vitals і kit hooks |
| `combat/ability/` | Контракти й виконання класових слотів, їхній input, targeting та damage hooks |
| `combat/item/` | Спільний базовий клас активного бойового предмета |
| `render/vfx/` | Wire envelope, доставка/leases, спільні timeline та impact envelope |
| `text/` | Локалізація, спільна для сервера й клієнта |

### Фічі (`features/`)

| Папка | Відповідальність |
|---|---|
| `arena/` | Геометрія, чернетки, authoring-команди та збереження арен |
| `armor/` | Спільний mapping броні для kit і preview, не runtime конкретного сету |
| `axeofcumshot/` | Наявна порожня заготовка класу; не зареєстрована фіча, залишена без видалення |
| `bastion/` | Bastion, обидві здібності, їхня анімаційна математика та VFX-повідомлення |
| `concussed/` | Concussed, shockwave/quake, відкладені касти та їхня анімація |
| `controlhammer/` | Визначення, runtime та VFX молота |
| `crystalclaw/` | Визначення, runtime та VFX кігтя |
| `darkcube/` | Визначення, runtime та VFX куба |
| `demolitionist/` | Міна та Blast Jump одного класу |
| `duel/` | Модель/стан і виконання матчу; не сховище всіх бойових фіч |
| `duel/kit/` | Видача та ідентифікація тимчасового спорядження |
| `duel/recovery/` | Durable snapshots, write-ahead journal і відновлення гравця |
| `duel/mixin/` | Серверні safety-адаптери життєвого циклу матчу |
| `hud/` | Серверна HUD-проєкція та її payload |
| `loadout/` | Модель, persistence, mutation workflow, команди та editor request/result |
| `loadout/editor/` | Каталог вибору й чиста математика layout, доступні headless-тестам |
| `lobby/` | Кімнати, membership, дії/права, проєкція та payload-и |
| `matadora/` | Визначення, runtime та VFX Матадори |
| `obsidianarmor/` | Реальні правила конкретного сету Obsidian Bastion |
| `passives/` | Прості пасивні предмети, immutable bonuses і regeneration schedule |
| `training/` | Ідентифікація й setup тренувальних цілей |
| `utilities/` | Невеликий набір стандартного kit-контенту та простих gadgets |
| `vampire/` | Vampire, його lifesteal, здібності й VFX |
| `voidpaladin/` | Визначення меча, confirmed-hit counter, runtime та VFX |

`loadout/`, `lobby/` та `combat/ability/` залишені цілісними: їхні файли обслуговують
одну підсистему. Окремі transport/storage підпапки зараз додали б навігацію без
корисної межі. `utilities/` не призначена для нових складних предметів: їм потрібна
власна фіча.

## Клієнт (`src/client/java/dev/flake/`)

| Папка | Відповідальність |
|---|---|
| `bootstrap/` | Клієнтський startup/input та підключення конкретних VFX-акторів |
| `client/network/` | UI packet adapters і клієнтські кеші snapshot/HUD/build result |
| `client/settings/` | Спільні focus-overlay preferences; старий файл конфігурації й `mask.*` ключі збережені |
| `client/ui/` | Тема, іконки та tooltip, використані різними екранами/HUD |
| `client/ui/widgets/` | Спільна кнопка; editor-only `UiSlotButton` сюди не належить |
| `client/ui/mixin/` | Tab integration між загальним HUD і lobby overlay |
| `features/{bastion,concussed,controlhammer,crystalclaw,darkcube,matadora,vampire,voidpaladin}/client/` | Тільки актори/геометрія власної фічі |
| `features/hud/client/` | HUD-панелі й статуси |
| `features/hud/client/mixin/` | Адаптер vanilla effect icons до статусного HUD |
| `features/loadout/client/` | Editor, picker, mannequin preview та власний slot widget |
| `features/lobby/client/` | Browser/menu, керування кімнатою та Tab overlay |
| `render/vfx/` | Спільні actor-бази, slot/frame/buffer infrastructure та renderer |
| `render/vfx/mixin/` | Єдиний PostPass/UBO-адаптер |

Спільний renderer більше не імпортує runtime/налаштування конкретного предмета.
Чотири UBO-слоти визначені у `VfxBuffer`, а не в анімації Concussed.

## Тести й ресурси

- `src/test/java/` повторює відповідальність production-пакетів: `combat/ability`,
  `content`, `features/arena`, `bastion`, `concussed`, `duel` (з `kit` і `recovery`),
  `hud`, `loadout` (з `editor`), `lobby`, `passives`, `vampire`, `voidpaladin`, `render/vfx`.
- Feature animation-тести належать фічам; спільні clock/impact/shader-assembly тести — VFX.
- `src/main/resources/assets/flake/{items,models/item,textures/item,sounds,lang}` —
  формат Minecraft, не god-folder. Розташування визначають resource IDs і references.
- `src/client/resources/assets/flake/post_effect/` і `shaders/post/` — post-pass;
  `shaders/include/{classes,items,weapons}` — власники ефектів,
  `shaders/include/common/` — справді спільні GLSL-примітиви.
- Корені `src/main/resources/` та `src/client/resources/` містять Fabric/mixin-конфігурацію.
  Resource IDs, shader includes і wire-поля при цьому впорядкуванні не змінювались.

## Інші папки репозиторію

| Папка | Призначення |
|---|---|
| `docs/` | Актуальна архітектура й цей навігаційний огляд |
| `docs/planning/` | План розвитку, не поточна специфікація коду |
| `docs/reports/` | Історичні UX-аудити, VFX-звіти та результати перевірок; старі посилання на код можуть бути неактуальними |
| `docs/tooling/` | Starter-інструкція Kilo |
| `tasks/task1`–`task7` | Вихідні завдання й reference assets, збережені разом за задачею |
| `tools/` | Ручний GPU/VFX probe, не production-код |
| `gradle/wrapper/` | Стандартний Gradle wrapper |
| `.kilo/{agent,commands,context,skills,tools}/` | Ролі/команди, карти, предметні інструкції й генератор карти |
| `.serena/`, `.zed/` | Конфігурація локальних інструментів |

`build/`, `bin/`, `.gradle/`, `run/`, `logs/`, `tools/__pycache__/` — generated/runtime
дані, а `reference/` — зовнішній референс. Це не production-пакети; вони не
перекомпоновані й не очищені цим аудитом. Частина `bin/` уже відстежується Git:
це окрема проблема гігієни артефактів, не причина переносити Java-файли.
Наявні `.bak` конфігурацій також не видалені як потенційні резервні копії користувача.
