# Visual Client

Визуальный клиент Minecraft **1.20.1** на Fabric: HUD, эффекты, косметика.
Без игровых преимуществ — см. [исследование](docs/research-visual-clients.md).

## Установка (для игроков)
1. Скачать `VisualClientInstaller.exe` из [последнего релиза](https://github.com/RovelLabs/111/releases/latest)
   (или запустить `install.bat` из репозитория — он сам скачает свежий установщик и откроет его).
2. Запустить и нажать **«Установить»**: на рабочем столе появится папка `Visual Client` (папка игры),
   в лаунчер Minecraft добавится профиль **Visual Client** с Fabric 1.20.1.
3. Открыть официальный лаунчер Minecraft, выбрать профиль «Visual Client», нажать «Играть».

Обновление: снова запустить установщик и нажать **«Обновить»** — он сверится с последним релизом
на GitHub и заменит клиент (и при необходимости Fabric / Fabric API).

## Как выпускаются версии
Релиз выходит **автоматически при каждом пуше** в основную ветку (кроме правок только в `docs/` и `.md`).
Номер версии — `<mod_version>.<номер сборки>`, например `0.1.7`; `mod_version` в `gradle.properties`
меняют, когда нужно поднять «крупную» часть версии.

GitHub Actions (`.github/workflows/release.yml`) собирает и выкладывает в релиз:
- `visual-client-<версия>.jar` — клиент;
- `VisualClientInstaller.exe` — установщик;
- `extras.zip` — файлы для папки клиента: всё из `package/` + `Обновить клиент.bat` (копия `install.bat`);
- `manifest.json` — что и откуда качать установщику (версии Minecraft, Fabric, Fabric API, контрольные суммы).

Кнопка «Обновить» сравнивает установленную версию с последним релизом и докачивает то, что изменилось.

## Запуск для разработки
Самый простой способ — дважды кликнуть **`run-dev.bat`**: он сам найдёт Java 17+ или скачает
Java 21 в папку `.jdk` (без прав администратора) и запустит игру с клиентом. Ничего ставить заранее
не нужно. `run-dev.bat build` — собрать jar.

Через IDE:
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
package/                     файлы, которые установщик кладёт в папку клиента
installer/                   установщик (Python + CustomTkinter → .exe через PyInstaller)
```

## Как добавить модуль
1. Класс в `module/modules/<категория>/`, наследник `Module`.
2. Настройки — полями через `add(new ...Setting(...))`.
3. Обработчики — методы с `@Subscribe`, принимающие событие (`Render2DEvent`, `Render3DEvent`, `TickEvent`, `KeyEvent`).
4. Зарегистрировать в конструкторе `ModuleManager`.
5. Если модуль нечестен в мультиплеере — переопределить `isSingleplayerOnly()` → `true`.
