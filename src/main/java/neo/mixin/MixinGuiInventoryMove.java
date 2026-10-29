package neo.mixin;

import neo.invmove.InventoryMove;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** インベントリ移動: 画面を開く (KeyMapping.releaseAll) 直後に、移動キーの状態を戻す。 */
@Mixin(Gui.class)
public abstract class MixinGuiInventoryMove {
    @Inject(method = "setScreen", at = @At("RETURN"), require = 1)
    private void neo$inventoryMove(CallbackInfo ci) {
        InventoryMove.onScreenSet();
    }
}
