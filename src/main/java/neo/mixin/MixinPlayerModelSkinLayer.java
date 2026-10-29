package neo.mixin;

import neo.skinlayers.SkinLayerModel;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D スキンレイヤー: setupAnim の最後 (描画の直前) で、各レイヤーにそのプレイヤーのスキンのメッシュを持たせる。 */
@Mixin(PlayerModel.class)
public abstract class MixinPlayerModelSkinLayer implements SkinLayerModel {
    @Shadow
    @Final
    private boolean slim;

    @Override
    public boolean neoIsSlim() {
        return slim;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 1)
    private void neo$applySkinLayers(AvatarRenderState state, CallbackInfo ci) {
        SkinLayers.apply((PlayerModel) (Object) this, state);
    }
}
