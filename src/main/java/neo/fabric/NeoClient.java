package neo.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * neo のクライアント側入口。Mod Menu / Spawner Info はここから初期化する (個別のエントリポイントは持たない)。
 * fabric.mod.json の client は neo.compat.FabricGate を指し、環境が合うときだけ {@link #start()} を呼ぶ。
 */
public class NeoClient implements ClientModInitializer {
    /** neo.compat.FabricGate から (リフレクションで) 呼ばれる。 */
    public static void start() {
        new NeoClient().onInitializeClient();
    }

    @Override
    public void onInitializeClient() {
        NeoCore.initClient();
    }
}
