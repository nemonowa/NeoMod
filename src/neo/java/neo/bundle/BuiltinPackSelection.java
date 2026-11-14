package neo.bundle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import neo.menu.config.ModManagerConfig;

/**
 * 同梱のリソースパックを「最初から有効・外せる」にする (クライアントのみ)。
 * バニラは options.txt の resourcePacks にある物だけを有効にするので、初めて見た同梱パックだけをそこへ足す。
 * 一度足した ID は options.txt の neo_builtin_packs_seen 行に覚え、使う人が外した後は二度と足さない。
 */
public final class BuiltinPackSelection {
    private BuiltinPackSelection() {}

    /** Options#loadSelectedResourcePacks の直前に、resourcePacks (options.txt の一覧) へ新しい同梱パックを足す。 */
    public static void addNew(List<String> resourcePacks) {
        List<String> ids = BuiltinPacks.clientPackIds();
        if (ids.isEmpty()) return;
        Set<String> seen = new HashSet<>(ModManagerConfig.BUILTIN_PACKS_SEEN.getValue());
        boolean changed = false;
        for (String id : ids) {
            if (seen.add(id)) {
                if (!resourcePacks.contains(id)) resourcePacks.add(id);
                changed = true;
            }
        }
        if (changed) ModManagerConfig.BUILTIN_PACKS_SEEN.setValue(seen);
    }
}
