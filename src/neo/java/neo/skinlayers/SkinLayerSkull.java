package neo.skinlayers;

import net.minecraft.resources.Identifier;

/**
 * 3D スキンレイヤー (頭): 頭の描画状態 (ブロックの描画状態・頭モデルの State) に、「どのプレイヤーのスキンか」を持たせる口。
 * 頭の描画 (SkullBlockRenderer#submitSkull) は RenderType だけを受け取り、スキンの情報は渡らない。そのため入口で拾ったスキンを、
 * State の生成時に受け渡して ({@link SkinLayers#takePendingSkull}) 、描画の直前の setupAnim で hat にメッシュを持たせる。
 * 実装は Mixin (MixinSkullBlockRenderStateSkinLayer / MixinSkullModelStateSkinLayer)。
 */
public interface SkinLayerSkull {
    /** プレイヤーの頭ならそのスキンのテクスチャ。それ以外 (他の頭・スキン不明・遠い・OFF) は null。 */
    Identifier neoGetSkin();

    void neoSetSkin(Identifier skin);
}
