package neo.mixin;

import neo.advancements.AdvancementsLayout;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** ホバー時の説明ボックスを上側に出すかの判定 (バニラは表示域の高さ 113 と比較) を、拡大した表示域に合わせる。 */
@Mixin(AdvancementWidget.class)
public abstract class MixinAdvancementWidget {
    @ModifyConstant(method = "extractHover", constant = @Constant(intValue = 113), require = 1)
    private int neo$contentHeight(int original) {
        return AdvancementsLayout.contentHeight(original);
    }
}
