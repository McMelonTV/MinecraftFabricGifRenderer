package ing.boykiss.gifrender.gui;

import com.madgag.gif.fmsware.GifDecoder;
import com.mojang.blaze3d.systems.RenderSystem;
import ing.boykiss.gifrender.ModMain;
import ing.boykiss.gifrender.mixin.AccessorNativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
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
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Optional;

public class GifDrawable implements Drawable {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int frameCount;
    private final GifDecoder gifDecoder = new GifDecoder();
    private int lastFrame = 0;
    private long lastFrameTime = 0;
    private final HashMap<Integer, Identifier> frameTextures = new HashMap<>();
    private final HashMap<Integer, Integer> frameDurations = new HashMap<>();

    public GifDrawable(Identifier gif, int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager rm = client.getResourceManager();
        if (rm == null) {
            throw new IllegalStateException("ResourceManager is null");
        }
        Optional<Resource> optionalResource = rm.getResource(gif);
        if (optionalResource.isEmpty()) {
            throw new IllegalStateException("Resource not found: " + gif);
        }
        Resource gifResource = optionalResource.get();
        try {
            gifDecoder.read(gifResource.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        frameCount = gifDecoder.getFrameCount();

        for (int i = 0; i < frameCount; i++) {
            frameDurations.put(i, gifDecoder.getDelay(i));
        }
    }

    // yoinked from https://github.com/0x3C50/Renderer/blob/master/src/main/java/me/x150/renderer/util/RendererUtils.java#L166
    private static @NotNull NativeImageBackedTexture bufferedImageToNIBT(@NotNull BufferedImage bi) {
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

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime > frameDurations.get(lastFrame)) {
            lastFrameTime = currentTime;
            lastFrame++;
            if (lastFrame >= frameCount) {
                lastFrame = 0;
            }
        }
        int frame = lastFrame;

        if (!frameTextures.containsKey(frame)) {
            BufferedImage image = gifDecoder.getFrame(frame);
            NativeImageBackedTexture texture = bufferedImageToNIBT(image);
            Identifier i = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("gifrender", texture);
            frameTextures.put(frame, i);
        }

        Identifier i = frameTextures.get(frame);
        MinecraftClient.getInstance().getTextureManager().bindTexture(i);

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bufferBuilder.vertex(matrix, x, y + height, 0).texture(0, 1);
        bufferBuilder.vertex(matrix, x + width, y + height, 0).texture(1, 1);
        bufferBuilder.vertex(matrix, x + width, y, 0).texture(1, 0);
        bufferBuilder.vertex(matrix, x, y, 0).texture(0, 0);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, i);

        BuiltBuffer buffer = bufferBuilder.end();
        BufferRenderer.drawWithGlobalProgram(buffer);
    }
}
