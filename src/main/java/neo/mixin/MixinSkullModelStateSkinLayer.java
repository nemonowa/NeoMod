package neo.mixin;

import neo.skinlayers.SkinLayerSkull;
import neo.skinlayers.SkinLayers;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3D スキンレイヤー (頭): 頭モデルの State (描画の直前に setupAnim へ渡る) に、スキンを持たせる。
 * State は SkullBlockRenderer#submitSkull の中で作られる (その時点で入口が置いたスキンを受け取る)。
 */
@Mixin(SkullModelBase.State.class)
public abstract class MixinSkullModelStateSkinLayer implements SkinLayerSkull {
    @Unique
    private Identifier neoSkin;

    @Override
    public Identifier neoGetSkin() {
        return neoSkin;
    }

    @Override
    public void neoSetSkin(Identifier skin) {
        this.neoSkin = skin;
    }

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 1)
    private void neo$takeSkin(CallbackInfo ci) {
        this.neoSkin = SkinLayers.takePendingSkull();
    }
}
