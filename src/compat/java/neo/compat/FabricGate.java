package neo.compat;

import java.lang.reflect.Method;

/**
 * Fabric の入口。fabric.mod.json の entrypoints (preLaunch / main / client) は、すべてここを指す (メソッド参照 "neo.compat.FabricGate::...")。
 * 環境が合う (Java 25 以上 かつ Minecraft 26.3 以上) ときだけ、Mixin の設定を登録し、本体 (neo.fabric.NeoMain / NeoClient) を呼ぶ。
 * 合わないときは、ログ (mods/NeoMods.log) を書いて何もしない。ゲームは止めない。
 *
 * Mixin の設定 (mixins.neo.json) を fabric.mod.json に書かず、ここで登録するのは、古い環境で Java 25 のバイトコードの Mixin クラスを
 * 読んでしまわないため (古い Mixin / ASM は、新しいバイトコードを読むと止まる)。preLaunch は、Minecraft のクラスを読む前に呼ばれる。
 */
public final class FabricGate {
    private static final String MIXIN_CONFIG = "mixins.neo.json";
    private static Boolean supported;

    private FabricGate() {}

    /** Minecraft より前 (preLaunch)。環境が合うときだけ Mixin の設定を登録する。 */
    public static void preLaunch() {
        if (!isSupported()) {
            return;
        }

        try {
            Class.forName("org.spongepowered.asm.mixin.Mixins").getMethod("addConfiguration", String.class).invoke(null, MIXIN_CONFIG);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NeoMods: could not register " + MIXIN_CONFIG, e);
        }
    }

    /** ModInitializer#onInitialize の代わり。 */
    public static void main() {
        if (isSupported()) {
            CompatCheck.invokeStatic("neo.fabric.NeoMain", "start");
        }
    }

    /** ClientModInitializer#onInitializeClient の代わり。 */
    public static void client() {
        if (isSupported()) {
            CompatCheck.invokeStatic("neo.fabric.NeoClient", "start");
        }
    }

    /** 環境の確認 (結果は覚えておき、無効のときはログを 1 回だけ書く)。 */
    private static synchronized boolean isSupported() {
        if (supported == null) {
            String minecraft = minecraftVersion();
            supported = Boolean.valueOf(CompatCheck.supported(minecraft));
            if (!supported.booleanValue()) {
                CompatCheck.writeDisabledLog("Fabric" + loaderSuffix(), minecraft);
            }
        }

        return supported.booleanValue();
    }

    /** Minecraft のバージョン (FabricLoader のリフレクション)。分からなければ null。 */
    private static String minecraftVersion() {
        try {
            Object loader = Class.forName("net.fabricmc.loader.api.FabricLoader").getMethod("getInstance").invoke(null);
            Object container = ((java.util.Optional<?>) loader.getClass().getMethod("getModContainer", String.class).invoke(loader, "minecraft")).orElse(null);
            if (container == null) {
                return null;
            }

            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
            Method friendly = version.getClass().getMethod("getFriendlyString");
            return String.valueOf(friendly.invoke(version));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static String loaderSuffix() {
        try {
            Object loader = Class.forName("net.fabricmc.loader.api.FabricLoader").getMethod("getInstance").invoke(null);
            Object container = ((java.util.Optional<?>) loader.getClass().getMethod("getModContainer", String.class).invoke(loader, "fabricloader")).orElse(null);
            if (container == null) {
                return "";
            }

            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
            return " " + version.getClass().getMethod("getFriendlyString").invoke(version);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "";
        }
    }
}
