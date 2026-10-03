package dev.pulseclient;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.TickEvent;
import dev.pulseclient.gui.ClickGuiScreen;
import dev.pulseclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Автопроверка для CI (включается -Dpulseclient.selftest=true): из главного меню создаёт мир,
 * включает все модули, открывает ClickGUI и пишет в лог, что всё прошло без ошибок.
 */
public final class SelfTest {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private int titleTicks;
    private int worldTicks;
    private boolean worldRequested;
    private boolean done;

    public static boolean enabled() {
        return Boolean.getBoolean("pulseclient.selftest");
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (done) return;
        if (mc.world == null) {
            if (mc.currentScreen instanceof TitleScreen && !worldRequested && ++titleTicks > 100) {
                worldRequested = true;
                PulseClient.LOGGER.info("Pulse self-test: создаю мир");
                LevelInfo info = new LevelInfo("PulseSelfTest", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart("PulseSelfTest", info,
                        new GeneratorOptions(12345L, false, false), WorldPresets::createDemoOptions);
            }
            return;
        }
        if (mc.player == null) return;
        worldTicks++;
        if (worldTicks == 60) {
            int count = 0;
            for (Module m : PulseClient.get().getModuleManager().getAll()) {
                if (m.isHidden() || m.isEnabled()) continue;
                m.setEnabled(true);
                count++;
            }
            mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
            PulseClient.LOGGER.info("Pulse self-test: в мире, включено модулей: {}", count);
        }
        if (worldTicks == 140) {
            mc.setScreen(new ClickGuiScreen());
            PulseClient.LOGGER.info("Pulse self-test: ClickGUI открыт");
        }
        if (worldTicks == 220) {
            long enabled = PulseClient.get().getModuleManager().getAll().stream().filter(Module::isEnabled).count();
            PulseClient.LOGGER.info("Pulse self-test OK: модулей включено {}, ClickGUI {}", enabled,
                    mc.currentScreen instanceof ClickGuiScreen ? "открыт" : "закрыт");
            done = true;
        }
    }
}
