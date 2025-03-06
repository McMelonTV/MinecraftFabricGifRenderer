package ing.boykiss.gifrender;

import ing.boykiss.gifrender.gui.GifDrawable;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ModMain implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(ModMain.class);

    @Override
    public void onInitializeClient() {
        AtomicReference<Optional<GifDrawable>> gifDrawable = new AtomicReference<>(Optional.empty());

        //have to do this because ResourceManager is not available until the client is started
        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            if (gifDrawable.get().isEmpty()) {
                gifDrawable.set(Optional.of(new GifDrawable(Identifier.of("gifrender:textures/flash-run.gif"), 0, 0, 300, 150)));
            }
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            gifDrawable.get().ifPresent(gif -> gif.render(context, 0, 0, 0));
        });
    }
}
