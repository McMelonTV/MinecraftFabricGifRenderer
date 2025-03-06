package ing.boykiss.gifrender;

import ing.boykiss.gifrender.gui.GifDrawable;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ModMain implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AtomicReference<Optional<GifDrawable>> gifDrawable = new AtomicReference<>(Optional.empty());

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            if (gifDrawable.get().isEmpty()) {
                gifDrawable.set(Optional.of(new GifDrawable(Identifier.of("gifrender:textures/gif.gif"), 0, 0, 30, 50)));
            }
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            gifDrawable.get().ifPresent(gif -> gif.render(context, 0, 0, 0));
        });
    }
}
