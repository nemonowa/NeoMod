package neo.forge;

import neo.menu.mod.Mod;
import neo.menu.mod.ModHierarchy;
import neo.menu.mod.ModRegistry;
import neo.menu.service.ConfigScreens;
import neo.menu.util.ModManagerPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarFile;

/** {@link ModManagerPlatform} の Forge 実装。 */
final class ForgePlatform extends ModManagerPlatform {
    /** Forge の拡張ポイントから集めた設定画面 (最初に必要になった時点で1度だけ作る)。 */
    private Map<String, Function<Screen, Screen>> forgeConfigScreens;

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path modsDir() {
        return FMLPaths.MODSDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public boolean isModFile(Path file) {
        try (JarFile jarFile = new JarFile(file.toFile())) {
            return jarFile.getEntry("META-INF/mods.toml") != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Forge には Fabric の "modmenu" エントリポイントに相当するものがないので、
     * 各 Mod が Forge 標準の拡張ポイント (ConfigScreenFactory) で登録した設定画面を使う。
     * モドパックバッジを他 Mod から受け取る手段は無い (modpackMods は空のまま)。
     */
    @Override
    public void loadIntegrations(Set<String> modpackMods) {
        ConfigScreens.addProvider(this::forgeConfigScreens);
    }

    @Override
    public void discoverMods(Set<String> modpackMods) {
        for (IModInfo info : ModList.getMods()) {
            Mod mod = new ForgeMod(info);
            ModRegistry.MODS.put(mod.getId(), mod);
        }

        // mods.toml には親子関係の情報がないので、ダミー親は作らず全 Mod が ROOT になる
        ModHierarchy.build((child, parentId) -> null);
    }

    private Map<String, Function<Screen, Screen>> forgeConfigScreens() {
        if (forgeConfigScreens == null) {
            Map<String, Function<Screen, Screen>> screens = new HashMap<>();
            for (IModInfo info : ModList.getMods()) {
                ConfigScreenHandler.getScreenFactoryFor(info)
                        .ifPresent(factory -> screens.put(info.getModId(), parent -> factory.apply(Minecraft.getInstance(), parent)));
            }
            forgeConfigScreens = screens;
        }
        return forgeConfigScreens;
    }
}
