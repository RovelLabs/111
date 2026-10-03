package dev.pulseclient.module.modules.render;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.Render3DEvent;
import dev.pulseclient.event.events.TickEvent;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.ColorUtil;
import dev.pulseclient.render.Render3D;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.NumberSetting;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Расходящиеся светящиеся круги на земле при прыжке. */
public final class JumpCircles extends Module {
    private static final int SEGMENTS = 64;

    private final NumberSetting size = add(new NumberSetting("Размер", 1.2, 0.5, 2.5, 0.1));
    private final NumberSetting duration = add(new NumberSetting("Длительность, с", 1.0, 0.4, 2.5, 0.1));
    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround = true;

    private record Circle(Vec3d pos, long start) {
    }

    public JumpCircles() {
        super("Jump Circles", "Круги на земле при прыжке", Category.RENDER);
    }

    @Subscribe
    private void onTick(TickEvent event) {
        if (mc.player == null) return;
        boolean onGround = mc.player.isOnGround();
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0.2) {
            circles.add(new Circle(mc.player.getPos(), System.currentTimeMillis()));
        }
        wasOnGround = onGround;
        long life = (long) (duration.get() * 1000);
        circles.removeIf(c -> System.currentTimeMillis() - c.start() > life);
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (circles.isEmpty()) return;
        Vec3d cam = event.camera().getPos();
        long life = (long) (duration.get() * 1000);
        for (Circle c : circles) {
            float t = Math.min(1f, (System.currentTimeMillis() - c.start()) / (float) life);
            float eased = 1f - (float) Math.pow(1 - t, 3);
            float outer = size.getFloat() * eased, inner = outer * 0.82f;
            int alpha = (int) (220 * (1 - t));

            MatrixStack ms = event.matrices();
            ms.push();
            ms.translate(c.pos().x - cam.x, c.pos().y - cam.y + 0.02, c.pos().z - cam.z);
            Matrix4f m = ms.peek().getPositionMatrix();
            BufferBuilder buffer = Render3D.begin(VertexFormat.DrawMode.TRIANGLE_STRIP);
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = Math.PI * 2 * i / SEGMENTS;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                int color = Theme.wave(i / (float) SEGMENTS);
                Render3D.vertex(buffer, m, cos * outer, 0, sin * outer, ColorUtil.withAlpha(color, alpha));
                Render3D.vertex(buffer, m, cos * inner, 0, sin * inner, ColorUtil.withAlpha(color, alpha / 4));
            }
            Render3D.end(buffer);
            ms.pop();
        }
    }
}
