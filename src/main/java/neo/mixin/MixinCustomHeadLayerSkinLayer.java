package neo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D スキンレイヤー (着用中の頭): プレイヤーの頭を頭に被っているエンティティ (プレイヤー・ゾンビなど) 用。
 * submit の間だけ、被っている頭のスキンを入口に置く。遠いエンティティは 2D のまま。
 */
@Mixin(CustomHeadLayer.class)
public abstract class MixinCustomHeadLayerSkinLayer {
    private static final String SUBMIT =
            "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V";

    @Shadow
    @Final
    private PlayerSkinRenderCache playerSkinRenderCache;

    @Inject(method = SUBMIT, at = @At("HEAD"), require = 1)
    private void neo$beginSubmit(PoseStack poseStack, SubmitNodeCollector collector, int light, LivingEntityRenderState state,
            float yRot, float xRot, CallbackInfo ci) {
        Identifier skin = null;
        ResolvableProfile profile = state.wornHeadProfile;
        if (profile != null && state.wornHeadType == SkullBlock.Types.PLAYER && SkinLayers.skullInRange(state.distanceToCameraSq)) {
            skin = this.playerSkinRenderCache.getOrDefault(profile).playerSkin().body().texturePath();
        }
        SkinLayers.setPendingSkull(skin);
    }

    @Inject(method = SUBMIT, at = @At("RETURN"), require = 1)
    private void neo$endSubmit(PoseStack poseStack, SubmitNodeCollector collector, int light, LivingEntityRenderState state,
            float yRot, float xRot, CallbackInfo ci) {
        SkinLayers.setPendingSkull(null);
    }
}
