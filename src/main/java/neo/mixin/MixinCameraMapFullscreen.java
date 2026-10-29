package neo.mixin;

import neo.map.HeldMapView;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * ワールドマップの全画面: 手の描画の視野角 (Camera#calculateHudFov の定数 70°) を、全画面のときだけ狭めて、地図が画面全体を覆うまで拡大する (画面の縦横比で決まる)。
 * 死亡・水中の補正 (modifyFovBasedOnDeathOrFluid) はそのまま通る。
 */
@Mixin(Camera.class)
public abstract class MixinCameraMapFullscreen {
    @ModifyConstant(method = "calculateHudFov(F)F", constant = @Constant(floatValue = 70.0F), require = 1)
    private float neo$mapFullscreenFov(float vanilla) {
        return HeldMapView.hudFov(vanilla);
    }
}
