package neo.mixin;

import neo.advancements.AdvancementsLayout;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 進捗タブの表示域 (バニラ 234x113) を、拡大した窓に合わせる。MixinAdvancementsScreen と対。 */
@Mixin(AdvancementTab.class)
public abstract class MixinAdvancementTab {
    @ModifyConstant(method = {"tick", "extractContents", "extractTooltips", "scroll", "canScrollHorizontally"}, constant = @Constant(intValue = 234), require = 5)
    private int neo$contentWidth(int original) {
        return AdvancementsLayout.contentWidth(original);
    }

    @ModifyConstant(method = {"tick", "extractContents", "extractTooltips", "scroll", "canScrollVertically"}, constant = @Constant(intValue = 113), require = 5)
    private int neo$contentHeight(int original) {
        return AdvancementsLayout.contentHeight(original);
    }

    // 初回の中央寄せ
    @ModifyConstant(method = "extractContents", constant = @Constant(intValue = 117), require = 1)
    private int neo$halfContentWidth(int original) {
        return AdvancementsLayout.halfContentWidth(original);
    }

    @ModifyConstant(method = "extractContents", constant = @Constant(intValue = 56), require = 1)
    private int neo$halfContentHeight(int original) {
        return AdvancementsLayout.halfContentHeight(original);
    }

    // 背景タイルを敷く範囲 (列・行の上限)
    @ModifyConstant(method = "extractContents", constant = @Constant(intValue = 15), require = 1)
    private int neo$tileColumns(int original) {
        return AdvancementsLayout.tileColumns(original);
    }

    @ModifyConstant(method = "extractContents", constant = @Constant(intValue = 8), require = 1)
    private int neo$tileRows(int original) {
        return AdvancementsLayout.tileRows(original);
    }
}
