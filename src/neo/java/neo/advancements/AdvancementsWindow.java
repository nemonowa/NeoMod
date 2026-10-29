package neo.advancements;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 進捗画面の窓枠 (window.png) を任意サイズで描く。
 * 4 隅は等倍、4 辺は引き伸ばす。中央は透明なので描かない (背景は先に別途描かれる)。
 * window.png は 256x256 で、枠の部分は左上 252x140。
 */
public final class AdvancementsWindow {
    private static final int TEXTURE_SIZE = 256;

    private AdvancementsWindow() {}

    public static void draw(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, int width, int height) {
        int bx = AdvancementsLayout.BORDER_X;
        int top = AdvancementsLayout.BORDER_TOP;
        int bottom = AdvancementsLayout.BORDER_BOTTOM;
        int srcW = AdvancementsLayout.VANILLA_WINDOW_WIDTH;
        int srcH = AdvancementsLayout.VANILLA_WINDOW_HEIGHT;
        int innerW = width - 2 * bx;
        int innerH = height - top - bottom;
        int srcInnerW = srcW - 2 * bx;
        int srcInnerH = srcH - top - bottom;

        // 4 隅 (等倍)
        part(g, pipeline, texture, x, y, 0, 0, bx, top, bx, top);
        part(g, pipeline, texture, x + width - bx, y, srcW - bx, 0, bx, top, bx, top);
        part(g, pipeline, texture, x, y + height - bottom, 0, srcH - bottom, bx, bottom, bx, bottom);
        part(g, pipeline, texture, x + width - bx, y + height - bottom, srcW - bx, srcH - bottom, bx, bottom, bx, bottom);

        // 4 辺 (引き伸ばし)
        part(g, pipeline, texture, x + bx, y, bx, 0, innerW, top, srcInnerW, top);
        part(g, pipeline, texture, x + bx, y + height - bottom, bx, srcH - bottom, innerW, bottom, srcInnerW, bottom);
        part(g, pipeline, texture, x, y + top, 0, top, bx, innerH, bx, srcInnerH);
        part(g, pipeline, texture, x + width - bx, y + top, srcW - bx, top, bx, innerH, bx, srcInnerH);
    }

    /** テクスチャの (u, v) から src サイズの領域を、画面の (x, y) に dst サイズで描く。 */
    private static void part(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                             int x, int y, int u, int v, int dstW, int dstH, int srcW, int srcH) {
        g.blit(pipeline, texture, x, y, (float) u, (float) v, dstW, dstH, srcW, srcH, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
