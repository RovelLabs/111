package dev.pulseclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/** Простые 3D-примитивы в мире (цветные треугольники без текстур). */
public final class Render3D {
    private Render3D() {
    }

    public static BufferBuilder begin(VertexFormat.DrawMode mode) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(mode, VertexFormats.POSITION_COLOR);
        return buffer;
    }

    public static void end(BufferBuilder buffer) {
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int argb) {
        buffer.vertex(matrix, x, y, z)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24)
                .next();
    }
}
