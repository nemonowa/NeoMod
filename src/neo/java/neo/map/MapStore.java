package neo.map;

import com.mojang.datafixers.DataFixer;
import neo.data.NeoData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 1 ワールド分の地図の保管庫。保存の規約 (NeoData) に従う:
 *  - バニラの地図データ (MapItemSavedData) は、バニラの階層・形式のまま dataFolder/minecraft/maps/&lt;id&gt;.dat に置く
 *    (バニラの SavedDataStorage で読み書きするので、バニラの地図アイテムが読むのと同じファイル)
 *  - 索引 (どの升目がどの ID か) と ID の採番カウンタは Mod 固有のデータなので dataFolder/neo/map/index.dat に置く
 * dataFolder は、シングルではワールドの data フォルダ (&lt;ワールド&gt;/data)、マルチではクライアント側の同じ構造のフォルダ。
 *
 * ID の採番: バニラの MapIndex (minecraft/maps/last_id.dat) は触らない。シングルではサーバーが同じフォルダの last_id を持っていて、
 * こちらが先に進めると、サーバーが次に作る地図が同じ ID になり、プレイヤーの地図を上書きしてしまうため。
 * そこで索引のカウンタで ID_BASE 以上を採番する (バニラの地図アイテムの ID と重ならない範囲)。
 * 地図の作り方 (createFresh) と保存 (SavedDataStorage) はバニラの地図アイテム作成と同じ。
 */
final class MapStore implements AutoCloseable {
    private static final String INDEX_PATH = "map/index";
    /**
     * この Mod が採番する地図 ID の最初の値 (バニラの地図アイテムの ID と重ならない範囲)。
     * 既存のワールドは、索引に保存した next (これ以上) から続けるので、値を変えても今ある ID は変わらない。
     * 採番のとき、同じ ID のファイルが既にあれば飛ばす (nextFreeId) ので、バニラの地図を上書きしない。
     */
    static final int ID_BASE = 1000;
    /** 地図 1 枚の一辺のピクセル数 (バニラの固定値)。 */
    static final int SIZE = 128;
    private static final int OFFSET = 64;
    static final int MAX_SCALE = MapItemSavedData.MAX_SCALE;

    /** 読み込み済みの地図 1 枚。revision はピクセルが変わるたびに増える (画面のテクスチャ更新の判定用)。 */
    static final class MapRef {
        final MapId id;
        final MapItemSavedData data;
        int revision;
        private int hash;

        MapRef(MapId id, MapItemSavedData data) {
            this.id = id;
            this.data = data;
            this.hash = Arrays.hashCode(data.colors);
        }

        /** colors が変わっていれば revision を進める。 */
        void refresh() {
            int current = Arrays.hashCode(data.colors);
            if (current != hash) {
                hash = current;
                revision++;
            }
        }

        /** 地図が覆うブロック数 (一辺)。 */
        int blockSize() {
            return SIZE << data.scale;
        }

        /** 地図の左上 (ブロック座標)。 */
        int originX() {
            return data.centerX - blockSize() / 2;
        }

        int originZ() {
            return data.centerZ - blockSize() / 2;
        }

        /** そのブロックが載るピクセルに色が付いている (探索済み) か。範囲外は false。 */
        boolean isExplored(int worldX, int worldZ) {
            int x = (worldX - originX()) >> data.scale;
            int z = (worldZ - originZ()) >> data.scale;
            return x >= 0 && z >= 0 && x < SIZE && z < SIZE && data.colors[z * SIZE + x] != 0;
        }
    }

    private final SavedDataStorage storage;
    private final Path dataFolder;
    private int nextId = ID_BASE;
    private final Map<String, Integer> index = new HashMap<>();
    private final Map<Integer, MapRef> loaded = new HashMap<>();
    private boolean indexDirty;
    private boolean closed;

    MapStore(Path dataFolder, DataFixer fixer, HolderLookup.Provider registries) {
        this.storage = new SavedDataStorage(dataFolder, fixer, registries);
        this.dataFolder = dataFolder;
        readIndex();
    }

    /** そのスケールの地図の一辺のブロック数。 */
    static int blockSize(int scale) {
        return SIZE << scale;
    }

    /** ブロック座標が属する升目 (バニラの MapItemSavedData#createFresh と同じ割り当て)。 */
    static int cellOf(int blockCoord, int scale) {
        return Math.floorDiv(blockCoord + OFFSET, blockSize(scale));
    }

    /** 升目の中心 (バニラの地図の centerX / centerZ と同じ)。 */
    static int centerOf(int cell, int scale) {
        int size = blockSize(scale);
        return cell * size + size / 2 - OFFSET;
    }

    static String key(ResourceKey<Level> dimension, int scale, int cellX, int cellZ) {
        return dimension.identifier() + "|" + scale + "|" + cellX + "|" + cellZ;
    }

    /** 読み込み済みの地図だけを返す (ディスクは読まない)。 */
    MapRef peek(ResourceKey<Level> dimension, int scale, int cellX, int cellZ) {
        Integer id = index.get(key(dimension, scale, cellX, cellZ));
        return id == null ? null : loaded.get(id);
    }

    boolean has(ResourceKey<Level> dimension, int scale, int cellX, int cellZ) {
        return index.containsKey(key(dimension, scale, cellX, cellZ));
    }

    /** 地図を返す。未読み込みならディスク (バニラの .dat) から読む。無ければ null。 */
    MapRef find(ResourceKey<Level> dimension, int scale, int cellX, int cellZ) {
        String key = key(dimension, scale, cellX, cellZ);
        Integer id = index.get(key);
        if (id == null) {
            return null;
        }

        MapRef ref = loaded.get(id);
        if (ref != null) {
            return ref;
        }

        MapId mapId = new MapId(id);
        MapItemSavedData data = storage.get(MapItemSavedData.type(mapId));
        if (data == null) {
            // 索引にあるのにファイルが無い (手で消された等): 索引から外して、次回作り直せるようにする
            index.remove(key);
            indexDirty = true;
            return null;
        }

        ref = new MapRef(mapId, data);
        loaded.put(id, ref);
        return ref;
    }

    /**
     * 新しい地図を登録する。バニラの地図アイテム作成 (MapItem#createNewSavedData) と同じ手順:
     * 索引のカウンタから次の ID を取り (ID_BASE 以上。既にファイルがある ID は飛ばす)、データを保管庫に set する。
     */
    MapRef adopt(ResourceKey<Level> dimension, int cellX, int cellZ, MapItemSavedData data) {
        int scale = data.scale;
        MapId id = new MapId(nextFreeId());
        storage.set(MapItemSavedData.type(id), data);
        index.put(key(dimension, scale, cellX, cellZ), id.id());
        indexDirty = true;
        MapRef ref = new MapRef(id, data);
        loaded.put(id.id(), ref);
        return ref;
    }

    /**
     * 次に使う ID。minecraft/maps/&lt;id&gt;.dat が既にあれば (バニラの地図アイテムが先に使っている) 飛ばして、
     * 次の空きを返す。返した ID の次を nextId に進める。
     */
    private int nextFreeId() {
        Path maps = dataFolder.resolve("minecraft").resolve("maps");
        while (nextId < Integer.MAX_VALUE && (loaded.containsKey(nextId) || Files.exists(maps.resolve(nextId + ".dat")))) {
            nextId++;
        }

        return nextId++;
    }

    /** 変更のあった地図を非同期で保存する (バニラのオートセーブと同じ)。 */
    void saveAsync() {
        if (closed) {
            return;
        }

        storage.scheduleSave();
        writeIndexIfDirty();
    }

    /** 保存が終わるまで待つ。 */
    void saveNow() {
        if (closed) {
            return;
        }

        storage.saveAndJoin();
        writeIndexIfDirty();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        writeIndexIfDirty();
        storage.close(); // 保存して閉じる
        closed = true;
        loaded.clear();
    }

    private void readIndex() {
        CompoundTag root = NeoData.read(dataFolder, INDEX_PATH);
        if (root == null) {
            return;
        }

        nextId = Math.max(ID_BASE, root.getIntOr("next", ID_BASE));
        ListTag list = root.getListOrEmpty("maps");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            String key = entry.getStringOr("key", "");
            int id = entry.getIntOr("id", -1);
            if (!key.isEmpty() && id >= 0) {
                index.put(key, id);
                nextId = Math.max(nextId, id + 1);
            }
        }
    }

    private void writeIndexIfDirty() {
        if (!indexDirty) {
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt("next", nextId);
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : index.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", entry.getKey());
            tag.putInt("id", entry.getValue());
            list.add(tag);
        }

        root.put("maps", list);
        if (NeoData.write(dataFolder, INDEX_PATH, root)) {
            indexDirty = false;
        }
    }
}
