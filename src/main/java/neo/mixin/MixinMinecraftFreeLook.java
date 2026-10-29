package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.freelook.FreeLookState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 自由視点: handleKeybinds の最初の consumeClick (= 視点切り替えキー F5) を肩代わりする。
 * 設定 free_look が OFF のときは、バニラの判定をそのまま返す。ON のときは単押しだけバニラの切り替えを通し、長押しは自由視点にする。
 * あわせて、バニラの 3 段切り替えの書き込み (Options#setCameraType) も、自由視点中は独立に止める。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFreeLook {
    @WrapOperation(
        method = "handleKeybinds()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z", ordinal = 0),
        require = 1
    )
    private boolean neo$perspectiveKey(KeyMapping key, Operation<Boolean> original) {
        return FreeLookState.consumePerspectiveKey(() -> original.call(key));
    }

    /** バニラの視点切り替えの書き込み (handleKeybinds の最初の setCameraType = CameraType#cycle の結果)。自由視点中は何もしない。 */
    @WrapOperation(
        method = "handleKeybinds()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V", ordinal = 0),
        require = 1
    )
    private void neo$blockVanillaSwitch(Options options, CameraType type, Operation<Void> original) {
        if (!FreeLookState.blocksVanillaSwitch()) {
            original.call(options, type);
        }
    }
}
