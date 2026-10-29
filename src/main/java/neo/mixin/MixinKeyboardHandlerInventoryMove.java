package neo.mixin;

import neo.invmove.InventoryMove;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** インベントリ移動: 画面を開いている間も、移動キーを押したら「押した」状態にする (画面側のキー処理は止めない)。 */
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandlerInventoryMove {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 1)
    private void neo$inventoryMove(long handle, int action, KeyEvent event, CallbackInfo ci) {
        InventoryMove.onKeyPress(action, event);
    }
}
