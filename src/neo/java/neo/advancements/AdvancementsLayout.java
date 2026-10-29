package neo.advancements;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;

/**
 * 進捗画面のサイズ計算。バニラは枠 252x140・表示域 234x113 の固定だが、有効時は画面いっぱい (タブの分の余白を残す) まで広げる。
 * 有効時は上部の見出し「進捗」と下部の「完了」ボタンも出さない (MixinAdvancementsScreen)。Esc / 進捗のキーで閉じる。
 * 画面サイズは GUI スケール後のウィンドウサイズ (= Screen#width / height) から毎回求める (状態を持たない)。
 * 無効時はすべて元の値をそのまま返すので、バニラと同じ動作になる。
 */
public final class AdvancementsLayout {
    /** バニラの枠サイズ。これより小さくはしない (小さい画面ではバニラと同じになる)。 */
    public static final int VANILLA_WINDOW_WIDTH = 252;
    public static final int VANILLA_WINDOW_HEIGHT = 140;

    /** 枠の内側余白 (window.png): 左右 9、上 18、下 9。 */
    public static final int BORDER_X = 9;
    public static final int BORDER_TOP = 18;
    public static final int BORDER_BOTTOM = 9;

    /** 上下左右に残す余白。窓の外にはみ出すタブ (バニラの AdvancementTabType は枠の外へ 28px) を隠さない。 */
    private static final int MARGIN = 30;
    /** 画面の幅・高さが 0 以下 (起動直後など) のときに使う仮の値。 */
    private static final int FALLBACK = 1;

    private AdvancementsLayout() {}

    public static boolean enabled() {
        return ModManagerConfig.LARGE_ADVANCEMENTS.getValue();
    }

    public static int windowWidth() {
        return windowSize(guiWidth(), VANILLA_WINDOW_WIDTH);
    }

    public static int windowHeight() {
        return windowSize(guiHeight(), VANILLA_WINDOW_HEIGHT);
    }

    public static int contentWidth() {
        return windowWidth() - 2 * BORDER_X;
    }

    public static int contentHeight() {
        return windowHeight() - BORDER_TOP - BORDER_BOTTOM;
    }

    // ---- Mixin 用: 無効時は original をそのまま返す ----

    public static int windowWidth(int original) {
        return enabled() ? windowWidth() : original;
    }

    public static int windowHeight(int original) {
        return enabled() ? windowHeight() : original;
    }

    public static int contentWidth(int original) {
        return enabled() ? contentWidth() : original;
    }

    public static int contentHeight(int original) {
        return enabled() ? contentHeight() : original;
    }

    /** 右端・下端のタブの位置 (枠の端から 4px 内側)。 */
    public static int windowWidthMinus4(int original) {
        return enabled() ? windowWidth() - 4 : original;
    }

    public static int windowHeightMinus4(int original) {
        return enabled() ? windowHeight() - 4 : original;
    }

    public static int halfContentWidth(int original) {
        return enabled() ? contentWidth() / 2 : original;
    }

    public static int halfContentHeight(int original) {
        return enabled() ? contentHeight() / 2 : original;
    }

    /** 背景タイルを敷く列数の上限 (バニラ: 表示域 234 に対して 15)。 */
    public static int tileColumns(int original) {
        return enabled() ? contentWidth() / 16 + 1 : original;
    }

    /** 背景タイルを敷く行数の上限 (バニラ: 表示域 113 に対して 8)。 */
    public static int tileRows(int original) {
        return enabled() ? contentHeight() / 16 + 1 : original;
    }

    private static int windowSize(int screenSize, int vanilla) {
        return Math.max(vanilla, screenSize - 2 * MARGIN);
    }

    private static int guiWidth() {
        return Math.max(FALLBACK, Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }

    private static int guiHeight() {
        return Math.max(FALLBACK, Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }
}
