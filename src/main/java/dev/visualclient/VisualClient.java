package dev.visualclient;

import dev.visualclient.config.ConfigManager;
import dev.visualclient.event.EventBus;
import dev.visualclient.event.events.Render2DEvent;
import dev.visualclient.event.events.Render3DEvent;
import dev.visualclient.event.events.TickEvent;
import dev.visualclient.module.ModuleManager;
import dev.visualclient.module.modules.hud.ModuleList;
import dev.visualclient.module.modules.hud.Watermark;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Точка входа клиента: создаёт ядро и подключает хуки игры к шине событий. */
public final class VisualClient implements ClientModInitializer {
    public static final String NAME = "Visual Client";
    public static final String VERSION = FabricLoader.getInstance()
            .getModContainer("visualclient")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");

    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static VisualClient instance;

    private EventBus eventBus;
    private ModuleManager moduleManager;
    private ConfigManager configManager;

    public static VisualClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        eventBus = new EventBus();
        moduleManager = new ModuleManager(eventBus);
        configManager = new ConfigManager(FabricLoader.getInstance().getGameDir().resolve("visualclient"), moduleManager);

        if (!configManager.load(ConfigManager.DEFAULT)) {
            // Первый запуск — включаем базовый HUD.
            moduleManager.get(Watermark.class).setEnabled(true);
            moduleManager.get(ModuleList.class).setEnabled(true);
        }

        // Хуки, которые даёт Fabric API. Клавиатура подключена через KeyboardMixin.
        ClientTickEvents.END_CLIENT_TICK.register(client -> eventBus.post(new TickEvent()));
        HudRenderCallback.EVENT.register((context, tickDelta) -> eventBus.post(new Render2DEvent(context, tickDelta)));
        WorldRenderEvents.LAST.register(context ->
                eventBus.post(new Render3DEvent(context.matrixStack(), context.camera(), context.tickDelta())));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> configManager.save(ConfigManager.DEFAULT));

        LOGGER.info("{} {} загружен, модулей: {}", NAME, VERSION, moduleManager.getAll().size());
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
