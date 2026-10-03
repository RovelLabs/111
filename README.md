# Visual Client

Визуальный клиент Minecraft **1.20.1** на Fabric: HUD, эффекты, косметика.
Без игровых преимуществ — см. [исследование](docs/research-visual-clients.md).

## Запуск для разработки
1. Установить **JDK 17 или 21** и **IntelliJ IDEA**.
2. Открыть папку проекта в IntelliJ, дождаться импорта Gradle (первый раз качается Minecraft — несколько минут).
3. Запустить `./gradlew runClient` (Windows: `gradlew.bat runClient`) или конфигурацию **Minecraft Client**.
4. В игре: слева сверху Watermark, справа список модулей.

Сборка jar: `./gradlew build` → `build/libs/visual-client-<версия>.jar`.
Его можно положить в `mods` любого лаунчера с Fabric 1.20.1 и Fabric API.

## Структура
```
src/main/java/dev/visualclient/
├── VisualClient.java        точка входа, подключение хуков к шине событий
├── event/                   EventBus, @Subscribe, события Tick/Render2D/Render3D/Key
├── module/                  Module, Category, ModuleManager (бинды, singleplayer-защита)
│   └── modules/hud/         Watermark, ModuleList
├── setting/                 Boolean / Number / Mode / Color настройки
├── config/                  ConfigManager — профили в .minecraft/visualclient/configs
└── mixin/                   KeyboardMixin (бинды)
```

## Как добавить модуль
1. Класс в `module/modules/<категория>/`, наследник `Module`.
2. Настройки — полями через `add(new ...Setting(...))`.
3. Обработчики — методы с `@Subscribe`, принимающие событие (`Render2DEvent`, `Render3DEvent`, `TickEvent`, `KeyEvent`).
4. Зарегистрировать в конструкторе `ModuleManager`.
5. Если модуль нечестен в мультиплеере — переопределить `isSingleplayerOnly()` → `true`.
