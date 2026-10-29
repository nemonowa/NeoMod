package neo.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import neo.zoom.ZoomState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** ズーム: カメラの視野角 (手の描画用の hudFov は対象外) に倍率を掛ける。 */
@Mixin(Camera.class)
public abstract class MixinCameraZoom {
    @ModifyReturnValue(method = "calculateFov(F)F", at = @At("RETURN"), require = 1)
    private float neo$zoomFov(float fov) {
        return ZoomState.applyFov(fov);
    }
}
