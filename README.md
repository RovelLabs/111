# Visual Client

Визуальный клиент Minecraft **1.20.1** на Fabric: HUD, эффекты, косметика.
Без игровых преимуществ — см. [исследование](docs/research-visual-clients.md).

## Установка (для игроков)
1. Скачать **`VisualClient.exe`** из [последнего релиза](https://github.com/RovelLabs/111/releases/latest) и запустить.
2. Нажать **«Установить»**. Лаунчер сам скачает всё нужное (~600 МБ): Java 17, Minecraft 1.20.1, Fabric,
   Fabric API и клиент. На рабочем столе появятся папка `Visual Client` и ярлык.
3. Ввести ник, выбрать память и нажать **«Играть»**.

Официальный лаунчер Minecraft не нужен. Лаунчер **обновляется сам** при запуске, а новая версия клиента
докачивается при нажатии «Играть». «Проверить файлы» — докачать повреждённые или удалённые файлы.

Папка `Visual Client` на рабочем столе:
```
Visual Client.exe   лаунчер
game/               миры, настройки, mods/ (клиент и Fabric API)
minecraft/          файлы игры: versions/, libraries/, assets/
runtime/            Java
logs/               лог последнего запуска игры
launcher.json       ник, память, установленная версия
```

## Как выпускаются версии
Релиз выходит **автоматически при каждом пуше** в основную ветку (кроме правок только в `docs/` и `.md`).
Номер версии — `<mod_version>.<номер сборки>`, например `0.1.7`.

GitHub Actions (`.github/workflows/release.yml`):
1. собирает клиент и `manifest.json` (версии Minecraft, Fabric, Fabric API, контрольная сумма клиента);
2. собирает `VisualClient.exe` (Python + CustomTkinter → PyInstaller) со своей иконкой;
3. **проверяет на чистой Windows**: ставит всё с нуля и запускает игру с клиентом (`launcher/ci_test.py`);
4. только если проверка прошла — публикует релиз.

## Запуск для разработки
1. Установить **JDK 17 или 21** и **IntelliJ IDEA**.
2. Открыть папку проекта в IntelliJ, дождаться импорта Gradle (первый раз качается Minecraft — несколько минут).
3. Запустить `./gradlew runClient` (Windows: `gradlew.bat runClient`) или конфигурацию **Minecraft Client**.
4. В игре: слева сверху Watermark, справа список модулей.

Сборка jar: `./gradlew build` → `build/libs/visual-client-<версия>.jar`.
Для проверки лаунчера без сборки exe: `pip install -r launcher/requirements.txt && python launcher/app.py`.

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
launcher/                    лаунчер VisualClient.exe (Python + CustomTkinter)
```

## Как добавить модуль
1. Класс в `module/modules/<категория>/`, наследник `Module`.
2. Настройки — полями через `add(new ...Setting(...))`.
3. Обработчики — методы с `@Subscribe`, принимающие событие (`Render2DEvent`, `Render3DEvent`, `TickEvent`, `KeyEvent`).
4. Зарегистрировать в конструкторе `ModuleManager`.
5. Если модуль нечестен в мультиплеере — переопределить `isSingleplayerOnly()` → `true`.
