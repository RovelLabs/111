package dev.pulseclient.module.modules.render;

import dev.pulseclient.event.Subscribe;
import dev.pulseclient.event.events.Render3DEvent;
import dev.pulseclient.module.Category;
import dev.pulseclient.module.Module;
import dev.pulseclient.render.ColorUtil;
import dev.pulseclient.render.Render3D;
import dev.pulseclient.render.Theme;
import dev.pulseclient.setting.BooleanSetting;
import dev.pulseclient.setting.NumberSetting;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** «Китайская шляпа» — светящийся конус над головой. */
public final class ChinaHat extends Module {
    private static final int SEGMENTS = 48;

    private final NumberSetting radius = add(new NumberSetting("Радиус", 0.6, 0.3, 1.0, 0.05));
    private final NumberSetting height = add(new NumberSetting("Высота", 0.3, 0.1, 0.6, 0.05));
    private final NumberSetting opacity = add(new NumberSetting("Прозрачность", 0.55, 0.1, 1.0, 0.05));
    private final BooleanSetting others = add(new BooleanSetting("На других игроках", false));

    public ChinaHat() {
        super("China Hat", "Светящийся конус над головой (вид от 3-го лица)", Category.RENDER);
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        for (PlayerEntity player : mc.world.getPlayers()) {
            boolean self = player == mc.player;
            if (!self && !others.get()) continue;
            if (self && mc.options.getPerspective().isFirstPerson()) continue;
            if (player.isInvisible()) continue;
            draw(event, player);
        }
    }

    private void draw(Render3DEvent event, PlayerEntity player) {
        Vec3d pos = player.getLerpedPos(event.tickDelta());
        Vec3d cam = event.camera().getPos();
        MatrixStack ms = event.matrices();
        ms.push();
        ms.translate(pos.x - cam.x, pos.y - cam.y + player.getHeight() + 0.03, pos.z - cam.z);
        float yaw = player.getYaw(event.tickDelta());
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        Matrix4f m = ms.peek().getPositionMatrix();

        int alpha = (int) (255 * opacity.get());
        float r = radius.getFloat(), h = height.getFloat();
        BufferBuilder buffer = Render3D.begin(VertexFormat.DrawMode.TRIANGLE_FAN);
        Render3D.vertex(buffer, m, 0, h, 0, ColorUtil.withAlpha(0xFFFFFFFF, Math.min(255, alpha + 40)));
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / SEGMENTS;
            int color = ColorUtil.withAlpha(Theme.wave(i / (float) SEGMENTS), alpha);
            Render3D.vertex(buffer, m, (float) (Math.cos(angle) * r), 0, (float) (Math.sin(angle) * r), color);
        }
        Render3D.end(buffer);
        ms.pop();
    }
}
