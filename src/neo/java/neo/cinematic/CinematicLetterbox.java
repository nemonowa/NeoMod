package neo.cinematic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * シネマティック中に画面の上下へ出す黒帯 (縦横比 2.1:1。16:9 の画面で上下それぞれ高さの約 7.7%)。出入りはなめらかに動かす。
 * 画面がすでに 2.1:1 より横長なら、帯は出ない。
 * 描画は Hud#extractRenderState の先頭 (HUD の下。neo.mixin.MixinHudCinematic) から毎フレーム呼ばれる。
 * 出ていない間 (進行度 0 で表示も要求されていない) は、入口で即 return する。
 */
public final class CinematicLetterbox {
    /** 目標の縦横比 (幅 / 高さ)。 */
    private static final float ASPECT = 2.1F;
    /** 完全に出る / 消えるまでの秒数。 */
    private static final float FADE_SECONDS = 0.6F;
    private static final int BLACK = 0xFF000000;

    private static boolean shown;
    private static float progress;
    private static long lastNanos;

    private CinematicLetterbox() {}

    /** 帯を出す / しまう。 */
    static void setShown(boolean value) {
        shown = value;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!shown && progress <= 0.0F) {
            lastNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1.0E9F;
        lastNanos = now;
        progress = Math.max(0.0F, Math.min(1.0F, progress + (shown ? seconds : -seconds) / FADE_SECONDS));

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int fullBar = Math.max(0, (height - Math.round(width / ASPECT)) / 2);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        int bar = Math.round(fullBar * eased);
        if (bar <= 0) {
            return;
        }

        graphics.fill(0, 0, width, bar, BLACK);
        graphics.fill(0, height - bar, width, height, BLACK);
    }
}
