package neo.mixin;

import neo.skinlayers.SkinLayerSkull;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 3D スキンレイヤー (頭ブロック): 描画状態に、プレイヤーの頭のスキンを持たせる (extract で入れて、submit で使う)。 */
@Mixin(SkullBlockRenderState.class)
public abstract class MixinSkullBlockRenderStateSkinLayer implements SkinLayerSkull {
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
}
