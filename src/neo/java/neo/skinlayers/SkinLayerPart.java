package neo.skinlayers;

import java.util.List;
import net.minecraft.client.model.geom.ModelPart;

/**
 * 3D スキンレイヤー: {@link ModelPart} に「立体メッシュ」を持たせるための口。
 * 実装は Mixin (neo.mixin.MixinModelPartSkinLayer) が ModelPart に足す。メッシュが無い (null) ときは、バニラの描画のまま。
 */
public interface SkinLayerPart {
    /** このパーツの代わりに描く立体メッシュ。無ければ null (バニラの 2D のまま)。 */
    SkinLayerMesh neoGetSkinLayer();

    void neoSetSkinLayer(SkinLayerMesh mesh);

    /** このパーツが持つバニラの Cube (UV と面の位置をここから読む)。 */
    List<ModelPart.Cube> neoGetCubes();
}
