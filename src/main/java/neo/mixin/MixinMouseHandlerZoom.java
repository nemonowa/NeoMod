package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.zoom.ZoomState;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ズーム: 視点移動の感度を下げる / ズーム中のホイールを倍率の変更に使う (ホットバーは切り替えない)。 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerZoom {
    /** turnPlayer の最初の OptionInstance#get() が感度の設定値 (続く 2 つは X/Y 反転)。 */
    @WrapOperation(
        method = "turnPlayer(D)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
        require = 1
    )
    private Object neo$zoomSensitivity(OptionInstance<?> sensitivity, Operation<Object> original) {
        Object value = original.call(sensitivity);
        return value instanceof Double d ? (Object) ZoomState.applySensitivity(d) : value;
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void neo$zoomScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        if (ZoomState.onScroll(yOffset)) {
            ci.cancel();
        }
    }
}
