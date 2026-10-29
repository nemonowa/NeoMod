package neo.mixin;

import neo.tooltip.EnchantToolTipClient;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EnchantToolTip: Fabric API の ItemTooltipCallback の代替。 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), require = 1)
    private void neo$enchantTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
                                       CallbackInfoReturnable<List<Component>> cir) {
        EnchantToolTipClient.appendLines((ItemStack) (Object) this, cir.getReturnValue());
    }
}
