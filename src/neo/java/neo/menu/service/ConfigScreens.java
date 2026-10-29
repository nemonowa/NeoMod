package neo.menu.service;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/** Mod ごとの設定画面ファクトリの登録簿。 */
public final class ConfigScreens {
    private static final Map<String, Function<Screen, Screen>> FACTORIES = new ConcurrentHashMap<>();
    /** 他 Mod の設定画面を肩代わりして提供する供給元 (自 Mod の登録より低優先)。 */
    private static final List<Supplier<Map<String, Function<Screen, Screen>>>> PROVIDERS = new CopyOnWriteArrayList<>();

    private ConfigScreens() {}

    public static void register(String modId, Function<Screen, Screen> factory) {
        FACTORIES.put(modId, factory);
    }

    public static void addProvider(Supplier<Map<String, Function<Screen, Screen>>> provider) {
        PROVIDERS.add(provider);
    }

    public static boolean hasConfigScreen(String modId) {
        return getFactory(modId) != null;
    }

    public static @Nullable Screen getConfigScreen(String modId, Screen parent) {
        Function<Screen, Screen> factory = getFactory(modId);
        return factory != null ? factory.apply(parent) : null;
    }

    private static @Nullable Function<Screen, Screen> getFactory(String modId) {
        if (ModManagerConfig.HIDDEN_CONFIGS.getValue().contains(modId)) {
            return null;
        }

        for (Supplier<Map<String, Function<Screen, Screen>>> provider : PROVIDERS) {
            Map<String, Function<Screen, Screen>> provided = provider.get();
            if (!provided.isEmpty()) {
                provided.forEach(FACTORIES::putIfAbsent);
            }
        }

        return FACTORIES.get(modId);
    }
}
