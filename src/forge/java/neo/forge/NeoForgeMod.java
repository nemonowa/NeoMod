package neo.forge;

import java.nio.file.Path;

import neo.bundle.BuiltinPacks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
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
        // 同梱パック (jar の resourcepacks/)。Fabric 版の MixinPackRepository の代わり。リソースパックの「最初から有効」は Forge 版には無い (選択画面で手で有効にする)
        IModFileInfo own = ModList.getModFileById(MOD_ID);
        Path packs = own != null ? own.getFile().findResource("resourcepacks") : null;
        if (packs != null && packs.getParent() != null) BuiltinPacks.setRoot(packs.getParent());
        AddPackFindersEvent.BUS.addListener(event -> event.addRepositorySource(BuiltinPacks.source(event.getPackType())));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientInit.run();
        }
    }
}
