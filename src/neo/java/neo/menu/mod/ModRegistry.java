package neo.menu.mod;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 検出済み Mod の登録簿 (データのみ。構築は {@link FabricModDiscovery})。 */
public final class ModRegistry {
    /** 全 Mod (ダミー親を含む)。 */
    public static final Map<String, Mod> MODS = new ConcurrentHashMap<>();
    /** 親を持たない Mod。 */
    public static final Map<String, Mod> ROOT_MODS = new ConcurrentHashMap<>();
    /** 親 Mod → 子 Mod 一覧。 */
    public static final ListMultimap<Mod, Mod> PARENT_MAP = Multimaps.synchronizedListMultimap(LinkedListMultimap.create());

    private ModRegistry() {}
}
