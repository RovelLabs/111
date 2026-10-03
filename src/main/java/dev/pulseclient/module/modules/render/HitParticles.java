package dev.pulseclient.module.modules.render;

import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.setting.ModeSetting;
import dev.pulseclient.setting.NumberSetting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;

import java.util.concurrent.ThreadLocalRandom;

/** Дополнительные частицы при ударе — только у вас на экране. */
public final class HitParticles extends Module {
    private final ModeSetting type = add(new ModeSetting("Частицы", "Криты",
            "Криты", "Магия", "Сердца", "Огонь", "Души", "Звёзды", "Тотем"));
    private final NumberSetting amount = add(new NumberSetting("Количество", 12, 2, 40, 1));

    public HitParticles() {
        super("Hit Particles", "Красивые частицы при ударе", Category.RENDER);
        // Событие Fabric нельзя отписать, поэтому проверяем isEnabled() внутри
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (isEnabled() && world.isClient && player == mc.player) spawn(entity);
            return ActionResult.PASS;
        });
    }

    private ParticleEffect particle() {
        return switch (type.get()) {
            case "Магия" -> ParticleTypes.ENCHANTED_HIT;
            case "Сердца" -> ParticleTypes.HEART;
            case "Огонь" -> ParticleTypes.FLAME;
            case "Души" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Звёзды" -> ParticleTypes.END_ROD;
            case "Тотем" -> ParticleTypes.TOTEM_OF_UNDYING;
            default -> ParticleTypes.CRIT;
        };
    }

    private void spawn(Entity target) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ParticleEffect effect = particle();
        for (int i = 0; i < amount.getInt(); i++) {
            double x = target.getX() + rnd.nextDouble(-0.4, 0.4);
            double y = target.getY() + rnd.nextDouble(0.2, target.getHeight());
            double z = target.getZ() + rnd.nextDouble(-0.4, 0.4);
            mc.particleManager.addParticle(effect, x, y, z,
                    rnd.nextDouble(-0.15, 0.15), rnd.nextDouble(0, 0.2), rnd.nextDouble(-0.15, 0.15));
        }
    }
}
