package neo.fabric;

import neo.menu.mod.Mod;
import neo.menu.mod.ModHierarchy;
import neo.menu.mod.ModRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Set;

/** Fabric Loader から全 Mod を列挙して {@link ModRegistry} を組み立てる。 */
public final class FabricModDiscovery {
    private FabricModDiscovery() {}

    /** @param modpackMods モドパック由来として扱う Mod ID */
    public static void discover(Set<String> modpackMods) {
        for (ModContainer modContainer : FabricLoader.getInstance().getAllMods()) {
            Mod mod = new FabricMod(modContainer, modpackMods);
            ModRegistry.MODS.put(mod.getId(), mod);
        }

        // 親が見つからない Mod には、fabric.mod.json の情報からダミー親を作る
        ModHierarchy.build((mod, parentId) -> mod instanceof FabricMod fabricMod ? new FabricDummyParentMod(fabricMod, parentId) : null);
    }
}
