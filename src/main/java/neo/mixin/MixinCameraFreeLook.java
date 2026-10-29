package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.freelook.FreeLookState;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 自由視点: カメラの向きを、プレイヤーの向きではなく自由視点専用の向きにする。
 * alignWithEntity はトロッコ搭乗中とそれ以外の 2 か所で、向きを Entity から読んでいる (Y 回転・X 回転が各 2 回)。
 */
@Mixin(Camera.class)
public abstract class MixinCameraFreeLook {
    @WrapOperation(
        method = "alignWithEntity(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"),
        require = 2
    )
    private float neo$freeLookYaw(Entity entity, float partialTick, Operation<Float> original) {
        return FreeLookState.cameraYaw(entity, original.call(entity, partialTick));
    }

    @WrapOperation(
        method = "alignWithEntity(F)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"),
        require = 2
    )
    private float neo$freeLookPitch(Entity entity, float partialTick, Operation<Float> original) {
        return FreeLookState.cameraPitch(entity, original.call(entity, partialTick));
    }
}
