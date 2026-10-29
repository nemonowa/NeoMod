package neo.fabric;

import net.fabricmc.api.ModInitializer;

/**
 * neo の共通 (main) 入口。fabric.mod.json の main は neo.compat.FabricGate を指し、環境が合うときだけ {@link #start()} を呼ぶ
 * (古い Minecraft / Java では、ログだけ書いて何もしない。neo.compat を参照)。
 */
public class NeoMain implements ModInitializer {
    /** neo.compat.FabricGate から (リフレクションで) 呼ばれる。 */
    public static void start() {
        new NeoMain().onInitialize();
    }

    @Override
    public void onInitialize() {
        NeoCore.init("Fabric");
    }
}
