package neo.fabric;

import neo.menu.service.ConfigScreens;
import neo.menu.util.ModManagerInfo;
import neo.menu.util.ModManagerPlatform;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.NullScreenFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarFile;

/** {@link ModManagerPlatform} の Fabric Loader 実装。 */
final class FabricPlatform extends ModManagerPlatform {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isModFile(Path file) {
        try (JarFile jarFile = new JarFile(file.toFile())) {
            return jarFile.getEntry("fabric.mod.json") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** 他 Mod が提供する "modmenu" エントリポイント (設定画面ファクトリ / モドパックバッジ) を取り込む。 */
    @Override
    public void loadIntegrations(Set<String> modpackMods) {
        // Ignore deprecations, they're from Quilt Loader being in the dev env
        //noinspection deprecation
        FabricLoader.getInstance().getEntrypointContainers("modmenu", ModMenuApi.class).forEach(entrypoint -> {
            //noinspection deprecation
            ModMetadata metadata = entrypoint.getProvider().getMetadata();
            String modId = metadata.getId();
            try {
                ModMenuApi api = entrypoint.getEntrypoint();
                ConfigScreenFactory<?> factory = api.getModConfigScreenFactory();
                if (!(factory instanceof NullScreenFactory<?>)) {
                    ConfigScreens.register(modId, factory::create);
                }
                ConfigScreens.addProvider(() -> toFunctions(api.getProvidedConfigScreenFactories()));
                api.attachModpackBadges(modpackMods::add);
            } catch (Throwable e) {
                ModManagerInfo.LOGGER.error("Mod {} provides a broken implementation of ModMenuApi", modId, e);
            }
        });
    }

    @Override
    public void discoverMods(Set<String> modpackMods) {
        FabricModDiscovery.discover(modpackMods);
    }

    private static Map<String, Function<Screen, Screen>> toFunctions(Map<String, ConfigScreenFactory<?>> factories) {
        Map<String, Function<Screen, Screen>> functions = new HashMap<>();
        factories.forEach((modId, factory) -> functions.put(modId, factory::create));
        return functions;
    }
}
