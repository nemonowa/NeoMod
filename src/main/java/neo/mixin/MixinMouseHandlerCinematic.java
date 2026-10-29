package neo.mixin;

import neo.cinematic.AfkCinematic;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AFK シネマティック: マウスの移動・ボタン・ホイールを「放置の解除」として伝える。 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerCinematic {
    @Inject(method = "onMove(JDDDD)V", at = @At("HEAD"), require = 1)
    private void neo$cinematicMove(long handle, double xpos, double ypos, double xrel, double yrel, CallbackInfo ci) {
        AfkCinematic.onMouseMove(xrel, yrel);
    }

    @Inject(method = "onButton", at = @At("HEAD"), require = 1)
    private void neo$cinematicButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        AfkCinematic.onActivity();
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), require = 1)
    private void neo$cinematicScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        AfkCinematic.onActivity();
    }
}
