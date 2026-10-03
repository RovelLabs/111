package dev.visualclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.visualclient.module.Module;
import dev.visualclient.module.ModuleManager;
import dev.visualclient.setting.Setting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Профили настроек в .minecraft/visualclient/configs/*.json.
 * Профиль "default" загружается при запуске и сохраняется при выходе.
 */
public final class ConfigManager {
    public static final String DEFAULT = "default";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private final ModuleManager modules;

    public ConfigManager(Path clientDir, ModuleManager modules) {
        this.configDir = clientDir.resolve("configs");
        this.modules = modules;
    }

    public void save(String name) {
        JsonObject root = new JsonObject();
        JsonObject modulesJson = new JsonObject();
        for (Module module : modules.getAll()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty("enabled", module.isEnabled());
            moduleJson.addProperty("key", module.getKey());
            JsonObject settingsJson = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settingsJson.add(setting.getName(), setting.toJson());
            }
            moduleJson.add("settings", settingsJson);
            modulesJson.add(module.getName(), moduleJson);
        }
        root.add("modules", modulesJson);

        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file(name), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            System.err.println("[VisualClient] Не удалось сохранить конфиг " + name + ": " + e.getMessage());
        }
    }

    /** @return false, если профиля нет или он повреждён. */
    public boolean load(String name) {
        Path file = file(name);
        if (!Files.exists(file)) return false;

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            System.err.println("[VisualClient] Повреждён конфиг " + name + ": " + e.getMessage());
            return false;
        }

        JsonObject modulesJson = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module module : modules.getAll()) {
            JsonObject moduleJson = modulesJson.getAsJsonObject(module.getName());
            if (moduleJson == null) continue;

            JsonObject settingsJson = moduleJson.has("settings") ? moduleJson.getAsJsonObject("settings") : new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                JsonElement value = settingsJson.get(setting.getName());
                if (value == null) continue;
                try {
                    setting.fromJson(value);
                } catch (Exception e) {
                    setting.reset(); // значение неверного типа — откатываемся к стандартному
                }
            }
            if (moduleJson.has("key")) module.setKey(moduleJson.get("key").getAsInt());
            if (moduleJson.has("enabled")) module.setEnabled(moduleJson.get("enabled").getAsBoolean());
        }
        return true;
    }

    public List<String> list() {
        if (!Files.isDirectory(configDir)) return List.of();
        try (Stream<Path> files = Files.list(configDir)) {
            List<String> names = new ArrayList<>();
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .forEach(n -> names.add(n.substring(0, n.length() - ".json".length())));
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path file(String name) {
        return configDir.resolve(name + ".json");
    }
}
