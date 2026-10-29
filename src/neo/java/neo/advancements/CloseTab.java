package neo.advancements;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementTabType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 進捗画面の右下に付ける「閉じる」タブ。バニラのタブ (AdvancementTabType.BELOW) の見た目で、
 * アイコンはエンチャント付き (光る) のバリアブロック。押すと進捗画面を閉じる。
 * 「進捗画面の拡大」(large_advancements) が ON のときだけ出る (OFF のときはバニラと同じ)。
 * 描画・判定・ツールチップは Mixin (MixinAdvancementsScreen) からここを呼ぶだけ。
 */
public final class CloseTab {
    /** 下側タブの右端のスプライト (バニラは index == max - 1 のとき「右」の絵になる)。 */
    private static final AdvancementTabType TYPE = AdvancementTabType.BELOW;
    /** 下側タブのアイコンの位置ずれ (AdvancementTabType#extractIcon の BELOW: +6, +6)。 */
    private static final int ICON_OFFSET = 6;

    private static ItemStack icon;

    private CloseTab() {}

    private static ItemStack icon() {
        if (icon == null) {
            ItemStack stack = new ItemStack(Items.BARRIER);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            icon = stack;
        }
        return icon;
    }

    private static int x(int leftPos) {
        return leftPos + AdvancementsLayout.windowWidth() - TYPE.getWidth();
    }

    private static int y(int topPos) {
        // バニラの下側タブの Y (窓の高さ − 4)
        return topPos + AdvancementsLayout.windowHeight() - 4;
    }

    public static boolean isMouseOver(int leftPos, int topPos, double mouseX, double mouseY) {
        int x = x(leftPos);
        int y = y(topPos);
        return mouseX > x && mouseX < x + TYPE.getWidth() && mouseY > y && mouseY < y + TYPE.getHeight();
    }

    /** タブとアイコンを描く (窓の描画の最後。バニラのタブと同じ層)。 */
    public static void extract(GuiGraphicsExtractor g, int leftPos, int topPos, int mouseX, int mouseY) {
        if (!AdvancementsLayout.enabled()) {
            return;
        }
        int x = x(leftPos);
        int y = y(topPos);
        TYPE.extractRenderState(g, x, y, false, TYPE.getMax() - 1);
        g.fakeItem(icon(), x + ICON_OFFSET, y + ICON_OFFSET);
        if (isMouseOver(leftPos, topPos, mouseX, mouseY)) {
            g.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    public static void extractTooltip(GuiGraphicsExtractor g, int leftPos, int topPos, int mouseX, int mouseY) {
        if (AdvancementsLayout.enabled() && isMouseOver(leftPos, topPos, mouseX, mouseY)) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, Component.translatable("neo.advancements.close"), mouseX, mouseY);
        }
    }

    /** クリックされたら true (呼び出し側が画面を閉じる)。クリック音も鳴らす。 */
    public static boolean click(int leftPos, int topPos, double mouseX, double mouseY) {
        if (!AdvancementsLayout.enabled() || !isMouseOver(leftPos, topPos, mouseX, mouseY)) {
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }
}
