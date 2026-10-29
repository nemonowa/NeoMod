package neo.menu.service;

import neo.menu.mod.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mod 一覧の並び順 (固定。切り替える設定・ボタンは無い)。上から Java、Minecraft、NeoMod の順に固定し、
 * それ以外は表示名 (翻訳後) の辞書順 (昇順)。
 */
public final class ModOrdering {
    /** 先頭に固定する Mod の ID (この順)。 */
    private static final List<String> PINNED = List.of("java", "minecraft", "neo"); // "neo" = この Mod の ID (fabric.mod.json の id)

    private static final Comparator<Mod> PINNED_FIRST = Comparator.comparingInt(mod -> {
        int index = PINNED.indexOf(mod.getId());
        return index < 0 ? PINNED.size() : index;
    });

    private static final Comparator<Mod> BY_NAME = Comparator.comparing(mod -> ModTexts.name(mod).toLowerCase(Locale.ROOT));
    private static final Comparator<Mod> ORDER = PINNED_FIRST.thenComparing(BY_NAME);

    private ModOrdering() {}

    public static Comparator<Mod> comparator() {
        return ORDER;
    }
}
