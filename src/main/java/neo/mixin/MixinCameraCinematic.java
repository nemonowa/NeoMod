package neo.mixin;

import neo.cinematic.AfkCinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AFK シネマティック: alignWithEntity (カメラをプレイヤーに合わせる処理) の末尾で、カメラの位置と向きをシネマティックのものに上書きする。
 * Camera#update はこの後に視野・カリング用の視錐台を作るので、上書きした位置・向きがそのまま描画に使われる。
 * シネマティック中でなければ {@link AfkCinematic#view} が null を返し、何もしない。
 */
@Mixin(Camera.class)
public abstract class MixinCameraCinematic {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"), require = 1)
    private void neo$cinematicCamera(float partialTicks, CallbackInfo ci) {
        AfkCinematic.View view = AfkCinematic.view(partialTicks);
        if (view != null) {
            this.setRotation(view.yaw(), view.pitch());
            this.setPosition(view.position());
        }
    }
}
