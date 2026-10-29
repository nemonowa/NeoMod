package neo.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 版のエントリポイント。ここはクライアント専用クラスを触らない (専用サーバーでも読み込まれるため)。
 * Mod Menu はクライアントのときだけ {@link ForgeClientInit} から起動する。
 * Forge が読む @Mod は neo.compat.ForgeGate にあり、環境が合うときだけ {@link #start()} を呼ぶ (古い Minecraft / Java では、ログだけ書いて何もしない)。
 */
public final class NeoForgeMod {
    /** mods.toml の modId と一致させる。Mod Menu の同梱アイコン等を自分の jar から引くのにも使う。 */
    public static final String MOD_ID = "neo";

    private NeoForgeMod() {}

    /** neo.compat.ForgeGate から (リフレクションで) 呼ばれる。 */
    public static void start() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientInit.run();
        }
    }
}
