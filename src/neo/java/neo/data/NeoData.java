package neo.data;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * この Mod が持つデータの保存規約 (共通規約)。
 *
 * <pre>
 * dataFolder/                      バニラのワールドの data フォルダ (シングル: &lt;ワールド&gt;/data)
 *   minecraft/maps/&lt;id&gt;.dat        バニラのデータは、バニラの階層・形式のまま置く (この Mod は作り方も読み方もバニラと同じ)
 *   neo/&lt;path&gt;.dat                 Mod 固有のデータ (索引・ピンなど) は、必ず neo/ の下に置く
 * </pre>
 *
 * 形式もバニラの SavedData と同じ gzip の NBT で、`{ DataVersion: int, data: { ... } }` の形にする。
 * path は小文字・数字・/ . _ - だけ (Identifier の規則)。例: "map/index" → dataFolder/neo/map/index.dat
 * 新しい永続データを足すときは、このクラスを通す (config/ は設定専用で、ワールドごとのデータは置かない)。
 */
public final class NeoData {
    public static final String NAMESPACE = "neo";
    private static final Logger LOGGER = LogUtils.getLogger();

    private NeoData() {}

    /** dataFolder/neo/&lt;path&gt;.dat。 */
    public static Path file(Path dataFolder, String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path).withSuffix(".dat").resolveAgainst(dataFolder);
    }

    /** data の中身を読む。ファイルが無い・壊れているときは null。 */
    public static @Nullable CompoundTag read(Path dataFolder, String path) {
        Path file = file(dataFolder, path);
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            return root.getCompoundOrEmpty("data");
        } catch (IOException e) {
            LOGGER.warn("[NeoData] 読めませんでした: {}", file, e);
            return null;
        }
    }

    /** フォルダを丸ごと移す (名前の付け方を変えたときの引っ越し用)。移せたら true、失敗したときはログに書いて false。 */
    public static boolean moveFolder(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to);
            return true;
        } catch (IOException e) {
            LOGGER.warn("[NeoData] 移せませんでした: {} -> {}", from, to, e);
            return false;
        }
    }

    /** data を書く (一時ファイルに書いてから置き換えるので、途中で落ちても壊れない)。成功したら true。終了時の保存フックと同じ一時ファイルを奪い合わないよう、直列にする。 */
    public static synchronized boolean write(Path dataFolder, String path, CompoundTag data) {
        Path file = file(dataFolder, path);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        NbtUtils.addCurrentDataVersion(root);
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, temp);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        } catch (IOException e) {
            LOGGER.warn("[NeoData] 書けませんでした: {}", file, e);
            return false;
        }
    }
}
