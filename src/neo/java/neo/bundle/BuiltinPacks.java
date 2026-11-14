package neo.bundle;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jar の中の resourcepacks/&lt;名前&gt;/ を、パックの選択画面にオン・オフできるパックとして出す。
 * assets/ を持つ物はリソースパック、data/ を持つ物はデータパックとして扱う (両方あれば両方に出る)。
 * データパックは新しいワールドで最初から有効 (PackSource の自動追加)。リソースパックの「最初から有効」は {@link BuiltinPackSelection} が行う。
 * サーバーでも読み込まれるので、クライアント専用のクラスを触らない (P-63)。
 */
public final class BuiltinPacks {
    private static final Logger LOGGER = LoggerFactory.getLogger("neo");
    private static final String ROOT = "resourcepacks";
    /** 「最初から有効・外せる」の印。データパックは新しいワールドでこれが true の物だけ自動で有効になる。 */
    private static final PackSource SOURCE = PackSource.create(
        name -> Component.translatable("neo.pack.source", name), true);

    private static volatile List<Path> packDirs;
    private static volatile Path rootOverride;

    private BuiltinPacks() {}

    /**
     * PackRepository の作成時に、同梱パックの読み込み元を足す。
     * 組み込みのパック (バニラ) を読む元を持つ物だけが対象: ServerPacksSource ならデータパック、それ以外の BuiltInPackSource (ClientPackSource) ならリソースパック。
     */
    public static RepositorySource[] withBundled(RepositorySource[] sources) {
        PackType type = null;
        for (RepositorySource s : sources) {
            if (s instanceof ServerPacksSource) {
                type = PackType.SERVER_DATA;
                break;
            }
            if (s instanceof BuiltInPackSource) type = PackType.CLIENT_RESOURCES;
        }
        if (type == null || dirs().isEmpty()) return sources;
        RepositorySource[] out = Arrays.copyOf(sources, sources.length + 1);
        out[sources.length] = source(type);
        return out;
    }

    /** 指定した種類の同梱パックを読む元 (Forge 版は AddPackFindersEvent でこれを足す)。 */
    public static RepositorySource source(PackType type) {
        return consumer -> loadPacks(type, consumer);
    }

    /**
     * jar の根を外から渡す (Forge 版用。Forge は jar を独自のファイルシステムで開くので、クラスの置き場所から jar を開けない)。
     * 最初に同梱パックを探すより前に呼ぶ。
     */
    public static void setRoot(Path root) {
        rootOverride = root;
    }

    /** 同梱しているリソースパックの ID (assets/ を持つ物)。 */
    public static List<String> clientPackIds() {
        List<String> ids = new ArrayList<>();
        for (Path dir : dirs()) {
            if (Files.isDirectory(dir.resolve("assets"))) ids.add(id(dir));
        }
        return ids;
    }

    private static void loadPacks(PackType type, java.util.function.Consumer<Pack> consumer) {
        String folder = type == PackType.CLIENT_RESOURCES ? "assets" : "data";
        for (Path dir : dirs()) {
            if (!Files.isDirectory(dir.resolve(folder))) continue;
            String name = dir.getFileName().toString().replace("/", "");
            PackLocationInfo info = new PackLocationInfo(id(dir), Component.literal(name), SOURCE, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir), type,
                new PackSelectionConfig(false, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
        }
    }

    private static String id(Path dir) {
        return "neo/" + dir.getFileName().toString().replace("/", "");
    }

    /** resourcepacks/ の下の、pack.mcmeta を持つフォルダ。jar を開くのは最初の1回だけで、開いた jar は閉じずに使い続ける。 */
    private static List<Path> dirs() {
        List<Path> d = packDirs;
        if (d != null) return d;
        synchronized (BuiltinPacks.class) {
            if (packDirs == null) packDirs = scan();
            return packDirs;
        }
    }

    private static List<Path> scan() {
        try {
            Path root = codeRoot().resolve(ROOT);
            if (!Files.isDirectory(root)) return List.of();
            try (Stream<Path> children = Files.list(root)) {
                List<Path> found = children
                    .filter(p -> Files.isDirectory(p) && Files.isRegularFile(p.resolve("pack.mcmeta")))
                    .sorted()
                    .toList();
                if (!found.isEmpty()) LOGGER.info("[NeoMods] 同梱パック: {}", found.stream().map(BuiltinPacks::id).toList());
                return found;
            }
        } catch (Exception e) {
            LOGGER.warn("[NeoMods] 同梱パックを読めない", e);
            return List.of();
        }
    }

    /** この Mod の jar (開発中はクラスのフォルダ) の根。 */
    private static Path codeRoot() throws Exception {
        if (rootOverride != null) return rootOverride;
        Path location = Path.of(BuiltinPacks.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) return location;
        URI uri = URI.create("jar:" + location.toUri());
        FileSystem fs;
        try {
            fs = FileSystems.getFileSystem(uri);
        } catch (java.nio.file.FileSystemNotFoundException e) {
            fs = FileSystems.newFileSystem(uri, Map.of());
        }
        return fs.getPath("/");
    }
}
