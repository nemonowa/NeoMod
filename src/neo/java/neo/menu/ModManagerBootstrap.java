package neo.menu;

import neo.menu.config.ModManagerConfigManager;
import neo.menu.service.ModCount;
import neo.menu.util.ModManagerPlatform;

import java.util.HashSet;
import java.util.Set;

/** Mod Menu の初期化手順 (ローダー非依存)。ローダー側は自分の {@link ModManagerPlatform} を渡して呼ぶだけ。 */
public final class ModManagerBootstrap {
    private ModManagerBootstrap() {}

    public static void init(ModManagerPlatform platform) {
        ModManagerPlatform.install(platform);
        ModManagerConfigManager.addSaveListener(ModCount::clearCache);

        Set<String> modpackMods = new HashSet<>();
        platform.loadIntegrations(modpackMods);
        platform.discoverMods(modpackMods);
        ModManagerEventHandler.register();
    }
}
