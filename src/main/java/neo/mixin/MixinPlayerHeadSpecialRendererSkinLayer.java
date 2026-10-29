package neo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D スキンレイヤー (頭アイテム): インベントリ (GUI)・手・地面・額縁の頭アイテム。submit の間だけそのスキンを入口に置く。
 * アイテムはカメラからの距離を持たないので、距離では切らない。スキンが無い (読み込み前) ときは 2D のまま。
 */
@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class MixinPlayerHeadSpecialRendererSkinLayer {
    private static final String SUBMIT =
            "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V";

    @Inject(method = SUBMIT, at = @At("HEAD"), require = 1)
    private void neo$beginSubmit(PlayerSkinRenderCache.RenderInfo info, PoseStack poseStack, SubmitNodeCollector collector,
            int light, int overlay, boolean glint, int outline, CallbackInfo ci) {
        boolean usable = info != null && SkinLayers.skullInRange(-1);
        SkinLayers.setPendingSkull(usable ? info.playerSkin().body().texturePath() : null);
    }

    @Inject(method = SUBMIT, at = @At("RETURN"), require = 1)
    private void neo$endSubmit(PlayerSkinRenderCache.RenderInfo info, PoseStack poseStack, SubmitNodeCollector collector,
            int light, int overlay, boolean glint, int outline, CallbackInfo ci) {
        SkinLayers.setPendingSkull(null);
    }
}
