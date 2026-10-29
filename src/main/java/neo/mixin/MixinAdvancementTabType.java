package neo.mixin;

import neo.advancements.AdvancementsLayout;
import net.minecraft.client.gui.screens.advancements.AdvancementTabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** 右側・下側に並ぶタブの位置 (バニラは 窓の幅 252 / 高さ 140 から 4px 内側に固定) を、拡大した窓の端に合わせる。 */
@Mixin(AdvancementTabType.class)
public abstract class MixinAdvancementTabType {
    @ModifyConstant(method = "getX", constant = @Constant(intValue = 248), require = 1)
    private int neo$rightTabX(int original) {
        return AdvancementsLayout.windowWidthMinus4(original);
    }

    @ModifyConstant(method = "getY", constant = @Constant(intValue = 136), require = 1)
    private int neo$belowTabY(int original) {
        return AdvancementsLayout.windowHeightMinus4(original);
    }
}
