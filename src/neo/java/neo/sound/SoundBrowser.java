package neo.sound;

import java.util.Locale;
import neo.event.NeoClientEvents;
import neo.menu.config.ModManagerConfig;
import neo.util.ScreenWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * サウンドブラウザ (クリエイティブインベントリの右側面のタブ)。
 * 設定 sound_browser が OFF のときは、クリエイティブ画面を開いたときに設定を 1 回引いて何もしない (タブを作らない)。
 */
public final class SoundBrowser {
    /** クリエイティブ画面の大きさ (26.3 の CreativeModeInventoryScreen のコンストラクタ: 195 x 136)。 */
    static final int CREATIVE_WIDTH = 195;
    static final int CREATIVE_HEIGHT = 136;
    /** バニラのレシピ本の横向きタブ (recipe_book/tab*.png、35 x 27) を左右反転して、右側面に使う。 */
    static final int TAB_WIDTH = 35;
    static final int TAB_HEIGHT = 27;
    /** タブが本体の縁に重なる幅 (レシピ本と同じく、開いた側を本体の下に潜らせる)。 */
    static final int TAB_OVERLAP = 4;
    private static final Identifier TAB_SPRITE = Identifier.withDefaultNamespace("recipe_book/tab");
    private static final Identifier TAB_SELECTED_SPRITE = Identifier.withDefaultNamespace("recipe_book/tab_selected");

    private SoundBrowser() {}

    public static void init() {
        NeoClientEvents.registerAfterScreenInit(SoundBrowser::afterScreenInit);
    }

    private static void afterScreenInit(Minecraft client, Screen screen, int width, int height) {
        if (!(screen instanceof CreativeModeInventoryScreen) || !ModManagerConfig.SOUND_BROWSER.getValue()) {
            return;
        }
        // leftPos / topPos は AbstractContainerScreen#init と同じ式 ((画面 - 大きさ) / 2)
        int left = (width - CREATIVE_WIDTH) / 2;
        int top = (height - CREATIVE_HEIGHT) / 2;
        ScreenWidgets.getWidgets(screen).add(new SideTab(left + CREATIVE_WIDTH - TAB_OVERLAP, top + 4, false,
                () -> client.gui.setScreen(new SoundBrowserScreen(screen))));
    }

    /** /playsound の本文 (先頭の / なし)。数値は小数 2 桁までで、末尾の 0 は省く。 */
    static String command(Identifier id, SoundSource source, float volume, float pitch) {
        return "playsound " + id + " " + source.getName() + " @s ~ ~ ~ " + number(volume) + " " + number(pitch);
    }

    static String number(float value) {
        String s = String.format(Locale.ROOT, "%.2f", value);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** 右側面に縦に出るタブ (音符ブロックのアイコン)。選択中は本体と同じ色でつなげて描く。 */
    static final class SideTab extends AbstractButton {
        private static final ItemStack ICON = new ItemStack(Items.NOTE_BLOCK);
        private final boolean selected;
        private final Runnable action;

        SideTab(int x, int y, boolean selected, Runnable action) {
            super(x, y, TAB_WIDTH, TAB_HEIGHT, Component.translatable("neo.sound.tab"));
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            // 左右反転して描く (レシピ本のタブは左向き)。選択中は本体側へ少し伸びて見える (バニラと同じスプライト)
            g.pose().pushMatrix();
            g.pose().translate(x + w + (selected ? 2 : 0), y);
            g.pose().scale(-1.0F, 1.0F);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? TAB_SELECTED_SPRITE : TAB_SPRITE, 0, 0, w, h);
            g.pose().popMatrix();
            // アイコンの位置もレシピ本のタブに合わせる (選択中は 2px 外へ)
            g.item(ICON, x + w - 16 - 9 + (selected ? 2 : 0), y + 5);
            if (isHovered()) {
                g.setTooltipForNextFrame(Minecraft.getInstance().font, getMessage(), mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
