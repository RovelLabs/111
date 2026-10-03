# Визуал-клиенты Minecraft: как они устроены

Цель документа — разобрать, **как технически устроены отдельные клиенты Minecraft**
(Nursultan, Celestial, Expensive, Wexside, а из легальных — Lunar, Badlion, Feather, LabyMod),
и взять из них **архитектуру** для собственного клиента, в котором будут **только визуальные
функции**. Функции, дающие преимущество в игре (боевые, движения, ESP/X-Ray и т.п.), в проект
не входят.

---

## 1. Что это такое и чем отличается от сборки модов

| | Сборка модов | Визуал / PvP-клиент (Lunar, Feather) | Чит-клиент (Nursultan, Celestial) |
|---|---|---|---|
| Как ставится | Forge/Fabric + папка `mods` | Свой лаунчер (.exe), всё «в одной коробке» | Свой лаунчер / загрузчик |
| Функции | Разрозненные моды разных авторов | Единое ядро с модулями | Единое ядро с модулями |
| Интерфейс | У каждого мода свой | Единое меню (ClickGUI / Mod Menu), единый HUD-редактор | ClickGUI, HUD, конфиги |
| Настройки | Файлы конфигов каждого мода | Общие профили/конфиги клиента | Конфиги, иногда облачные |
| Пример функций | — | Кейстроуксы, FPS, броня, косметика, зум, кастомный прицел | То же + KillAura, Fly, ESP, X-Ray… |

Ключевое понимание: **по архитектуре чит-клиент и визуал-клиент — это одно и то же**.
И там и там есть: лаунчер → модифицированный Minecraft → ядро клиента → менеджер модулей →
система событий → рендер (GUI, HUD, 3D-эффекты) → конфиги. Разница только в том, *какие модули*
реализованы. Мы берём каркас, но реализуем только визуальные модули.

---

## 2. Три способа сделать «отдельный клиент»

### A. Форк декомпилированного Minecraft (MCP / MCP-Reborn) — так делают Nursultan, Expensive и большинство СНГ-клиентов 1.16.5

1. Инструментом **MCP-Reborn** (или старым MCP для 1.8/1.12) игра декомпилируется в исходный
   Java-код с читаемыми именами.
2. Разработчик **прямо правит исходники игры**: в `Minecraft.java` создаёт ядро клиента, в
   `IngameGui.render()` вызывает событие отрисовки HUD, в `WorldRenderer` — событие 3D-рендера и т.д.
3. Собирается собственный `.jar`, который кладётся в `versions/<Client>/` как отдельная версия
   и запускается своим лаунчером.

Плюсы: полный контроль, нет зависимости от Forge/Fabric, быстро писать «в лоб».
Минусы:
- **Юридически плохо**: распространяется изменённый код Mojang (нарушение EULA), лицензия
  MCP-Reborn прямо запрещает публиковать сгенерированный код.
- Привязка к одной версии игры; переход на новую версию = заново переносить все правки.
- Совместимость с модами (Sodium, OptiFine, Iris) — практически нулевая.

Поэтому у таких клиентов обычно ещё есть обфускация, лицензирование по HWID и закрытый backend —
всё это нам не нужно.

### B. Мод-загрузчик + Mixin, упакованный в свой лаунчер (рекомендуемый путь)

Клиент пишется как **Fabric-мод** (ядро + модули), а точечные изменения игры делаются через
**Mixin** — библиотеку, которая во время загрузки классов вставляет наш код в методы Minecraft
(`@Inject(method = "render", at = @At("TAIL"))` и т.п.). Свой лаунчер скачивает чистый Minecraft
у Mojang, Fabric Loader и наш `.jar` — пользователь видит «отдельный клиент», а не сборку.

Плюсы:
- Мы **не распространяем код Mojang** — только свой код (так же работают Feather, LabyMod 4).
- Обновление на новые версии значительно дешевле.
- Можно встроить Sodium/Iris для FPS и шейдеров.
- Fabric API уже даёт готовые хуки: `HudRenderCallback`, `WorldRenderEvents`, `ClientTickEvents`,
  `KeyBindingHelper` — многие вещи делаются вообще без своих Mixin.

### C. Свой загрузчик / Java-agent (Lunar Client)

Lunar использует собственную систему патчинга (**Ichor** + Mixin): лаунчер на Electron запускает
JVM с агентами, которые на лету патчат классы игры. Это тот же подход B, только со своим
загрузчиком вместо Fabric. Имеет смысл, только когда команда большая и нужна поддержка десятков
версий; для старта — избыточно.

**Вывод: делаем вариант B (Fabric + Mixin + свой лаунчер).**

---

## 3. Из чего состоит клиент

```
┌──────────────── Лаунчер (.exe) ────────────────┐
│ аккаунты · новости · выбор версии · обновления │
│ скачивание Java, Minecraft, Fabric, клиента    │
└───────────────────────┬────────────────────────┘
                        │ запускает JVM с нужными аргументами
┌───────────────────────▼────────────────────────┐
│ Minecraft + Fabric Loader                       │
│  └─ Клиент (наш .jar)                           │
│      ├─ Mixins  ──► генерируют события          │
│      ├─ EventBus                                │
│      ├─ ModuleManager ─► Module ─► Settings     │
│      ├─ Render: шрифты, шейдеры, 2D/3D-утилиты  │
│      ├─ UI: ClickGUI, HUD-редактор, главное меню│
│      ├─ ConfigManager (JSON-профили)            │
│      └─ (опц.) сеть косметики                   │
└─────────────────────────────────────────────────┘
```

### 3.1 Лаунчер
- Технологии: Electron / Tauri (веб-интерфейс) или C# WPF/Avalonia, или JavaFX.
- Что делает:
  - **Скачивание игры** по официальному манифесту Mojang
    (`piston-meta.mojang.com/mc/game/version_manifest_v2.json`): клиентский jar, библиотеки,
    ассеты, натив-библиотеки LWJGL.
  - **Скачивание Java** (Adoptium/Temurin или Mojang runtime) нужной версии.
  - **Fabric Loader** + наш клиент + (опционально) Sodium/Iris с Modrinth.
  - **Авторизация**: Microsoft OAuth → Xbox Live → XSTS → Minecraft Services (официальный путь,
    нужен свой Azure App ID); для одиночной игры достаточно оффлайн-ника.
  - Формирование аргументов запуска (`-cp`, main class `net.fabricmc.loader.impl.launch.knot.KnotClient`,
    `--gameDir`, `--assetsDir`, `--accessToken`…).
  - Автообновление клиента, новости, профили.
- Готовые библиотеки, чтобы не писать с нуля: `minecraft-launcher-core` (Node.js),
  `CmlLib.Core` (C#), `HMCL`/`PrismLauncher` (как образец, open source).

### 3.2 Ядро клиента
- **Точка входа** — `ClientModInitializer.onInitializeClient()`: создаются менеджеры, грузится конфиг.
- **EventBus** — шина событий. Mixin-хуки в игре публикуют события, модули на них подписаны:
  - `TickEvent` — каждый тик клиента;
  - `Render2DEvent` — после отрисовки HUD (DrawContext/MatrixStack, partialTicks);
  - `Render3DEvent` — в мире после сущностей (матрицы камеры, partialTicks);
  - `KeyEvent`, `ScreenOpenEvent`, `EntityRenderEvent`, `ParticleEvent`, `HandRenderEvent`…
  Можно взять готовую (Orbit от Meteor) или написать свою на 100 строк.
- **Module**: имя, категория, бинд, флаг `enabled`, `onEnable()/onDisable()`, список настроек.
- **Settings**: `BooleanSetting`, `NumberSetting` (слайдер), `ModeSetting` (выпадающий список),
  `ColorSetting` (палитра), `BindSetting`, `MultiBooleanSetting`. Каждая умеет сериализоваться в JSON.
- **ConfigManager**: `.minecraft/<client>/configs/*.json`, автосохранение, импорт/экспорт.
- **Команды** (опционально): `.bind`, `.config load`, `.toggle` через перехват чата.

Минимальный пример модуля (Fabric, 1.21):

```java
public final class JumpCircles extends Module {
    private final NumberSetting radius = add(new NumberSetting("Радиус", 1.0, 0.3, 3.0));
    private final ColorSetting color   = add(new ColorSetting("Цвет", 0xFF8A5CF6));
    private final List<Circle> circles = new ArrayList<>();

    public JumpCircles() { super("Jump Circles", Category.VISUAL); }

    @Subscribe
    private void onTick(TickEvent e) {
        var p = mc.player;
        if (p != null && p.isOnGround() && p.prevY < p.getY()) // начало прыжка
            circles.add(new Circle(p.getPos(), System.currentTimeMillis()));
        circles.removeIf(c -> c.age() > 1500);
    }

    @Subscribe
    private void onRender3D(Render3DEvent e) {
        for (Circle c : circles)
            RenderUtil.drawRing(e.matrices(), c.pos(), radius.get() * c.progress(), color.withAlpha(1 - c.progress()));
    }
}
```

### 3.3 Mixin-хуки (где именно вклиниваются)
| Что нужно | Класс игры (Yarn, 1.21) | Зачем |
|---|---|---|
| HUD | `InGameHud#render` (или `HudRenderCallback`) | Render2DEvent, скрытие ванильных элементов |
| 3D в мире | `WorldRenderer#render` (или `WorldRenderEvents.LAST`) | Render3DEvent |
| Тик | `MinecraftClient#tick` | TickEvent |
| Клавиши | `Keyboard#onKey` | бинды модулей |
| Рука/предмет | `HeldItemRenderer#renderFirstPersonItem` | ViewModel, кастомная анимация удара |
| Камера | `Camera#update`, `GameRenderer#getFov` | зум, плавная камера, aspect ratio |
| Погода/небо/туман | `WorldRenderer`, `BackgroundRenderer`, `ClientWorld#getTimeOfDay` | Ambience, Time Changer, NoFog |
| Частицы | `ParticleManager#addParticle` | множитель/цвет частиц удара |
| Модель игрока | `PlayerEntityRenderer`, `LivingEntityRenderer` | косметика, цвет хита |
| Главное меню | `TitleScreen#init` / замена экрана | своё главное меню |

### 3.4 Рендер — самая важная часть визуал-клиента
То, что делает Nursultan/Celestial «красивыми», — это не модули, а **рендер-движок**:
- **Скруглённые прямоугольники, тени, градиенты, glow** — рисуются фрагментными **GLSL-шейдерами**
  (SDF-функция скруглённого прямоугольника), а не текстурами.
- **Blur (размытие фона)** — копия кадра во Framebuffer → двухпроходный Gaussian / Kawase blur →
  вырезка по маске.
- **Свои шрифты** — TTF (например Inter, Montserrat) рендерятся через атлас глифов или
  **MSDF**-шрифты (чёткие при любом масштабе).
- **Анимации** — всё на easing-функциях (`easeOutCubic`, `easeOutBack`) с привязкой ко времени,
  а не к FPS.
- **3D-утилиты** — линии, кольца, конусы (China Hat), ленты (Trails) через `BufferBuilder`/
  `Tessellator` + свой `RenderLayer`/`RenderPipeline`.
- **Важно по версиям**: в 1.16.5 частично ещё работает старый GL-стиль (`GlStateManager`,
  `MatrixStack`); с 1.17 — только core-шейдеры; в 1.21.5+ Mojang переписала рендер на
  абстракцию `RenderPipeline`/GpuDevice — утилиты рендера придётся писать под конкретную ветку.

### 3.5 UI
- **ClickGUI** — панели-категории (Render, HUD, World, Misc…), в них модули, по ПКМ раскрываются
  настройки. Либо современный вариант «окно с сайдбаром» (как в Celestial/Expensive).
- **HUD-редактор** — экран, где элементы HUD (Watermark, ArmorHUD, Potions, Keystrokes,
  TargetHUD, Coordinates, FPS/Ping, Music) перетаскиваются мышью, позиции сохраняются в конфиг.
- **Своё главное меню и загрузочный экран**, свой экран мультиплеера (опционально).
- **Уведомления** (toast в углу) о включении модулей.

### 3.6 Косметика с синхронизацией (опционально, этап 2)
Чтобы плащи/шапки нашего клиента видели **другие игроки этого же клиента** (как в Lunar), нужен
свой сервер: клиент по WebSocket сообщает «я UUID X, на мне косметика Y», получает данные о
других игроках вокруг. Без сервера косметику видит только сам игрок.

---

## 4. Какие модули можно, а какие нет

Граница простая: **модуль не должен давать информацию или действие, недоступные ванильному
игроку**. Тогда клиент честный, и его можно использовать в том числе на серверах, разрешающих
визуальные моды.

**✅ Делаем (чистая визуалка / удобство):**
- HUD: Watermark, ArrayList (список включённых модулей), ArmorHUD, Potions, Keystrokes, CPS,
  FPS/Ping, координаты, часы, Music-плеер, Scoreboard-кастомизация.
- Мир: Ambience / Time Changer (только у себя), Custom Fog, Custom Sky, Weather Changer,
  Block Overlay (контур выделенного блока), Custom Crosshair.
- Игрок: ViewModel (положение руки), анимации удара (1.7 animations), Hit Color, China Hat,
  Jump Circles, Trails, Wings/Capes, Custom Hand, Aspect Ratio, Zoom (как OptiFine).
- Эффекты: Hit Particles, Kill Effects, Motion Blur, Glow-обводка своего ника, Damage Numbers.
- Удобства: Toggle Sprint (разрешён почти везде), Auto GG, Screenshot-менеджер, FPS-буст через
  Sodium.

**⚠️ Только в одиночном мире (отключаются, если `mc.isInSingleplayer() == false`):**
- FullBright, X-Ray-подобная подсветка руд, Freecam, Freelook, NoFog в пещерах.

**❌ Не делаем вообще:** ESP/Tracers/NameTags сквозь стены, KillAura/AimAssist/Reach/Velocity,
Fly/Speed/Scaffold, любые модификации пакетов, обходы античитов, HWID-спуфинг.

---

## 5. Выбор версии игры

| Версия | Плюсы | Минусы |
|---|---|---|
| **1.16.5** | Стандарт СНГ-PvP (FunTime, HolyWorld и др.), все популярные клиенты на ней | Старая Java 8/16, мало современных библиотек, Fabric-экосистема старая |
| **1.20.1** | Много модов, стабильный Fabric API | Не самая новая |
| **1.21.x** | Актуальная, Sodium/Iris, новые API | Рендер-API меняется почти каждый минор |

Рекомендация: **начать с одной версии** (выбрать по целевой аудитории — для СНГ-PvP это обычно
1.16.5 или 1.21.x), архитектуру ядра держать независимой от версии, а всё, что трогает Minecraft,
изолировать в слое `render/` и `mixin/`, чтобы потом портировать.

---

## 6. Рекомендуемый стек

| Часть | Технология |
|---|---|
| Язык клиента | Java 17/21 (+ возможно Kotlin) |
| Загрузчик | Fabric Loader + Fabric API, сборка — Fabric Loom (Gradle) |
| Маппинги | Yarn или официальные Mojang mappings |
| Патчинг игры | Mixin (SpongePowered), MixinExtras |
| События | Своя EventBus или Orbit |
| Конфиги | Gson |
| Рендер | Свои GLSL-шейдеры + утилиты над BufferBuilder |
| FPS | Sodium (+ Iris для шейдеров) как зависимости |
| Лаунчер | Tauri или Electron (React UI) + `minecraft-launcher-core`, либо C# + CmlLib.Core |

---

## 7. План MVP

1. **Каркас Fabric-мода**: entry point, EventBus, Module/Setting/ModuleManager, ConfigManager.
2. **Mixin-хуки**: Tick, Render2D, Render3D, Key.
3. **Рендер-утилиты**: шрифты (TTF), скруглённые прямоугольники через шейдер, blur, easing.
4. **ClickGUI** + **HUD-редактор**.
5. **10–15 визуальных модулей**: Watermark, ArrayList, Keystrokes, ArmorHUD, Potions, Custom
   Crosshair, Ambience, Hit Color, ViewModel, China Hat, Jump Circles, Trails, Zoom, Block Overlay.
6. **Своё главное меню**.
7. **Лаунчер**: скачивание игры/Java/Fabric/клиента, оффлайн-ник, затем Microsoft-вход.
8. Этап 2: облачные конфиги, сервер косметики, автообновления.

---

## 8. Юридические моменты
- **Не распространять код Mojang** (декомпилированный/изменённый jar) — поэтому Fabric+Mixin,
  а не MCP-форк. Лаунчер скачивает игру с серверов Mojang сам.
- Для онлайн-игры — только лицензионный вход через Microsoft. Оффлайн-режим — для одиночной игры.
- Нельзя использовать в названии/логотипе «Minecraft» как бренд продукта, монетизация — по
  Minecraft Usage Guidelines (косметика за деньги допустима с ограничениями, продажа геймплейных
  преимуществ — нет).
- Визуальные модули не должны давать игровое преимущество — тогда клиент не попадает в категорию
  читов на большинстве серверов.

---

## Источники
- [MCP-Reborn (Hexeption)](https://github.com/Hexeption/MCP-Reborn) — инструмент декомпиляции, которым пользуются клиенты на базе форка игры; лицензия запрещает публиковать сгенерированный код.
- [Nursultan (Grokipedia)](https://grokipedia.com/page/Nursultan_Minecraft_client), [nursultan-nullified](https://github.com/unleg1t/nursultan-nullified) — описание устройства клиента 1.16.5 (обфусцированные классы, встроенные viaversion/discord-rpc, backend).
- [Lunar Client × ReplayMod mixins](https://github.com/LunarClient/ReplayModMixins), [lunar-launcher-inject](https://github.com/Nilsen84/lunar-launcher-inject) — Lunar использует Mixin и Electron-лаунчер, запускающий JVM с агентами.
- [Lunar: Sodium and Iris](https://www.lunarclient.com/news/sodium-and-iris) — встраивание сторонних модов в клиент.
- [Minecraft modding — Wikipedia](https://en.wikipedia.org/wiki/Minecraft_modding) — история MCP и мод-загрузчиков.
