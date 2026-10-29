package neo.mixin;

import neo.cinematic.AfkCinematic;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFK シネマティック: キーの操作を「放置の解除」として伝える (押す・離すのどちらも)。シネマティック中の F1 (GUI の切り替え) だけは解除にならない (F1 の処理は MixinGuiCinematic)。 */
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandlerCinematic {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 1)
    private void neo$cinematicKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
        AfkCinematic.onKeyPress(event);
    }
}
