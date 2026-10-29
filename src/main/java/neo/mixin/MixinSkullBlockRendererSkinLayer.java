package neo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import neo.skinlayers.SkinLayerSkull;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D スキンレイヤー (頭ブロック): extract でプレイヤーの頭のスキンを描画状態に入れ、submit の間だけそのスキンを入口に置く
 * (submit の中で作られる頭モデルの State が受け取る)。遠い頭は入れない (2D のまま)。
 */
@Mixin(SkullBlockRenderer.class)
public abstract class MixinSkullBlockRendererSkinLayer {
    @Shadow
    @Final
    private PlayerSkinRenderCache playerSkinRenderCache;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SkullBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("TAIL"),
            require = 1)
    private void neo$extractSkullSkin(SkullBlockEntity entity, SkullBlockRenderState state, float partialTicks, Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay overlay, CallbackInfo ci) {
        Identifier skin = null;
        if (SkinLayers.enabled() && state.skullType == SkullBlock.Types.PLAYER) {
            BlockPos pos = state.blockPos;
            double distanceSq = cameraPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            ResolvableProfile profile = entity.getOwnerProfile();
            if (profile != null && SkinLayers.skullInRange(distanceSq)) {
                skin = this.playerSkinRenderCache.getOrDefault(profile).playerSkin().body().texturePath();
            }
        }
        ((SkinLayerSkull) (Object) state).neoSetSkin(skin);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            require = 1)
    private void neo$beginSubmit(SkullBlockRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        SkinLayers.setPendingSkull(((SkinLayerSkull) (Object) state).neoGetSkin());
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"),
            require = 1)
    private void neo$endSubmit(SkullBlockRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        SkinLayers.setPendingSkull(null);
    }
}
