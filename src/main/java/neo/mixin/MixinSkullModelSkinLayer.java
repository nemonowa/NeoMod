package neo.mixin;

import neo.skinlayers.SkinLayerSkull;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D スキンレイヤー (頭): setupAnim の最後 (描画の直前) で、head の子 "hat" にそのスキンのメッシュを持たせる。
 * hat を持つのはプレイヤーの頭のモデルだけ (ゾンビなどの頭には無いので、何もしない)。
 */
@Mixin(SkullModel.class)
public abstract class MixinSkullModelSkinLayer {
    @Shadow
    @Final
    protected ModelPart head;

    @Inject(method = "setupAnim(Lnet/minecraft/client/model/object/skull/SkullModelBase$State;)V", at = @At("TAIL"), require = 1)
    private void neo$applySkullLayer(SkullModelBase.State state, CallbackInfo ci) {
        if (!this.head.hasChild("hat")) {
            return;
        }
        SkinLayers.applySkull(this.head.getChild("hat"), ((SkinLayerSkull) (Object) state).neoGetSkin());
    }
}
