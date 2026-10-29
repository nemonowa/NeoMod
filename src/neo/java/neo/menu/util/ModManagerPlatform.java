package neo.menu.util;

import java.nio.file.Path;
import java.util.Set;

/**
 * ローダー (Fabric) 固有の機能への窓口。Mod Menu 本体はローダーに依存せず、この抽象を通して利用する。
 * 実装は起動時に {@link #install} で登録する (Fabric 版は neo.fabric.FabricPlatform)。
 */
public abstract class ModManagerPlatform {
    private static ModManagerPlatform current;

    public static void install(ModManagerPlatform platform) {
        current = platform;
    }

    public static ModManagerPlatform get() {
        if (current == null) {
            throw new IllegalStateException("ModManagerPlatform is not installed yet");
        }
        return current;
    }

    /** 設定ファイルの置き場 (config フォルダ)。 */
    public abstract Path configDir();

    /** Mod の jar を置く mods フォルダ。 */
    public abstract Path modsDir();

    public abstract boolean isModLoaded(String modId);

    /** ファイルがこのローダーの Mod として読み込める jar か (ドラッグ&ドロップ導入の判定)。 */
    public abstract boolean isModFile(Path file);

    /**
     * 他 Mod が提供する設定画面・モドパックバッジなどを取り込む (ConfigScreens に登録する)。
     * Fabric: "modmenu" エントリポイント / Forge: 設定画面の拡張ポイント。
     *
     * @param modpackMods モドパック由来として扱う Mod ID の出力先
     */
    public abstract void loadIntegrations(Set<String> modpackMods);

    /** 全 Mod を列挙して ModRegistry を組み立てる (親子関係は ModHierarchy#build)。 */
    public abstract void discoverMods(Set<String> modpackMods);
}
