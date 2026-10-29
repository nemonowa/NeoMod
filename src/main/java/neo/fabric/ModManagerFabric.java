package neo.fabric;

import neo.menu.ModManagerBootstrap;

/** Fabric 側から Mod Menu を初期化する入口。手順そのものは loader 非依存の {@link ModManagerBootstrap}。 */
public final class ModManagerFabric {
    private ModManagerFabric() {}

    /** neo のクライアント初期化 (NeoCore) から呼ばれる。独立した Fabric エントリポイントではない。 */
    public static void init() {
        ModManagerBootstrap.init(new FabricPlatform());
    }
}
