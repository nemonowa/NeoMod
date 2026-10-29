package neo.menu.mod;

import java.nio.file.Path;

/** アイコン画像を読み出す元 (Mod の jar など)。 */
public interface IconSource {
    /** 元の中の相対パスを実ファイルのパスに解決する。 */
    Path getPath(String relativePath);

    /** ログ表示用の Mod ID。 */
    String getId();
}
