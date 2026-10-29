package neo.compat;

import net.minecraftforge.fml.common.Mod;

/**
 * Forge の入口 (@Mod)。環境が合う (Java 25 以上 かつ Minecraft 26.3 以上) ときだけ、本体 (neo.forge.NeoForgeMod#start) を呼ぶ。
 * 合わないときは、ログ (mods/NeoMods.log) を書いて何もしない。ゲームは止めない。
 * コンストラクターは引数なし (古い Forge でも、新しい Forge でも受け付けられる形)。
 */
@Mod("neo")
public final class ForgeGate {
    public ForgeGate() {
        String minecraft = minecraftVersion();
        if (CompatCheck.supported(minecraft)) {
            CompatCheck.invokeStatic("neo.forge.NeoForgeMod", "start");
        } else {
            CompatCheck.writeDisabledLog("Forge", minecraft);
        }
    }

    /** Minecraft のバージョン (Forge のリフレクション。版によって場所が違うので、順に試す)。分からなければ null。 */
    private static String minecraftVersion() {
        // 1.13 〜: net.minecraftforge.versions.mcp.MCPVersion#getMCVersion()
        try {
            return String.valueOf(Class.forName("net.minecraftforge.versions.mcp.MCPVersion").getMethod("getMCVersion").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // 次の方法へ
        }

        // 1.17 〜: net.minecraftforge.fml.loading.FMLLoader#versionInfo() の mcVersion()
        try {
            Object info = Class.forName("net.minecraftforge.fml.loading.FMLLoader").getMethod("versionInfo").invoke(null);
            return String.valueOf(info.getClass().getMethod("mcVersion").invoke(info));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
