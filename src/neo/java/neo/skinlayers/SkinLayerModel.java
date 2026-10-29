package neo.skinlayers;

/** 3D スキンレイヤー: PlayerModel がスリム (腕 3px) かどうかを取り出す口。実装は Mixin (MixinPlayerModelSkinLayer)。 */
public interface SkinLayerModel {
    boolean neoIsSlim();
}
