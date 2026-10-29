package neo.forge;

import neo.menu.mod.IconSource;
import net.minecraftforge.forgespi.locating.IModFile;

import java.nio.file.Path;

/** Forge の Mod ファイル (jar) をアイコンの読み出し元として扱うアダプタ。 */
final class ForgeIconSource implements IconSource {
    private final IModFile file;
    private final String modId;

    ForgeIconSource(IModFile file, String modId) {
        this.file = file;
        this.modId = modId;
    }

    @Override
    public Path getPath(String relativePath) {
        return file.findResource(relativePath);
    }

    @Override
    public String getId() {
        return modId;
    }
}
