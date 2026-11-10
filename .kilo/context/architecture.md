# Flake — архітектурна карта

> Компактна база знань для наступних сесій. Джерело істини — код, не цей файл.
> Позначки: **[П]** підтверджено читанням коду, **[?]** припущення або не перевірено.
> Оновлено: 2026-11-10. Переглянуто ~26 Java-файлів + build/mixin/asset-конфіги.

## 0. Швидкі факти

- Fabric mod, Java 25, Minecraft `26.3`, Fabric API `0.165.2+26.3` (`gradle.properties`, `build.gradle`). **[П]**
- `splitEnvironmentSourceSets()`: `src/main` (common/server), `src/client` (client-only), `src/test` (JUnit 5). **[П]**
- Entrypoints (`fabric.mod.json`): main — `dev.flake.ExampleMod`, `dev.flake.ui.FlakeUiServer`; client — `dev.flake.client.FlakeClient`, `dev.flake.client.ui.FlakeUiClient`. **[П]**
- Mixin-конфіги: `flake.duel.mixins.json`, `flake.client.mixins.json`, `flake.ui.mixins.json`, `flake.ui.client.mixins.json`. **[П]**
- `reference/orbital-railgun/` — сторонній референс, не входить у source sets. **[П]**

## 1. Підсистеми та призначення

| Підсистема | Пакети / файли | Роль |
|---|---|---|
| Vertical Slice Architecture | `dev.flake.shared` | Контракти `CombatSlice`, реєстр `SliceRegistry`, базовий предмет `FlakeActiveItem`, i18n |
| Slice-реалізації | `dev.flake.slices.*` | Метадані + lifecycle кожного предмета/класу |
| Combat / abilities | `dev.flake.ability`, `dev.flake.duel` | Здібності, кулдауни, шкода, відкидання |
| Builds | `dev.flake.build`, `dev.flake.editor` | `Loadout`, сесійний `LoadoutManager`, `/flake` команди, каталог редактора |
| Duel | `dev.flake.duel` | Лобі, матч, підготовка, відновлення речей, тимчасовий кіт |
| Arena | `dev.flake.arena` | Конфіг арени, команди, bounds |
| UI / networking | `dev.flake.ui` | Payload-и лобі/HUD, `LobbyRoom`, серверна синхронізація |
| Client UI | `src/client/.../client/editor`, `.../client/ui` | Редактор білда, HUD, скіни, віджети |
| Client VFX | `src/client/.../client`, `.../duel/*Animation.java` | Пост-процес, геометрія блискавки, таймлайни |

## 2. Vertical Slice Architecture (VSA)

`src/main/java/dev/flake/shared/CombatSlice.java`: **[П]**
- `Lifecycle` — `register()` (серверні хуки/тіки) і `reset(ServerPlayer)` (очищення стану).
- `WeaponSpec(id, titleKey, cooldownTicks, arrowCount, stackFactory)`.
- `ActiveItemSpec(id, titleKey, cooldownTicks, buffTicks, offhand, stackFactory)`.
- `PassiveSpec(id, titleKey, categoryKey, iconFactory, PassiveHandler)`; `PassiveHandler.onApplyKit` / `onFightTick`.
- `ClassSpec` — id, role/summary ключі, `slot1()`, `slot2()`, `cooldownRemaining`, `activeElapsed`.

`src/main/java/dev/flake/shared/SliceRegistry.java`: **[П]**
- Статичний `bootstrap()` викликає `install()` шести slices: ControlHammer, Matadora, DarkCube, Gadgets, Passives, Classes.
- EnumMap-и `WEAPONS/ACTIVES/PASSIVES/CLASSES` + список `LIFECYCLES`.
- `registerServerHooks()` (з `ExampleMod.onInitialize`) виконує `Lifecycle.register()`.
- `resetPlayer()` викликається на старті дуелі та при поверненні речей.
- `cooldownSeconds(stack)` / `buffSeconds(stack)` — резолв кулдауну за стеком (споживач у HUD — **[?]**).

Ключовий принцип: UI, редактор, HUD і дуель **не мають** hardcoded switch по предметах — усе через `SliceRegistry`. **[П]**

### Залежності між підсистемами

```
ExampleMod ──> LoadoutCommands ──> LoadoutManager / Loadout
          │                   ├─> DuelRuntime ─> DuelMatch / DuelLobby / RecoveryJournal
          │                   │                └> ArenaStore / DuelPlayerState / InventorySnapshot
          │                   └─> AbilitySlotPayload -> activateSlot -> ClassSkills / Shockwave / ElectricQuake
          ├─> ArenaCommands ──> ArenaStore / ArenaDraft
          ├─> ShockwaveVisuals ─> ShockwavePayload (S->C) + post effect
          └─> SliceRegistry ──> slices/* ──> duel/* (item effects) + ClassSkills

FlakeUiServer ──> LobbyRoom ──> DuelRuntime / LoadoutCommands
             ├─> LobbyActionPayload (C->S), LobbySnapshotPayload + HudStatePayload (S->C)

FlakeClient ──> AbilitySlotPayload / ShockwavePayload, G -> FlakeScreen -> BuildEditorScreen
FlakeUiClient ──> snapshot/hud cache; BuildEditorScreen <- HudStatePayload.toLoadout()
ShockwaveVisuals -> ShockwavePayload -> ShockwaveRenderState -> PostPassMixin -> shockwave.fsh
ElectricQuake (shared math) -> server hit timing + client wavefront (GroundLightning)
```

Напрямок залежностей: slices знають про `Loadout`, `SliceRegistry`, `DuelRuntime`; `DuelRuntime` знає про slices лише через `SliceRegistry`; клієнт не залежить від серверних класів стану, тільки від payload-ів і спільних math/animation-класів у `dev.flake.duel`. **[П]**


## 3. Предмети та реєстрація

- Базовий клас: `shared/FlakeActiveItem.java` — делегує `use`/`useOn`/`interactLivingEntity` у `activateServer(ServerPlayer, Entity)`; кулдаун перевіряється до активації; клієнтський `canConsumeOnClient` дозволяє пропустити ПКМ до щита в офхенді. **[П]**
- Конкретні предмети: `duel/Matadora.java`, `duel/DarkCube.java`, `duel/Gadgets.java` (smoke bomb, adrenaline). Реєстрація `Registry.register(BuiltInRegistries.ITEM, key, ...)` у статичному полі; звуки — `BuiltInRegistries.SOUND_EVENT`. **[П]**
- Ресурси: `src/main/resources/assets/flake/items/<id>.json`, `models/item/<id>.json`, `textures/`, `lang/`. **[П]**
- `SliceRegistry` install-класи лише описують metadata/kit-stack; сам Item-об'єкт реєструється в класі предмета. **[П]**

### Як додати новий active item
1. Додати значення в `Loadout.ActiveItem` (`build/Loadout.java`) з cost/title/description/category. **[П]**
2. Створити клас ефекту (за зразком `Matadora`/`DarkCube`/`Gadgets`): Item, серверна логіка, `stack()`, `register()`, `reset(ServerPlayer)`, перевірка `DuelRuntime.blocksAbility`. **[П]**
3. Створити `slices/<name>/<Name>Slice.java` з `ActiveItemSpec` (кулдаун/buff/offhand) і `Lifecycle`. **[П]**
4. Додати `XSlice.install()` у `SliceRegistry.bootstrap()`. **[П]**
5. Додати assets (`items/`, `models/item/`, текстуру, `lang`), за потреби sound event. **[П]**
6. Нічого не додавати в команди/редактор: `LoadoutCommands` і `EditorCatalog.choices()` ітерують `Loadout.ActiveItem.values()`. **[П]**
7. Для VFX: сервер шле `ShockwavePayload` (див. §7), клієнт додає гілку в `ShockwaveRenderState`. **[П]**

> Не плутати: `ACTIVE_ITEM_SLOTS = 2` — це два **предмети** (ПКМ), а не слоти здібностей H/R. **[П]**

### Як додати нову зброю
1. Значення в `Loadout.Weapon` (cost, damage, category, activeInfo). **[П]**
2. Slice з `WeaponSpec` (cooldownTicks, arrowCount, stackFactory) + `Lifecycle`. **[П]**
3. `install()` у `SliceRegistry.bootstrap()`.
4. Серверна логіка здібності зброї — за зразком `duel/ControlHammer.java` (стан, stun, cooldown). **[?]** деталі ControlHammer не читалися повністю.
5. Kit-видача автоматична: `DuelRuntime.prepareKit` бере `SliceRegistry.weapon(...)`, стріли — з `arrowCount`. **[П]**

### Як додати пасивний предмет
- Значення в `Loadout.PassiveItem` + `PassiveSpec` у `slices/passives/PassivesSlice.java` (`onApplyKit` / `onFightTick`). **[П]**
- Бойовий тік пасивів викликається з `DuelRuntime.tick()` лише для FIGHTING. **[П]**
- Leech/Thorns додатково мають серверні хуки в `ClassSkills.register()` (`AFTER_DAMAGE`). **[П]**

## 4. Здібності класів

- `ability/AbilitySlots.java`: enum `Ability` з `cooldownTicks`, `activeTicks`, `implemented`; `COUNT = 2`, слоти 1-based; `resolve(class, slot)` через `SliceRegistry.fighterClass`. **[П]**
- 4 класи (`Loadout.FighterClass`): CONCUSSED, BASTION, VAMPIRE, DEMOLITIONIST. **[П]**
- `slices/classes/ClassSlices.java` зіставляє класи з парами Ability і делегує кулдауни:
  - CONCUSSED → `GRAVITATIONAL_DISSONANCE`, `ELECTRIC_QUAKE` → `LoadoutCommands`.
  - BASTION/VAMPIRE/DEMOLITIONIST → `ability/ClassSkills.java`.
- `ClassSkills.cast(player, ability, slot)` — серверна перевірка `blocksAbility`, кулдаун, пошук цілі (конус + LOS), damage/knockback/ефекти; невдалий каст не витрачає кулдаун. **[П]**
- Concussed-здібності живуть у `build/LoadoutCommands.java`: charge `Shockwave` (22 тіки) + detonate; `ElectricQuake` покроково з тіків. **[П]**
- Клієнт: H/R → `AbilitySlotPayload` (`FlakeClient`) → серверний receiver у `LoadoutCommands.register()` → `activateSlot(...)`. **[П]**
- Гейт: `DuelRuntime.blocksAbility(id)` = stun або protected-not-fighting або countdown. **[П]**

### Як додати нову class ability
1. Значення `Ability` у `AbilitySlots` (назва, кулдаун, activeTicks, implemented=true). **[П]**
2. Серверна логіка: `ClassSkills` (для нових класів) або окремий handler. **[П]**
3. Прив'язка в `ClassSlices.install()` (slot1/slot2) і, за потреби, `cooldownRemaining/activeElapsed`. **[П]**
4. Гейти: `DuelRuntime.blocksAbility`, alive/spectator, клас/слот. **[П]**
5. VFX — окремий payload-phase або reuse наявного; HUD отримує кулдаун через `HudStatePayload`. **[П]**

## 5. Білди та редактор

`build/Loadout.java` — immutable record: клас, 4 броні, зброя, пасив, 2 активні предмети, `migrationConflicts`. **[П]**
- `BUDGET = 6`, `BASE_HEALTH = 50`, `cost()`, `errors()` (конфлікти міграції, дубль активного, перевищення бюджету), `stats()`, `withX()`-копіювання, `defaultLoadout()`. **[П]**
- v1-міграція: `LoadoutManager.importLegacy` + `LegacyImport`; невдалий імпорт не замінює поточний білд. **[П]**
- `LoadoutManager` — **session-only** (`HashMap<UUID, Loadout>`), очищається при disconnect/server stop; файлів білдів на диску немає. **[П]**

`build/LoadoutCommands.java`: **[П]**
- Статичний `LOADOUTS`; `/flake class|armor|slot|weapon|passive|item 1|2 ...`.
- `update()` відхиляє зміни при `DuelRuntime.locked`, валідовує `errors()`, при зміні класу — `onClassChanged`, скидає готовність, синхронізує UI.
- `/flake effect(s)`, `/flake duel stop|recover|join|leave|ready|not_ready|status`, `/flake dummy spawn|remove`.
- Старі команди `accessory|accslot|utility` явно відхиляються. **[П]**

`editor/EditorCatalog.java` — каталог вибору: `choices(slot, build)` будує candidate-Loadout-и з enum-ів, `comparison()` рахує стат-дельти, `command(slot, choice)` генерує `/flake ...`. **[П]**

Client editor:
- `FlakeClient` (G) → `FlakeScreen` → `BuildEditorScreen`. **[П]**
- `BuildEditorScreen`: 8 слотів + прев'ю + стат-стрічка; optimistic `draft`, надсилає команду через `player.connection.sendCommand`, у `tick()` звіряє з `HudStatePayload.toLoadout()`; блокується коли `hud.ready()` або активна фаза. **[П]**
- `ItemPickerScreen` — модальний вибір; `BuildPreview` — скін-прев'ю. **[?]** повний вміст не читався.

## 6. Duel, persistence, arena

Багатолобійна архітектура (переписано): **[П]**
- `ui/LobbyManager` — реєстр **незалежних кімнат**, прив'язаний до конкретного `MinecraftServer` (`INSTANCES`); `create/join/leave/delete/disconnect/shutdown`, авто-закриття порожніх кімнат, `visibleRooms(viewer)` (публічні + приватні, де ти owner/member/invited). Ліміт 128 кімнат.
- `ui/LobbyRoom` — owner, visibility (PUBLIC/PRIVATE), invite-сет, mode (DUEL/TEAM_FIGHT), arena, roster/readiness і **власний** `MatchRuntime`. Усі мутації повертають локалізовану помилку й перевіряють права owner.
- `duel/MatchRuntime` — **один матч на кімнату**: власні арена/учасники/відлік (`COUNTDOWN_TICKS = 60`)/таймер, `prepareKit`, recover, `finishRound`, `stop`. Кілька матчів працюють одночасно; гравець не може бути у двох (реєстр `roomId → MatchRuntime`).
- `duel/DuelRuntime` — статичний фасад без власного стану матчу: `create/remove/byRoom/all/of(player)` + per-player запити `protectedInventory/fighting/locked/blocksAbility/opponents/passiveOf/phase/timerTicks`; `install()` реєструє Fabric-події (damage/death/tick/join/disconnect/use/entity load).
- `duel/DuelRecovery` — глобальний per-player захист інвентаря + `RecoveryJournal` на конкретний сервер (`<world>/flake/recovery`), `resume(join/respawn)`, `recoverOnline`, `recover`.
- Потік матчу: `allReady()`/owner start → `beginCountdown()` (preflight арени, білдів, слотів, безпеки точок) → `launch()`:
  1. `DuelPreparation.run`: спочатку durable snapshot **усіх** гравців (`journal.create`), лише потім мутації/телепорт; збій → rollback.
  2. `prepareKit`: очищення інвентаря/ефектів, `PvpVitals`, ADVENTURE, броня/зброя/активні/пасив/стріли зі `SliceRegistry`, `DuelEquipment.temporary`.
  3. `match.startRound()`.
- FIGHTING: перевірка арени/режиму/дистанції, вимкнений ванільний реген, `PassiveHandler.onFightTick` (щотік; пасиви мусять бути ідемпотентними — інтервали тримаються per-player, напр. `PassivesSlice.regenerationDue`).
- Смерть → `finishRound`, повернення речей; cancel/disconnect/server-stop/leave → `stop()` + recover.
- `recover()`: читання `DuelPlayerState`, безпечна точка (з fallback), `state.restore`, тег-квитанція `flake_restored_<token>`, перевірка запису на диск через `loadPlayerData`, лише потім `journal.complete`.

`duel/DuelMatch.java` — стан `COUNTDOWN → FIGHTING → RESTORING → FINISHED|CANCELLED`, рахунок/winsNeeded, незмінні loadout-и матчу. **[П]**
`duel/DuelLobby.java` — незмінний контейнер складу/готовності (teams, teamSize, lock); `forceLeave` для дисконекту/виключення. **[П]**

Persistence:
- `InventorySnapshot` — копія слотів + selected slot, відновлення лише у порожній інвентар. **[П]**
- `DuelPlayerState` — NBT v1: позиція/вимір/fallback, items, effects, abilities, food, gamemode, health/absorption, XP, fire/air/frozen, token; валідація перед мутацією. **[П]**
- `RecoveryJournal` — write-ahead NBT (compressed), атомарний hard-link, `completed/<uuid>-<token>.nbt` архів. **[П]**
- `DuelEquipment` — маркер `CUSTOM_DATA.flake_duel_kit`; тимчасові предмети/стріли видаляються. **[П]**
- `build/LoadoutStore` — `<world>/flake/builds/<uuid>.properties` v1, `State {MISSING, LOADED, CORRUPT}`, atomic move; запис на диск **перед** публікацією в пам'ять. **[П]**
- `build/LoadoutManager` — ревізії білда, `apply(expectedRevision, desired)` → `OK/INVALID/STALE/SAVE_FAILED`, `updateChecked`. **[П]**
- `arena/ArenaRepository` — іменовані арени `<world>/flake/arenas/<name>.properties`; `default` пише legacy `flake/arena.properties`. **[П]**
- `arena/ArenaStore` — properties v1 (dimension, bounds, first/second/lobby), atomic move. **[П]**
- `arena/Arena.java` — record `dimension/bounds/firstSpawn/secondSpawn/lobby`, `Position`, `Bounds`. **[П]**

## 7. Клієнтський рендеринг і синхронізація gameplay ↔ VFX

Сервер (авторитет):
- `duel/ShockwaveVisuals.java` — єдина точка відправки візуальних фаз: `charge`, `play`, `playQuake`, `mask`, `crystal`, `bolt`, `impact`, `cancel`, `toggle`; `mask`/`crystal` зведені до спільного `buff(source, on, onPhase, offPhase, duration)`; розсилка гравцям у радіусі 48 блоків, `addPostEffect("flake:shockwave")`, тік-експірація. **[П]**
- Фази payload: `CHARGE`, `BURST`, `IMPACT`, `QUAKE`, `MASK_ON`, `MASK_OFF`, `CRYSTAL_ON`, `CRYSTAL_OFF`, `BOLT`, `CANCEL`, `CLEAR` (`ShockwavePayload`). **[П]**
- Крит Void Paladin: `VoidPaladin` шле `ShockwaveVisuals.crit` лише після підтвердженої шкоди (`ServerLivingEntityEvents.AFTER_DAMAGE`), у радіусі 48 блоків; ефект світовий (ядро, 3–7 спурів, шлейфи, бризки, імпульс), не екранна текстура. **[П]**
- Буф-візуали: `duel/MaskAnimation` (Матадора) і `duel/CrystalClawAnimation` (Кристалевий кіготь) — enter/plateau/exit конверти + `burst()` активації; `CrystalClaw.activate/tick/reset` шлють `CRYSTAL_ON/OFF`. **[П]**
- Коди стилю в `WaveStyles[i].z` (клієнт): 0 shell, 1 charge, 2 quake, 3 bolt, 4 оверлей власника Матадори, 5 аура Матадори, 6 оверлей власника Crystal Claw, 7 аура Crystal Claw, 8 світовий кривавий розрив криту Void Paladin (`critBurst`, `duel/CritAnimation`, 0.42 с). **[П]**
- Правило: сервер спершу застосовує damage/knockback/cooldown, потім шле payload. Клієнт не переоцінює цілі (коментар у `ShockwaveRenderState.writeBolt`). **[П]**

Бастіон (VFX rework): **[П]**
- `BastionAnimation` — damped assembly spring, compact Ram flash/suppression envelopes, Bulwark assembly/4 s plateau/0.62 s dissolution. Gameplay timing unchanged.
- `SHIELD_RAM[_SHIELD]`: `yaw` задає напрямок; `aim` — **нерухома серверна точка контакту**, `targetId >= 0` лише після успішного `hurtServer`. Промах не показує impact/suppression. Клієнт відстежує ціль тільки для пригнічення, а рух кастера — 5 world-space breadcrumbs із кроком 0.035 s та інерційним лагом.
- `BULWARK_ON/OFF/HIT`: `HIT.aim` — напрямок до джерела підтвердженої шкоди (ZERO для невідомого); локальна хвиля/пружна реакція найближчих пластин. Залишок absorption для внутрішнього світла читається з синхронізованого `LivingEntity`, не вигадується таймером.
- GLSL `bastionPlate` — точний перетин 11 півпросторів: товсті грановані щитові пластини з ребром, перламутровими шарами та erosion-dissolve. Ram — 6 частин клина, 3 інерційні ребра шлейфу, pressure facets/уламки; Bulwark — 12 пластин у двох ярусах, внутрішні підсилення, 6 наземних ключів. Геометрія видима також від першої особи; depth-test і spatial bounds, без raymarch/нового UBO/ванільних частинок як основи.

Упир (blood VFX): **[П]**
- `ClassSkills` зберігає чинні правила: Blood Lunge — обов'язкова ціль/LOS, негайний ривок, `skillDamage(4)`, незалежний `heal(1.5)`, cooldown 120 ticks; Bloodlust — Speed I/100 ticks, cooldown 300 ticks, чинний melee-hook `taken * 0.18` разом із Leech одним викликом `heal`.
- `ShockwavePayload` доповнено фазами `BLOOD_LUNGE`, `BLOODLUST_ON/OFF`, `BLOOD_SIPHON` без зміни wire-полів. Lunge: `aim` — фіксований контакт, `yaw` — напрямок, `targetId >= 0` лише при успішному damage. Siphon: `origin` — точка витягування, `sourceId` — отримувач, `aim` — його корпус, `yaw` — фактичний приріст HP. Siphon надсилається лише для активного Bloodlust у чинному melee-hook, після реального приросту здоров'я; не для Leech окремо, повного HP чи нерелевантної шкоди. OFF очищає також клієнтів, які вийшли з радіуса активації.
- `GhoulAnimation` — чиста математика таймінгів, конвертів та timestamped `Trail` (16 точок / 0.025 s; reset після teleport/stall). `client/GhoulRenderState.Actor` — один обмежений rig на `sourceId`, який поєднує ауру, ривок і до 2 одночасних siphon-подій у **одному** слоті існуючого UBO (style 12). Спільний бюджет — як раніше, 4 wave slots, UBO 15,136 bytes.
- Геометрія: 64 сегменти/8 bounds; основні мембрани й плечові кігті, 3 world-space стрічки ривка, криві витягування/поглинання, 2 асиметричні серпи контакту. GLSL — товсті анізотропні капсули з Hermite-згладжуванням, бордовим меніском/рубіновим ядром/темними прожилками, exact depth clipping; без raymarch чи billboard. Активна аура реагує на velocity/lag та синхронізований swing, OFF лишає лише коротке втягування/затухання. World/disconnect/CLEAR прибирають rig разом зі звичайними waves.
- `tools/vfx_probe.py --ghoul-fixtures <dir>` відтворює зняті з реального клієнта `.ubo` + camera `.json` для GPU-перевірок, не дублює процедурну CPU-анімацію в Python.

Клієнт:
- `FlakeClient` приймає `ShockwavePayload` → `ShockwaveRenderState.receive`; очищення при disconnect/зміні світу. **[П]**
- `ShockwaveRenderState` збирає `Wave`-и (`buffKind` 0/1/2 замість прапорця `mask`), рахує один `float[]` UBO (матриці, impact, позиції/стилі хвиль, частинки, сегменти, bounds), `uniforms()` викликається з міксина; `writeBuff` пише типи 4/5 (Матадора) або 6/7 (Crystal Claw). **[П]**
- `client/mixin/PostPassMixin` інжектиться в `PostPass.addToFrame`, знаходить uniform-буфер `ShockwaveConfig`, перестворює за потреби, мапить і записує float-и. **[П]**
- `src/client/resources/assets/flake/post_effect/shockwave.json`: 2 pass-и — `flake:post/shockwave` (sampler `In` + `Depth`) → blit назад у `minecraft:main`. **[П]**
- GLSL: `src/client/resources/assets/flake/shaders/post/shockwave.fsh` (755 рядків): аналітичні перетини без raymarch; `maskAura` (фіолетові куби), `crystalAura`+`iceShard` (кристалічні шарди Crystal Claw, палітра `CRYSTAL_DEEP/BRIGHT/TINT`), оверлей власника перемикає палітру через `maskCrystal`. **[П]**
- `GroundLightning` — детермінована процедурна геометрія (GROUPS=8, PER_GROUP=8, MOTES=32, DURATION=0.96s) для quake. **[П]**
- `VfxTimeline` — elapsed-час на `System.nanoTime`, пауза підтримується; не залежить від FPS. **[П]**
- `ElectricQuake.wavefrontDistance(t)` використовується **і** сервером для хітів, **і** клієнтом для візуального фронту — спільна модель синхронізації. **[П]**

### Додати custom GLSL VFX
1. Новий `.fsh`/`.vsh` у `src/client/resources/assets/flake/shaders/...`. **[?]** структура program-шейдерів не перевірена.
2. За потреби — post-effect JSON у `assets/flake/post_effect/` з uniform-блоком. **[П]** за зразком shockwave.
3. Дані з gameplay → `ShockwavePayload`/новий payload → client render state → UBO → `PostPassMixin`/новий міксин. **[П]**
4. Уникати ванільних частинок як основного VFX; тримати один спільний UBO та CPU-масив. **[П]**

## 8. Мережа (payload-и)

| Payload | Напрям | Реєстрація | Призначення |
|---|---|---|---|
| `LobbyActionPayload` (v2) | C→S | `FlakeUiServer` | 15 дій: query/create/join/leave/ready/not_ready/switch_team/start/stop/settings/kick/invite/uninvite/transfer/delete; rate limit 2 тіки |
| `LobbySnapshotPayload` (v2) | S→C | `FlakeUiServer` | власна кімната + список видимих кімнат + онлайн-гравці з назвою їхньої кімнати + список арен (кожні 10 тіків) |
| `HudStatePayload` | S→C | `FlakeUiServer` | білд, кулдауни/активні фази, фаза/таймер матчу, роль (NONE/SPECTATOR/PARTICIPANT), `blockReason`, `itemCooldowns`, статуси з amplifier/∞, `buildRevision`, `LoadState` (кожні 2 тіки) |
| `BuildApplyPayload` | C→S | `FlakeUiServer` | атомарна заміна білда + очікувана ревізія |
| `BuildResultPayload` | S→C | `FlakeUiServer` | OK/INVALID/STALE/SAVE_FAILED/LOCKED + текст |
| `AbilitySlotPayload` | C→S | `LoadoutCommands` | натиск H/R, слот 1..2 |
| `ShockwavePayload` | S→C | `ShockwaveVisuals` | візуальні фази VFX |

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

- Ефекти предметів: `FlakeActiveItem.activateServer` + локальний `DuelRuntime.blocksAbility`. **[П]**
- Здібності: `AbilitySlots.resolve` → `LoadoutCommands.activateSlot` / `ClassSkills.cast`. **[П]**
- Білд: `Loadout.errors()` + `LoadoutCommands.update` (`DuelRuntime.locked`). **[П]**
- Дуель: `DuelRuntime.preflight` (живий, не spectator, без транспорту, інвентар/крафт-сітка чисті, білд валідний, точки безпечні, відстань ≥ 2 блоки). **[П]**
- Відновлення: перевірка токена/власника/версії у `DuelPlayerState.read` і `RecoveryJournal`, durable-запис перед зміною стану. **[П]**

## 11. Навігація за задачею

| Task category | Entry points | Related components |
|---|---|---|
| New active item | `build/Loadout.java` (`ActiveItem`), `slices/<x>/<X>Slice.java`, `SliceRegistry.bootstrap()` | `shared/FlakeActiveItem`, `duel/<Item>.java`, `assets/flake/items/*`, `lang` |
| New weapon | `Loadout.Weapon`, новий `WeaponSpec`/slice | `DuelRuntime.prepareKit`, `duel/ControlHammer.java`, `assets` |
| Class ability | `ability/AbilitySlots.java`, `ClassSkills.java` | `slices/classes/ClassSlices.java`, `LoadoutCommands.activateSlot`, `HudStatePayload` |
| Gameplay balancing | константи в `Shockwave`, `ElectricQuake`, `ClassSkills`, `Matadora`, `DarkCube`, `Loadout` | `HudStatePayload` (кулдауни), `EditorCatalog.comparison` |
| Custom GLSL VFX | `src/client/resources/assets/flake/shaders/**`, `post_effect/*.json` | `ShockwaveRenderState`, `PostPassMixin`, `ShockwaveVisuals`, `ShockwavePayload` |
| Fullscreen post-processing | `assets/flake/post_effect/shockwave.json` | `PostPassMixin`, `ShockwaveRenderState.uniforms()`, `shaders/post/shockwave.fsh` |
| UI changes | `client/ui/*`, `FlakeScreen`, `FlakeHudOverlay` | `FlakeUiClient`, `HudStatePayload`, `UiTheme` |
| Build editor | `client/editor/BuildEditorScreen`, `ItemPickerScreen`, `BuildPreview` | `EditorCatalog`, `EditorLayout`, `/flake ...` commands, `HudStatePayload.toLoadout` |
| Server networking | `ui/FlakeUiServer`, `ui/*Payload`, `ability/AbilitySlotPayload`, `duel/ShockwavePayload` | `PayloadTypeRegistry`, `ServerPlayNetworking` |
| Duel logic | `duel/DuelRuntime` (фасад), `duel/MatchRuntime` (один матч), `DuelMatch`, `DuelLobby`, `DuelPreparation` | `DuelRecovery`, `RecoveryJournal`, `DuelPlayerState`, `ArenaRepository`, `PvpVitals` |
| Lobby rooms | `ui/LobbyManager`, `ui/LobbyRoom` | `LobbyActionPayload`, `LobbySnapshotPayload`, `MatchRuntime`, `ArenaRepository` |
| Inventory persistence | `duel/InventorySnapshot`, `DuelPlayerState`, `RecoveryJournal` | `DuelRuntime.recover`, `DuelEquipment` |
| Arena management | `arena/Arena`, `ArenaRepository`, `ArenaStore`, `ArenaCommands`, `ArenaDraft` | `MatchRuntime.preflight`, `<world>/flake/arenas/<name>.properties` (`default` → legacy `arena.properties`) |

## 12. Невідоме / неперевірене

- `FlakeScreen`/`LobbyManageScreen` — нові екрани; runtime-перевірка верстки на різних GUI scale не проводилась. **[?]**
- `ItemPickerScreen`, `BuildPreview`, `EditorLayout`, `FlakeHudOverlay`, `FlakeItemHud` — лише часткове або поверхневе читання. **[?]**
- `HudStatePayload.toLoadout()` — деталі реконструкції білда на клієнті. **[?]**
- `ControlHammer`, `ElectricQuake`, `PvpVitals`, `EnergyMotes`, `*Animation` — константи/математика перевірені лише частково. **[?]**
- 5 duel-міксинів (`Duel*SafetyMixin`) та `HudTabOverlayMixin` — вміст не читався. **[?]**
- `ArenaCommands`/`ArenaDraft` — повний workflow створення арени. **[?]**
- Вміст `shockwave.fsh` і всіх інших GLSL (окрім факту наявності) не аналізувався. **[?]**
- Хто саме споживає `SliceRegistry.cooldownSeconds/buffSeconds` (ймовірно `FlakeItemHud`). **[?]**
- Повний перелік assets/sounds/lang та відповідність назв предметів. **[?]**
- Одночасні матчі: архітектурно підтримано (`LobbyManager` + `MatchRuntime` per room), але runtime-сценарій із двома паралельними боями ще не прогнано. **[?]**

## 13. Правило оновлення цієї карти

- Після архітектурної зміни оновлювати **лише відповідний розділ**, не переписувати документ.
- Нові факти позначати **[П]** лише після читання актуального коду; інакше — **[?]**.
- Якщо шляхи/назви розійшлися з кодом — вірити коду й виправити карту.
