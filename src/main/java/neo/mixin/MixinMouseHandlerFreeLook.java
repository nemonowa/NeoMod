package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.freelook.FreeLookState;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 自由視点: turnPlayer の LocalPlayer#turn を、自由視点中はカメラだけを回す処理に置き換える。 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandlerFreeLook {
    @WrapOperation(
        method = "turnPlayer(D)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
        require = 1
    )
    private void neo$freeLookTurn(LocalPlayer player, double xo, double yo, Operation<Void> original) {
        if (!FreeLookState.turn(xo, yo)) {
            original.call(player, xo, yo);
        }
    }
}
