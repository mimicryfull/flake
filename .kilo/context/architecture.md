# Flake — архітектурна карта

> Компактна база знань для наступних сесій. Джерело істини — код, не цей файл.
> Позначки: **[П]** підтверджено читанням коду, **[?]** припущення або не перевірено.
> Повний feature-first рефакторинг: 2026-11-11. Короткий посібник: `docs/architecture.md`.

## 0. Швидкі факти

- Fabric mod, Java 25, Minecraft `26.3`, Fabric API `0.165.2+26.3` (`gradle.properties`, `build.gradle`). **[П]**
- `splitEnvironmentSourceSets()`: `src/main` (common/server), `src/client` (client-only), `src/test` (JUnit 5). **[П]**
- Entrypoints (`fabric.mod.json`): main — `dev.flake.bootstrap.FlakeMod`, `dev.flake.bootstrap.FlakeNetworking`; client — `dev.flake.client.FlakeClient`, `dev.flake.client.ui.FlakeUiClient`. Ідентичність мода — `dev.flake.Flake`, без зворотної залежності від bootstrap. **[П]**
- Mixin-конфіги: `flake.duel.mixins.json`, `flake.client.mixins.json`, `flake.ui.client.mixins.json`. **[П]**
- `reference/orbital-railgun/` — сторонній референс, не входить у source sets. **[П]**

## 1. Підсистеми та призначення

| Підсистема | Пакети / файли | Роль |
|---|---|---|
| Composition | `dev.flake.bootstrap` | Явне підключення фіч, entrypoints, мережеві адаптери |
| Content | `dev.flake.content` | `ContentSpec`, `ContentCatalog`; без імпортів конкретних фіч |
| Gameplay features | `dev.flake.features.<feature>` | Визначення, runtime, animation math та feature-specific VFX-повідомлення |
| Combat / abilities | `dev.flake.combat`, `combat.ability` | Модифікатори, stun, спільні ability rules та серверні runtime-и |
| Builds | `features.loadout`, `features.loadout.editor` | Immutable `Loadout`, store/manager, `LoadoutService`, команди й редактор |
| Duel | `features.duel`, `.kit`, `.recovery` | Матч, тимчасовий кіт, write-ahead journal і повернення стану |
| Arena | `features.arena` | Конфіг, persistence, команди та bounds |
| Lobby / HUD | `features.lobby`, `features.hud` | Власний стан, операції, read models і payload-и |
| Client UI | `features.<feature>.client`, `client.ui` | Feature-екрани/HUD та спільні віджети |
| Client VFX | `render.vfx`, `features.<feature>.client` | Спільний буфер і feature-актори; один UBO |

## 2. Feature-first архітектура

- `bootstrap.Features` — єдине явне підключення контенту. Окремі етапи: metadata, Minecraft item registration, серверні hooks. Metadata можна читати у headless-тестах без реєстрації предметів. **[П]**
- `content.ContentSpec` — `WeaponSpec`, `ActiveItemSpec`, `PassiveSpec`, `ClassSpec` і lifecycle. `ActiveItemSpec.buffStatusId` зв'язує предмет із серверним статусом без імпорту runtime в HUD. **[П]**
- `content.ContentCatalog` зберігає визначення й відхиляє дублікати; не bootstrap-ить конкретні фічі. **[П]**
- `ClassSpec` активує слот і проєктує таймери за `ServerPlayer`, а не неоднозначним UUID між серверами. **[П]**
- `CombatModifiers`: outgoing skill amplification → incoming mitigation; окремі внески cooldown, item totals, melee healing і reflection. Предмети реєструють свої правила, ядро їх не перелічує. **[П]**
- `CombatStatuses` збирає проєкції від власників статусів; `CombatEquipment` — внески підготовки спорядження. **[П]**
- `Stuns` володіє блокуванням дій та позицією; зброя тільки спричиняє stun. Interrupt не скидає cooldown-и. **[П]**
- Mutable state обмежений конкретним сервером (`ServerState` або server-scoped runtime). Minecraft registry IDs, wire IDs і формати збережень не змінюються від package move. **[П]**

```text
command / build packet → LoadoutService → validate / persist / consequences
ability command / H,R packet → ClassAbilityApplication → ClassSpec → owning runtime
feature → CombatModifiers / CombatStatuses / CombatEquipment
feature visual message → CombatVisuals → CombatVfxPayload → feature actor → shared UBO
```


## 3. Предмети та реєстрація

- `combat.item.ActiveCombatItem` делегує взаємодії в серверну активацію, перевіряє cooldown та підтримує client feedback/offhand shielding. **[П]**
- `features/<name>/<Name>Definition` підключає metadata, предмет, lifecycle і внески; `<Name>Runtime` володіє механікою та server-scoped станом. Не кожній простій фічі потрібні два файли. **[П]**
- `ItemRegistration` містить лише спільні Fabric helpers, не список конкретних предметів. **[П]**
- `features.passives.PassivesDefinition` описує прості пасиви. `RegenerationSchedule` — чистий per-player таймер, окремий на кожен сервер. Leech/Thorns підключають внески до спільного confirmed-damage pipeline. **[П]**
- `features.armor.ArmorItems` — одна mapping броні для server kit і client preview. **[П]**
- Assets лишаються у стандартних `assets/flake/items`, `models/item`, `textures`, `lang`; package move не змінює ресурсні ID. **[П]**
- Рецепти додавання/видалення фіч: `docs/architecture.md`.

> `ACTIVE_ITEM_SLOTS = 2` — два **предмети** (ПКМ), не слоти здібностей H/R.

## 4. Здібності класів

- `combat.ability.AbilitySlots` — два 1-based слоти H/R, окремі від предметів. **[П]**
- `ClassAbilityApplication` — спільний server-authoritative вхід команди та `AbilitySlotPayload`; викликає `ClassSpec.activate`, не switch по конкретних класах. **[П]**
- `features.concussed.ConcussedAbilities` володіє charge/quake/cooldown. `ConcussedDefinition` підключає клас, таймери й lifecycle. Зміна класу скасовує відкладений каст, не перезарядку. **[П]**
- `features.bastion.BastionRuntime`, `features.vampire.VampireRuntime`, `features.demolitionist.DemolitionistRuntime` мають окремий стан на сервер. `ClassAbilityRuntimes` зберігає фабрики, slot cooldown-и й порядок confirmed-damage реакцій, але не імпортує класи-фічі. **[П]**
- `AbilityTargeting`, `AbilityMovement`, `AbilityEffects` містять спільні фактичні правила, не discovery/framework. **[П]**
- Bloodlust + Leech лікують одним викликом; reflection захищений від циклів. **[П]**
- Гейт: shared stun, protected-not-fighting, countdown, alive/spectator. Damage/knockback/цілі визначає лише сервер. **[П]**

## 5. Білди та редактор

`features/loadout/Loadout.java` — immutable record: клас, 4 броні, зброя, пасив, 2 активні предмети, `migrationConflicts`. **[П]**
- `BASE_HEALTH = 50`; `errors()` перевіряє міграційні конфлікти та дубль активного предмета. Числового бюджету в поточній моделі немає. **[П]**
- v1-міграція: `LoadoutManager.importLegacy` + `LegacyImport`; невдалий імпорт не замінює поточний білд. **[П]**
- `LoadoutManager` зберігає білди на диску через `LoadoutStore`, а `LoadoutService` володіє ним у межах сервера. **[П]**

`features/loadout/LoadoutCommands.java` — тонкий командний адаптер: **[П]**
- `/flake class|armor|slot|weapon|passive|item 1|2 ...` викликають `LoadoutService`.
- Lock/validation/persistence/class-change/readiness спільні для команд і `BuildApplication` (пакет редактора), а не повторюються в адаптерах.
- `/flake effect(s)`, `/flake duel stop|recover|join|leave|ready|not_ready|status`, `/flake dummy spawn|remove`.
- Старі команди `accessory|accslot|utility` явно відхиляються. **[П]**

`features/loadout/editor/EditorCatalog.java` — каталог вибору, candidate-Loadout-и та стат-дельти. **[П]**

Client editor:
- `FlakeClient` (G) → `FlakeScreen` → `BuildEditorScreen`. **[П]**
- `features.loadout.client.BuildEditorScreen`: optimistic draft, атомарний `BuildApplyPayload` з revision, обробка STALE/INVALID/SAVE_FAILED/LOCKED і `LoadState.CORRUPT`. **[П]**
- `ItemPickerScreen` — модальний вибір; `BuildPreview` — скін-прев'ю. **[?]** повний вміст не читався.

## 6. Duel, persistence, arena

Багатолобійна архітектура (переписано): **[П]**
- `features.lobby.LobbyManager` — реєстр **незалежних кімнат**, прив'язаний до конкретного `MinecraftServer`; `create/join/leave/delete/disconnect/shutdown`, авто-закриття порожніх кімнат, per-viewer visibility. Ліміт 128 кімнат.
- `features.lobby.LobbyRoom` — owner, visibility, invite-сет, mode, arena, roster/readiness і **власний** `MatchRuntime`. Мутації перевіряють права owner.
- `features.duel.MatchRuntime` — **один матч на кімнату**: арена/учасники/відлік (`COUNTDOWN_TICKS = 60`)/таймер, recover, `finishRound`, `stop`. Kit preparation винесено в `features.duel.kit.DuelKit`.
- `features.duel.DuelRuntime` — серверний реєстр матчів і запити стану/допустимості; Fabric damage/death/tick/join/disconnect/use/entity-load hooks.
- `features.duel.recovery.DuelRecovery` — server-scoped захист інвентаря + journal (`<world>/flake/recovery`), resume/retry/recover. Стан одного сервера не очищає стан іншого.
- Потік матчу: `allReady()`/owner start → `beginCountdown()` (preflight арени, білдів, слотів, безпеки точок) → `launch()`:
  1. `DuelPreparation.run`: спочатку durable snapshot **усіх** гравців (`journal.create`), лише потім мутації/телепорт; збій → rollback.
  2. `DuelKit.apply`: очищення інвентаря/ефектів, `PvpVitals`, ADVENTURE, предмети з `ContentCatalog`, внески спорядження; `DuelEquipment.temporary(..., matchId)` зберігає наявні custom data.
  3. `match.startRound()`.
- FIGHTING: перевірка арени/режиму/дистанції, вимкнений ванільний реген, `PassiveHandler.onFightTick` (щотік; інтервали тримаються per-player у server-owned `RegenerationSchedule`).
- Смерть → `finishRound`, повернення речей; cancel/disconnect/server-stop/leave → `stop()` + recover.
- `recover()`: читання `DuelPlayerState`, безпечна точка (з fallback), `state.restore`, тег-квитанція `flake_restored_<token>`, перевірка запису на диск через `loadPlayerData`, лише потім `journal.complete`.

`features.duel.DuelMatch` — стан `COUNTDOWN → FIGHTING → RESTORING → FINISHED|CANCELLED`, рахунок/winsNeeded, незмінні loadout-и матчу. **[П]**
`features.duel.DuelLobby` — склад/готовність/teams/lock; `forceLeave` для дисконекту. **[П]**

Лобі описує `TEAM_FIGHT`, але чинний match preflight вимагає рівно двох учасників. Реалізація team fight — окрема функціональна задача, не частина структурного рефакторингу. **[П]**

Persistence:
- `InventorySnapshot` — копія слотів + selected slot, відновлення лише у порожній інвентар. **[П]**
- `DuelPlayerState` — NBT v1: позиція/вимір/fallback, items, effects, abilities, food, gamemode, health/absorption, XP, fire/air/frozen, token; валідація перед мутацією. **[П]**
- `RecoveryJournal` — write-ahead NBT (compressed), атомарний hard-link, `completed/<uuid>-<token>.nbt` архів. **[П]**
- `features.duel.kit.DuelEquipment` — маркери `flake_duel_kit` та `flake_match`; cleanup обмежений конкретним матчем. **[П]**
- `features.loadout.LoadoutStore` — `<world>/flake/builds/<uuid>.properties` v1, MISSING/LOADED/CORRUPT, atomic move; запис **перед** публікацією в пам'ять. **[П]**
- `features.loadout.LoadoutManager` — revisions, STALE/INVALID/SAVE_FAILED, атомарна заміна. **[П]**
- `features.arena.ArenaRepository` — іменовані арени `<world>/flake/arenas/<name>.properties`; `default` зберігає legacy `flake/arena.properties`. **[П]**
- `features.arena.ArenaStore` — properties v1 з atomic move; `Arena` — immutable bounds/spawn model. **[П]**

## 7. Клієнтський рендеринг і синхронізація gameplay ↔ VFX

Сервер (авторитет):
- `render.vfx.CombatVisuals` — generic доставка, radius 48, server-scoped preferences/leases, cancel/clear/toggle; не знає конкретних фіч. Повідомлення/контакти/таймінги належать `features.<name>.<Name>Visuals`. **[П]**
- `render.vfx.CombatVfxPayload` зберігає wire ID `flake:shockwave`, поля та порядок Phase; package/type rename не змінює протокол. **[П]**
- Крит Void Paladin: runtime шле `VoidPaladinVisuals.crit` лише після підтвердженої шкоди (`AFTER_DAMAGE`); світовий ефект, не екранна текстура. **[П]**
- Буф-візуали: `features.matadora.MaskAnimation` і `features.crystalclaw.CrystalClawAnimation` — enter/plateau/exit + activation burst; runtime шле ON/OFF, actor лише відтворює. **[П]**
- Коди стилю в `WaveStyles[i].z`: 0 shell, 1 charge, 2 quake, 3 bolt, 4/5 Матадора, 6/7 Crystal Claw, 8 крит Void Paladin (`features.voidpaladin.CritAnimation`, 0.42 с). **[П]**
- Правило: сервер визначає damage/knockback/cooldown та контакт; клієнтський `DarkBoltActor` не переоцінює цілі. **[П]**

Бастіон (VFX rework): **[П]**
- `BastionAnimation` — damped assembly spring, compact Ram flash/suppression envelopes, Bulwark assembly/4 s plateau/0.62 s dissolution. Gameplay timing unchanged.
- `SHIELD_RAM[_SHIELD]`: `yaw` задає напрямок; `aim` — **нерухома серверна точка контакту**, `targetId >= 0` лише після успішного `hurtServer`. Промах не показує impact/suppression. Клієнт відстежує ціль тільки для пригнічення, а рух кастера — 5 world-space breadcrumbs із кроком 0.035 s та інерційним лагом.
- `BULWARK_ON/OFF/HIT`: `HIT.aim` — напрямок до джерела підтвердженої шкоди (ZERO для невідомого); локальна хвиля/пружна реакція найближчих пластин. Залишок absorption для внутрішнього світла читається з синхронізованого `LivingEntity`, не вигадується таймером.
- GLSL `bastionPlate` — точний перетин 11 півпросторів: товсті грановані щитові пластини з ребром, перламутровими шарами та erosion-dissolve. Ram — 6 частин клина, 3 інерційні ребра шлейфу, pressure facets/уламки; Bulwark — 12 пластин у двох ярусах, внутрішні підсилення, 6 наземних ключів. Геометрія видима також від першої особи; depth-test і spatial bounds, без raymarch/нового UBO/ванільних частинок як основи.

Упир (blood VFX): **[П]**
- `VampireRuntime` зберігає правила: Blood Lunge — ціль/LOS, негайний ривок, skillDamage(4), незалежний heal(1.5), cooldown 120 ticks; Bloodlust — Speed I/100 ticks, cooldown 300 ticks, melee-heal `taken * 0.18` разом із Leech одним викликом.
- `CombatVfxPayload`: `BLOOD_LUNGE`, `BLOODLUST_ON/OFF`, `BLOOD_SIPHON`, wire-поля незмінні. Lunge: `aim` — фіксований контакт, `yaw` — напрямок, `targetId >= 0` лише при успішному damage. Siphon: `origin` — точка витягування, `sourceId` — отримувач, `aim` — його корпус, `yaw` — фактичний приріст HP. Повідомлення лише після реального лікування активним Bloodlust; OFF очищає також спостерігачів поза радіусом активації.
- `GhoulAnimation` — чиста математика таймінгів/Trail (16 точок / 0.025 s). `features.vampire.client.GhoulRenderState.Actor` поєднує ауру, ривок і до 2 siphon-подій в одному слоті UBO (style 12). Бюджет: 4 слоти, 15 136 байт.
- Геометрія: 64 сегменти/8 bounds; основні мембрани й плечові кігті, 3 world-space стрічки ривка, криві витягування/поглинання, 2 асиметричні серпи контакту. GLSL — товсті анізотропні капсули з Hermite-згладжуванням, бордовим меніском/рубіновим ядром/темними прожилками, exact depth clipping; без raymarch чи billboard. Активна аура реагує на velocity/lag та синхронізований swing, OFF лишає лише коротке втягування/затухання. World/disconnect/CLEAR прибирають rig разом зі звичайними waves.
- `tools/vfx_probe.py --ghoul-fixtures <dir>` відтворює зняті з реального клієнта `.ubo` + camera `.json` для GPU-перевірок, не дублює процедурну CPU-анімацію в Python.

Клієнт:
- `FlakeClient` приймає `CombatVfxPayload` → `VfxRenderState.receive`; очищення при disconnect/зміні світу. **[П]**
- `VfxRenderState` володіє бюджетом/кадром/масивом; feature-актори володіють конкретними анімаціями. `RenderFrame`/`SlotWriter` повторно використовуються без per-frame allocation. **[П]**
- `render.vfx.mixin.PostPassMixin` записує `VfxBuffer` у той самий `ShockwaveConfig`; UBO layout і shader style IDs збережені. **[П]**
- `src/client/resources/assets/flake/post_effect/shockwave.json`: 2 pass-и — `flake:post/shockwave` (sampler `In` + `Depth`) → blit назад у `minecraft:main`. **[П]**
- GLSL: `src/client/resources/assets/flake/shaders/post/shockwave.fsh` (755 рядків): аналітичні перетини без raymarch; `maskAura` (фіолетові куби), `crystalAura`+`iceShard` (кристалічні шарди Crystal Claw, палітра `CRYSTAL_DEEP/BRIGHT/TINT`), оверлей власника перемикає палітру через `maskCrystal`. **[П]**
- `GroundLightning` — детермінована процедурна геометрія (GROUPS=8, PER_GROUP=8, MOTES=32, DURATION=0.96s) для quake. **[П]**
- `VfxTimeline` — elapsed-час на `System.nanoTime`, пауза підтримується; не залежить від FPS. **[П]**
- `ElectricQuake.wavefrontDistance(t)` використовується **і** сервером для хітів, **і** клієнтом для візуального фронту — спільна модель синхронізації. **[П]**

### Додати custom GLSL VFX
1. Новий `.fsh`/`.vsh` у `src/client/resources/assets/flake/shaders/...`. **[?]** структура program-шейдерів не перевірена.
2. За потреби — post-effect JSON у `assets/flake/post_effect/` з uniform-блоком. **[П]** за зразком shockwave.
3. Gameplay → feature Visuals → `CombatVfxPayload` → feature actor → спільний UBO/PostPassMixin. **[П]**
4. Уникати ванільних частинок як основного VFX; тримати один спільний UBO та CPU-масив. **[П]**

## 8. Мережа (payload-и)

| Payload | Напрям | Реєстрація | Призначення |
|---|---|---|---|
| `features.lobby.LobbyActionPayload` (v2) | C→S | `FlakeNetworking` | 15 дій лобі; `LobbyActions` виконує їх, rate limit 2 тіки |
| `features.lobby.LobbySnapshotPayload` (v2) | S→C | `FlakeNetworking` | per-viewer snapshot від `LobbyProjection`, кожні 10 тіків |
| `features.hud.HudStatePayload` | S→C | `FlakeNetworking` | `HudProjection`: білд, таймери, ролі, статуси, revisions і load state, кожні 2 тіки |
| `features.loadout.BuildApplyPayload` | C→S | `FlakeNetworking` | `BuildApplication` → атомарна зміна через `LoadoutService` |
| `features.loadout.BuildResultPayload` | S→C | `FlakeNetworking` | OK/INVALID/STALE/SAVE_FAILED/LOCKED + текст |
| `combat.ability.AbilitySlotPayload` | C→S | `AbilityInputServer` | H/R, слот 1..2 → `ClassAbilityApplication` |
| `render.vfx.CombatVfxPayload` | S→C | `CombatVisuals` | VFX-фази; чинний wire ID `flake:shockwave` |

- Клієнтський стан: `client/ui/FlakeUiClient` (snapshot/hud/build result, 5-секундна свіжість, типові дії `query/createLobby/join/ready/startMatch/kick/invite/transfer/deleteRoom/settings/applyBuild`).
- `ui/mixin/LoadoutUiBridgeMixin` **видалено** разом із `flake.ui.mixins.json`: глобального лобі більше немає, стан тримає `LobbyManager`.
- Snapshot roster обмежений `ROSTER_CAP = 256` рядків; учасники власної кімнати завжди першими.

## 9. UI

- Гортач: `FlakeHudOverlay` (головний, картки здібностей лише для `PARTICIPANT`), `FlakePlayerHud` (броня/health), `FlakeItemHud` (хотбар/кулдауни, реальні key mappings), `FlakeTabOverlay` (онлайн + кімната кожного гравця), `client/ui/mixin/HudTabOverlayMixin`. **[П]** частково.
- Екрани лобі: `FlakeScreen` (MAIN_MENU/BROWSER/CREATE_LOBBY/ROOM/BUILD_EDITOR/ARENA_SETTINGS), `LobbyManageScreen` (owner: invite/kick/transfer з посторінковими списками).
- Редактор: `BuildEditorScreen` шле `BuildApplyPayload`, обробляє `BuildResultPayload` (STALE/INVALID/SAVE_FAILED/LOCKED) і показує `LoadState.CORRUPT` у футері.
- Віджети: `UiTheme`, `UiCard`, `UiButton`, `UiSlotButton`, `UiItemRow`, `UiIcons`. **[П]** (деякі лише за назвами/використанням).
- Ванільний `InventoryScreen` примусово закривається, коли активний Flake HUD. **[П]**
- Клавіші: G — меню/редактор, H/R — здібності, PageUp/PageDown — гортання вкладки, коли затиснутий Tab. **[П]**

## 10. Де відбувається серверна перевірка

- Ефекти предметів: `ActiveCombatItem.activateServer` + серверний `DuelRuntime.blocksAbility`. **[П]**
- Здібності: `ClassAbilityApplication` → `ClassSpec.activate` → runtime відповідної фічі. **[П]**
- Білд: `LoadoutService` + `Loadout.errors()` + durable `LoadoutManager`. **[П]**
- Дуель: `MatchRuntime.preflight` (alive/non-spectator, транспорт/сон, чисті crafting/cursor, валідний білд, безпечні точки, відстань ≥ 2 блоки). **[П]**
- Відновлення: перевірка токена/власника/версії у `DuelPlayerState.read` і `RecoveryJournal`, durable-запис перед зміною стану. **[П]**

## 11. Навігація за задачею

| Task category | Entry points | Related components |
|---|---|---|
| New item/weapon | `features/<name>/*Definition`, `features/loadout/Loadout`, `bootstrap/Features` | `ContentSpec`, `ItemRegistration`, assets/lang |
| Class ability | `features/<class>/*Runtime`, `*Definition` | `combat/ability`, `ContentCatalog`, feature Visuals/actor |
| Gameplay balancing | Константи відповідної фічі та metadata | `Loadout.stats`, `EditorCatalog.comparison`, cooldown snapshots |
| Custom GLSL VFX | `features/<name>/client/*Actor`, feature `*Visuals` | `render/vfx`, shader includes, client composition root |
| Post-processing | `render/vfx/VfxRenderState`, `VfxBuffer`, `mixin/PostPassMixin` | `assets/flake/post_effect/shockwave.json`, shader ABI |
| HUD | `features/hud`, `features/hud/client` | `CombatStatuses`, `FlakeUiClient`, shared `client/ui` |
| Build editor | `features/loadout/client`, `features/loadout/editor` | `LoadoutService`, BuildApply/Result payloads |
| Server networking | `bootstrap/FlakeNetworking`, `combat/ability/AbilityInputServer` | Feature-owned payloads, `CombatVisuals` |
| Duel | `features/duel/MatchRuntime`, `DuelRuntime`, `DuelMatch` | `kit/DuelKit`, `recovery/*`, `features/arena` |
| Lobby | `features/lobby/LobbyManager`, `LobbyRoom`, `LobbyActions` | `LobbyProjection`, snapshot/action payloads |
| Inventory recovery | `features/duel/recovery/*` | Write-ahead journal, receipt verification, `kit/DuelEquipment` |
| Arena | `features/arena/*` | `MatchRuntime.preflight`, existing world paths |

## 12. Невідоме / неперевірене

- `FlakeScreen`/`LobbyManageScreen` — нові екрани; runtime-перевірка верстки на різних GUI scale не проводилась. **[?]**
- `ItemPickerScreen`, `BuildPreview`, `EditorLayout`, `FlakeHudOverlay`, `FlakeItemHud` — лише часткове або поверхневе читання. **[?]**
- `HudStatePayload.toLoadout()` — деталі реконструкції білда на клієнті. **[?]**
- Реальні multiplayer-сценарії після рефакторингу (disconnect, lethal cast, два одночасні матчі) потребують перевірки в грі; unit-тести не замінюють runtime. **[?]**
- 5 duel-міксинів (`Duel*SafetyMixin`) та `HudTabOverlayMixin` — вміст не читався. **[?]**
- `ArenaCommands`/`ArenaDraft` — повний workflow створення арени. **[?]**
- Вміст `shockwave.fsh` і всіх інших GLSL (окрім факту наявності) не аналізувався. **[?]**
- `ContentCatalog.cooldownSeconds` споживають `HudProjection` і `FlakeItemHud`; серверні actual totals враховують passive modifiers. **[П]**
- Повний перелік assets/sounds/lang та відповідність назв предметів. **[?]**
- Одночасні матчі: архітектурно підтримано (`LobbyManager` + `MatchRuntime` per room), але runtime-сценарій із двома паралельними боями ще не прогнано. **[?]**

## 13. Правило оновлення цієї карти

- Після архітектурної зміни оновлювати **лише відповідний розділ**, не переписувати документ.
- Нові факти позначати **[П]** лише після читання актуального коду; інакше — **[?]**.
- Якщо шляхи/назви розійшлися з кодом — вірити коду й виправити карту.
