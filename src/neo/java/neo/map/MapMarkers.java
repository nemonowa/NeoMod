package neo.map;

import neo.data.NeoData;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.MapBanner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 地図に自動で出す目印: 発見した構造物 (シングルのみ) と、見つけた旗 (バナー)。見つけた位置は記憶して保存する
 * (保存の共通規約: dataFolder/neo/map/markers.dat、NeoData)。見た目はバニラの地図の装飾 (アイコン) で、描画はバニラが行う。
 *
 * 構造物: 配置データはサーバー側にしか無い。シングル・LAN のホストは内蔵サーバーをサーバーのスレッドで読み (MinecraftServer#execute)、
 *   それ以外は MOD を入れたサーバーが送る {@link StructureMarkersPayload} を受け取る (MOD の無いサーバーでは出ない)。
 *   プレイヤーが構造物の範囲に近づいたものだけを「発見済み」にする (まだ遠くのものは出ない = ネタバレしない。StructureDiscovery)。
 * 旗: ロード済みのチャンクにある旗 (BannerBlockEntity) を、クライアント側で見つけて記憶する。チャンクが読み込まれていて旗が無くなっていれば、
 *   バニラの MapItemSavedData#checkBanners と同じく目印を消す。
 * 調べるのはマップを開いた時だけ (毎 tick は何もしない)。設定 map_auto_markers が OFF のときは、開いた時も調べない。
 */
final class MapMarkers {
    private static final String DATA_PATH = "map/markers";
    /** 旗を調べる半径 (チャンク)。マップを開いた時に、この範囲のロード済みチャンクを 1 回だけ調べる。 */
    private static final int BANNER_RADIUS_CHUNKS = 8;
    /** 覚えておく旗の上限 (バニラの地図の装飾の上限 256 より大きく、メモリと保存の大きさを抑える)。 */
    private static final int MAX_BANNERS = 4096;
    /** 手持ちの地図で、装飾を「正立」に描く回転 (バニラは旗・目的地に 180° = 8 を使う)。 */
    static final int UPRIGHT = 8;

    private record Structure(String dimension, String iconKey, int x, int z) {
        String key() {
            return dimension + "|" + iconKey + "|" + x + "|" + z;
        }
    }

    private record Banner(String dimension, int x, int y, int z, int colorId, String name) {
        String key() {
            return dimension + "|" + x + "|" + y + "|" + z;
        }
    }

    private final Path dataFolder;
    private final Map<String, Structure> structures = new HashMap<>();
    private final Map<String, Banner> banners = new HashMap<>();
    /** チャンク → そこにある旗のキー (チャンクを調べ直したとき、無くなった旗を消すため)。 */
    private final Map<String, Map<Long, Set<String>>> bannersByChunk = new HashMap<>();
    private boolean dirty;
    private int version;
    private boolean structureScanRunning;

    MapMarkers(Path dataFolder) {
        this.dataFolder = dataFolder;
        load();
    }

    /** 目印が増減するたびに増える (画面側が、窓の中の目印を作り直すかの判定に使う)。 */
    int version() {
        return version;
    }

    // ---- マップを開いた時だけ ----

    /**
     * マップ (M キーの画面) を開いた時に 1 回だけ呼ぶ: 周りのロード済みチャンクの旗を調べ、構造物も調べる (毎 tick は何もしない)。
     * 開いていない間に近づいた・壊した物は、次に開いた時にその場の周りにあれば反映される。
     */
    void onMapOpened(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null || !ModManagerConfig.MAP_AUTO_MARKERS.getValue()) {
            return;
        }

        String dimension = level.dimension().identifier().toString();
        int playerChunkX = client.player.blockPosition().getX() >> 4;
        int playerChunkZ = client.player.blockPosition().getZ() >> 4;
        for (int dx = -BANNER_RADIUS_CHUNKS; dx <= BANNER_RADIUS_CHUNKS; dx++) {
            for (int dz = -BANNER_RADIUS_CHUNKS; dz <= BANNER_RADIUS_CHUNKS; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                ChunkAccess access = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (access instanceof LevelChunk chunk && !chunk.isEmpty()) {
                    updateChunkBanners(level, dimension, chunk, ChunkPos.pack(chunkX, chunkZ));
                }
            }
        }
        requestStructureScan(client, level.dimension());
    }

    // ---- 旗 ----

    /** チャンクの旗を見つけて記憶し、無くなっていた旗は消す。 */
    private void updateChunkBanners(ClientLevel level, String dimension, LevelChunk chunk, long chunkKey) {
        Set<String> found = new HashSet<>();
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (!(entity instanceof BannerBlockEntity)) {
                continue;
            }

            BlockPos pos = entity.getBlockPos();
            MapBanner mapBanner = MapBanner.fromWorld(level, pos); // 旗でなければ null (バニラと同じ判定)
            if (mapBanner == null) {
                continue;
            }

            Banner banner = new Banner(dimension, pos.getX(), pos.getY(), pos.getZ(), mapBanner.color().getId(),
                mapBanner.name().map(Component::getString).orElse(""));
            found.add(banner.key());
            Banner previous = banners.get(banner.key());
            if (!banner.equals(previous) && (previous != null || banners.size() < MAX_BANNERS)) {
                banners.put(banner.key(), banner);
                changed();
            }
        }

        // チャンクの座標は次元ごとに分ける (同じ座標の別の次元の旗を「無くなった」として消さない)
        Map<Long, Set<String>> byChunk = bannersByChunk.computeIfAbsent(dimension, d -> new HashMap<>());
        Set<String> previousKeys = byChunk.get(chunkKey);
        if (previousKeys != null) {
            for (String key : previousKeys) {
                if (!found.contains(key) && banners.remove(key) != null) {
                    changed();
                }
            }
        }

        if (found.isEmpty()) {
            byChunk.remove(chunkKey);
        } else {
            byChunk.put(chunkKey, found);
        }
    }

    // ---- 構造物 ----

    /** シングル・LAN のホストは内蔵サーバーを直接調べ、それ以外はサーバーへ 1 回だけ頼む (MOD の無いサーバーは読み捨てる)。 */
    private void requestStructureScan(Minecraft client, ResourceKey<Level> dimension) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            if (client.getConnection() != null) {
                client.getConnection().send(new ServerboundCustomPayloadPacket(new StructureScanRequestPayload()));
            }
            return;
        }
        if (structureScanRunning) {
            return;
        }

        structureScanRunning = true;
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        String dimensionId = dimension.identifier().toString();
        server.execute(() -> {
            List<StructureMarkersPayload.Entry> found = List.of();
            try {
                ServerLevel level = server.getLevel(dimension);
                if (level != null) found = StructureDiscovery.scan(level, playerX, playerZ);
            } finally {
                // 失敗しても次の走査ができるように、必ず走査中の印を戻す
                List<StructureMarkersPayload.Entry> result = found;
                client.execute(() -> {
                    structureScanRunning = false;
                    addStructures(dimensionId, result);
                });
            }
        });
    }

    /** マルチ: サーバーが送ってきた構造物を足す (クライアントのスレッド)。 */
    void onServerStructures(StructureMarkersPayload payload) {
        if (ModManagerConfig.MAP_AUTO_MARKERS.getValue()) {
            addStructures(payload.dimension(), payload.entries());
        }
    }

    private void addStructures(String dimensionId, List<StructureMarkersPayload.Entry> found) {
        for (StructureMarkersPayload.Entry entry : found) {
            Structure structure = new Structure(dimensionId, entry.iconKey(), entry.x(), entry.z());
            if (structures.putIfAbsent(structure.key(), structure) == null) {
                changed();
            }
        }
    }

    // ---- 画面に渡す ----

    /** 窓 (ブロック座標の長方形) の中にある目印を、手持ちの地図に載せる形で返す。 */
    List<HeldMapView.Marker> inWindow(String dimension, int minX, int minZ, int maxX, int maxZ) {
        List<HeldMapView.Marker> markers = new ArrayList<>();
        if (!ModManagerConfig.MAP_AUTO_MARKERS.getValue()) {
            return markers;
        }

        for (Structure structure : structures.values()) {
            if (structure.dimension().equals(dimension) && structure.x() >= minX && structure.x() <= maxX
                && structure.z() >= minZ && structure.z() <= maxZ) {
                markers.add(new HeldMapView.Marker(PinIcons.byKey(structure.iconKey()).type(), structure.x() + 0.5, structure.z() + 0.5,
                    UPRIGHT, Optional.empty()));
            }
        }

        for (Banner banner : banners.values()) {
            if (banner.dimension().equals(dimension) && banner.x() >= minX && banner.x() <= maxX
                && banner.z() >= minZ && banner.z() <= maxZ) {
                Optional<Component> name = banner.name().isEmpty() ? Optional.empty() : Optional.of(Component.literal(banner.name()));
                markers.add(new HeldMapView.Marker(new MapBanner(new BlockPos(banner.x(), banner.y(), banner.z()),
                    DyeColor.byId(banner.colorId()), name).getDecoration(), banner.x() + 0.5, banner.z() + 0.5, UPRIGHT, name));
            }
        }

        return markers;
    }

    // ---- 保存 ----

    private void changed() {
        dirty = true;
        version++;
    }

    void saveIfDirty() {
        if (!dirty) {
            return;
        }

        ListTag structureList = new ListTag();
        for (Structure structure : structures.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", structure.dimension());
            tag.putString("icon", structure.iconKey());
            tag.putInt("x", structure.x());
            tag.putInt("z", structure.z());
            structureList.add(tag);
        }

        ListTag bannerList = new ListTag();
        for (Banner banner : banners.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", banner.dimension());
            tag.putInt("x", banner.x());
            tag.putInt("y", banner.y());
            tag.putInt("z", banner.z());
            tag.putInt("color", banner.colorId());
            tag.putString("name", banner.name());
            bannerList.add(tag);
        }

        CompoundTag data = new CompoundTag();
        data.put("structures", structureList);
        data.put("banners", bannerList);
        if (NeoData.write(dataFolder, DATA_PATH, data)) {
            dirty = false;
        }
    }

    private void load() {
        CompoundTag data = NeoData.read(dataFolder, DATA_PATH);
        if (data == null) {
            return;
        }

        ListTag structureList = data.getListOrEmpty("structures");
        for (int i = 0; i < structureList.size(); i++) {
            CompoundTag tag = structureList.getCompoundOrEmpty(i);
            String dimension = tag.getStringOr("dimension", "");
            String icon = tag.getStringOr("icon", "");
            if (!dimension.isEmpty() && !icon.isEmpty()) {
                Structure structure = new Structure(dimension, icon, tag.getIntOr("x", 0), tag.getIntOr("z", 0));
                structures.put(structure.key(), structure);
            }
        }

        ListTag bannerList = data.getListOrEmpty("banners");
        for (int i = 0; i < bannerList.size() && banners.size() < MAX_BANNERS; i++) {
            CompoundTag tag = bannerList.getCompoundOrEmpty(i);
            String dimension = tag.getStringOr("dimension", "");
            if (dimension.isEmpty()) {
                continue;
            }

            Banner banner = new Banner(dimension, tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0),
                tag.getIntOr("color", 0), tag.getStringOr("name", ""));
            banners.put(banner.key(), banner);
            bannersByChunk.computeIfAbsent(dimension, d -> new HashMap<>())
                .computeIfAbsent(ChunkPos.pack(banner.x() >> 4, banner.z() >> 4), key -> new HashSet<>()).add(banner.key());
        }
    }
}
