package neo.menu.service;

import neo.menu.config.ModManagerConfig;
import neo.menu.mod.Mod;
import neo.menu.mod.ModRegistry;

import java.text.NumberFormat;

/** 「Mod 数」表示用のカウント (設定が変わるまでキャッシュする)。 */
public final class ModCount {
    private static int cached = -1;

    private ModCount() {}

    public static void clearCache() {
        cached = -1;
    }

    public static String getDisplayed() {
        if (cached == -1) {
            // Mod 一覧の「N個のModを表示中」と同じ数 (親の Mod のうち、非表示でも前提 Mod (ライブラリ) でもないもの)。
            // 子 Mod・ライブラリ・非表示の Mod は数えない (以前は ModRegistry.MODS の全件で、一覧の数とずれていた)。
            cached = (int) ModRegistry.ROOT_MODS.values().stream()
                    .filter(mod -> !ModManagerConfig.isHidden(mod) && !mod.getBadges().contains(Mod.Badge.LIBRARY))
                    .count();
        }

        return NumberFormat.getInstance().format(cached);
    }
}
