package neo.menu.mod;

import neo.menu.util.ModManagerInfo;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** {@link ModRegistry#MODS} の親子関係から ROOT_MODS / PARENT_MAP を組み立てる (ローダー非依存)。 */
public final class ModHierarchy {
    private ModHierarchy() {}

    /** 親 Mod が見つからないときに、代わりの「ダミー親」を作る。作れなければ null。 */
    @FunctionalInterface
    public interface DummyParentFactory {
        @Nullable Mod create(Mod child, String parentId);
    }

    public static void build(DummyParentFactory dummyParentFactory) {
        Map<String, Mod> dummyParents = new HashMap<>();

        // Initialize parent map
        HashSet<String> modParentSet = new HashSet<>();
        for (Mod mod : ModRegistry.MODS.values()) {
            String parentId = mod.getParent();
            if (parentId == null) {
                ModRegistry.ROOT_MODS.put(mod.getId(), mod);
                continue;
            }

            Mod parent;
            modParentSet.clear();
            while (true) {
                parent = ModRegistry.MODS.getOrDefault(parentId, dummyParents.get(parentId));
                if (parent == null) {
                    parent = dummyParentFactory.create(mod, parentId);
                    if (parent != null) {
                        dummyParents.put(parentId, parent);
                    }
                }

                parentId = parent != null ? parent.getParent() : null;
                if (parentId == null) {
                    // It will most likely end here in the first iteration
                    break;
                }

                if (modParentSet.contains(parentId)) {
                    ModManagerInfo.LOGGER.warn("Mods contain each other as parents: {}", modParentSet);
                    parent = null;
                    break;
                }

                modParentSet.add(parentId);
            }

            if (parent == null) {
                ModRegistry.ROOT_MODS.put(mod.getId(), mod);
                continue;
            }

            ModRegistry.PARENT_MAP.put(parent, mod);
        }

        ModRegistry.MODS.putAll(dummyParents);
    }
}
