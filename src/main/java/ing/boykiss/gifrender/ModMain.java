package ing.boykiss.gifrender;

import com.madgag.gif.fmsware.GifDecoder;
import ing.boykiss.gifrender.mixin.AccessorNativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Optional;

public class ModMain implements ClientModInitializer {

    boolean init = false;
    MinecraftClient client = MinecraftClient.getInstance();
    ResourceManager rm = null;
    Optional<Resource> optionalResource = Optional.empty();
    Resource resource = null;
    GifDecoder decoder = new GifDecoder();
    HashMap<Integer, Identifier> textures = new HashMap<>();

    // yoinked from https://github.com/0x3C50/Renderer/blob/master/src/main/java/me/x150/renderer/util/RendererUtils.java#L166
    public static @NotNull NativeImageBackedTexture bufferedImageToNIBT(@NotNull BufferedImage bi) {
        // argb from BufferedImage is little endian, alpha is actually where the `a` is in the label
        // rgba from NativeImage (and by extension opengl) is big endian, alpha is on the other side (abgr)
        // thank you opengl
        int ow = bi.getWidth();
        int oh = bi.getHeight();
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, ow, oh, false);
        @SuppressWarnings("DataFlowIssue") long ptr = ((AccessorNativeImage) (Object) image).getPointer();
        IntBuffer backingBuffer = MemoryUtil.memIntBuffer(ptr, image.getWidth() * image.getHeight());
        int off = 0;
        Object _d;
        WritableRaster raster = bi.getRaster();
        ColorModel colorModel = bi.getColorModel();
        int nbands = raster.getNumBands();
        int dataType = raster.getDataBuffer().getDataType();
        _d = switch (dataType) {
            case DataBuffer.TYPE_BYTE -> new byte[nbands];
            case DataBuffer.TYPE_USHORT -> new short[nbands];
            case DataBuffer.TYPE_INT -> new int[nbands];
            case DataBuffer.TYPE_FLOAT -> new float[nbands];
            case DataBuffer.TYPE_DOUBLE -> new double[nbands];
            default -> throw new IllegalArgumentException("Unknown data buffer type: " + dataType);
        };

        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                raster.getDataElements(x, y, _d);
                int a = colorModel.getAlpha(_d);
                int r = colorModel.getRed(_d);
                int g = colorModel.getGreen(_d);
                int b = colorModel.getBlue(_d);
                int abgr = a << 24 | b << 16 | g << 8 | r;
                backingBuffer.put(abgr);
            }
        }
        NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
        tex.upload();
        return tex;
    }

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (rm == null) {
                rm = client.getResourceManager();
            }
            if (rm != null && !init) {
                optionalResource = rm.getResource(Identifier.of("gifrender", "textures/gui/test.gif"));
                resource = optionalResource.orElse(null);
                if (resource == null) {
                    return;
                }
                try {
                    decoder.read(resource.getInputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                init = true;
            }

            int frameCount = decoder.getFrameCount();
            int frame = (int) (System.currentTimeMillis() / 100) % frameCount;
            System.out.println("frame = " + frame + " frameCount = " + frameCount);

            if (!textures.containsKey(frame)) {
                BufferedImage image = decoder.getFrame(frame);
                NativeImageBackedTexture texture = bufferedImageToNIBT(image);
                Identifier i = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("gifrender", texture);
                textures.put(frame, i);
            }

            Identifier i = textures.get(frame);

            int light = 15728880;

            VertexConsumer vertexConsumer = context.getVertexConsumers().getBuffer(RenderLayer.getGuiOverlay());
            MinecraftClient.getInstance().getTextureManager().bindTexture(i);
            MatrixStack matrices = context.getMatrices();
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();

            vertexConsumer.vertex(matrix4f, 0.0F, 498.0F, -0.01F).color(255, 255, 255, 255).texture(0.0F, 1.0F).light(light);
            vertexConsumer.vertex(matrix4f, 498.0F, 498.0F, -0.01F).color(255, 255, 255, 255).texture(1.0F, 1.0F).light(light);
            vertexConsumer.vertex(matrix4f, 498.0F, 0.0F, -0.01F).color(255, 255, 255, 255).texture(1.0F, 0.0F).light(light);
            vertexConsumer.vertex(matrix4f, 0.0F, 0.0F, -0.01F).color(255, 255, 255, 255).texture(0.0F, 0.0F).light(light);

            context.draw();
        });
    }
}
