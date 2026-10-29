package neo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D スキンレイヤー: 一人称の手 (setupAnim を通らない) の袖にメッシュを持たせる。 */
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRendererSkinLayer {
    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At("HEAD"),
            require = 1)
    private void neo$applyHandSkinLayers(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin,
            ModelPart arm, boolean sleeveVisible, CallbackInfo ci) {
        SkinLayers.applyHand(((AvatarRenderer<?>) (Object) this).getModel(), skin);
    }
}
