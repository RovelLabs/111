package dev.pulseclient.gui;

import dev.pulseclient.PulseClient;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Ставит иконку Pulse на окно игры (вместо травяного блока). */
public final class WindowIcon {
    private static final int[] SIZES = {16, 32, 48, 64, 128};

    private WindowIcon() {
    }

    public static void apply() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        List<ByteBuffer> buffers = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(SIZES.length, stack);
            for (int i = 0; i < SIZES.length; i++) {
                BufferedImage image = read("/assets/pulseclient/icons/icon_" + SIZES[i] + ".png");
                ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                buffers.add(pixels);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int argb = image.getRGB(x, y);
                        pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                    }
                }
                pixels.flip();
                images.position(i).width(image.getWidth()).height(image.getHeight()).pixels(pixels);
            }
            images.position(0);
            GLFW.glfwSetWindowIcon(handle, images);
        } catch (Exception e) {
            PulseClient.LOGGER.warn("Не удалось поставить иконку окна: {}", e.toString());
        } finally {
            buffers.forEach(MemoryUtil::memFree);
        }
    }

    private static BufferedImage read(String path) throws Exception {
        try (InputStream in = WindowIcon.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("нет ресурса " + path);
            return ImageIO.read(in);
        }
    }
}
