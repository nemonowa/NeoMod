package neo.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Fabric API (ClientTickEvents / ScreenEvents) の代替。Mixin から呼ばれる最小限のイベント置き場。 */
public final class NeoClientEvents {
    private NeoClientEvents() {}

    @FunctionalInterface
    public interface ScreenInit {
        void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
    }

    private static final List<Consumer<Minecraft>> END_CLIENT_TICK = new CopyOnWriteArrayList<>();
    private static final List<ScreenInit> AFTER_SCREEN_INIT = new CopyOnWriteArrayList<>();
    private static final List<Runnable> END_EXTRACTION = new CopyOnWriteArrayList<>();

    public static void registerEndClientTick(Consumer<Minecraft> listener) {
        END_CLIENT_TICK.add(listener);
    }

    public static void registerAfterScreenInit(ScreenInit listener) {
        AFTER_SCREEN_INIT.add(listener);
    }

    /** LevelExtractor#extract の末尾 (LevelExtractionEvents.END_EXTRACTION の代替)。 */
    public static void registerEndExtraction(Runnable listener) {
        END_EXTRACTION.add(listener);
    }

    // --- 以下は Mixin 専用 ---
    public static void fireEndExtraction() {
        for (Runnable l : END_EXTRACTION) l.run();
    }

    public static void fireEndClientTick(Minecraft client) {
        for (Consumer<Minecraft> l : END_CLIENT_TICK) l.accept(client);
    }

    public static void fireAfterScreenInit(Minecraft client, Screen screen, int w, int h) {
        for (ScreenInit l : AFTER_SCREEN_INIT) l.afterInit(client, screen, w, h);
    }
}
