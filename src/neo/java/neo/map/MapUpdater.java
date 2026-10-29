package neo.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * バニラの地図の更新処理 ({@link MapItem#update}) を、プレイヤーの周囲の地図に対して実行する。
 * 地図はバニラの全スケール (0〜4 = 1 ピクセルが 1〜16 ブロック) を持ち、スケールごとに独立した地図を更新する。
 * 色の計算・更新範囲・明暗・縁のディザリングは、すべてバニラの地図そのもの
 * (更新半径は 128 / スケール ピクセル = どのスケールでも 128 ブロック、天井のある次元は 64 ブロック)。
 * バニラ (MapItem#inventoryTick) は「手 (メインハンド・オフハンド) に持っている地図」だけを毎 tick 更新する
 * (ロックされていない地図のみ)。つまり多くても 1 tick に 2 枚。ここでは「更新半径に掛かる升目の地図」を
 * 1 tick に UPDATES_PER_TICK 枚ずつ回す (近い升目が先。バニラの最大の 2 倍の枚数なので、負荷を下げたければ減らす)。
 * 新しい升目は、色が 1 ピクセルでも付いた時点でバニラの作成手順 (ID 採番 + 保管) で登録する。
 */
final class MapUpdater {
    private static final int UPDATES_PER_TICK = 4;
    private static final int MAX_PENDING = 128;

    record Target(int scale, int cellX, int cellZ, double distanceSqr) {}

    /** 色がまだ付いていない地図 (登録前)。 */
    private final Map<String, MapItemSavedData> pending = new HashMap<>();
    private int turn;

    void reset() {
        pending.clear();
        turn = 0;
    }

    void tick(Minecraft client, MapStore store) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        // バニラと同じ更新半径 (MapItem#update の radius をブロックに直したもの)
        int radius = level.dimensionType().hasCeiling() ? 64 : 128;
        List<Target> targets = targetsInRange(player.getX(), player.getZ(), radius);
        if (targets.isEmpty()) {
            return;
        }

        int updates = Math.min(UPDATES_PER_TICK, targets.size());
        for (int i = 0; i < updates; i++) {
            Target target = targets.get(Math.floorMod(turn++, targets.size()));
            update(level, player, store, dimension, target);
        }
    }

    private void update(ClientLevel level, LocalPlayer player, MapStore store, ResourceKey<Level> dimension, Target target) {
        MapStore.MapRef ref = store.find(dimension, target.scale(), target.cellX(), target.cellZ());
        MapItemSavedData data;
        String key = null;
        if (ref != null) {
            data = ref.data;
        } else {
            key = MapStore.key(dimension, target.scale(), target.cellX(), target.cellZ());
            if (pending.size() > MAX_PENDING) {
                pending.clear();
            }

            data = pending.computeIfAbsent(key, k -> freshData(dimension, target.scale(), target.cellX(), target.cellZ()));
        }

        ((MapItem) Items.FILLED_MAP).update(level, player, data);

        if (ref != null) {
            ref.refresh();
        } else if (!isBlank(data)) {
            store.adopt(dimension, target.cellX(), target.cellZ(), data);
            pending.remove(key);
        }
    }

    /** 色がまだ付いていない新しい升目の地図 (バニラの作成手順 MapItemSavedData#createFresh)。 */
    static MapItemSavedData freshData(ResourceKey<Level> dimension, int scale, int cellX, int cellZ) {
        int size = MapStore.blockSize(scale);
        return MapItemSavedData.createFresh(cellX * (double) size, cellZ * (double) size, (byte) scale, false, false, dimension);
    }

    /** 全スケールで、更新半径の円に掛かる升目を、近い順 (同じ距離なら細かいスケールが先) に返す。 */
    static List<Target> targetsInRange(double playerX, double playerZ, int radius) {
        List<Target> targets = new ArrayList<>();
        for (int scale = 0; scale <= MapStore.MAX_SCALE; scale++) {
            int size = MapStore.blockSize(scale);
            int minCellX = MapStore.cellOf((int) Math.floor(playerX - radius), scale);
            int maxCellX = MapStore.cellOf((int) Math.floor(playerX + radius), scale);
            int minCellZ = MapStore.cellOf((int) Math.floor(playerZ - radius), scale);
            int maxCellZ = MapStore.cellOf((int) Math.floor(playerZ + radius), scale);
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    double left = MapStore.centerOf(cellX, scale) - size / 2.0;
                    double top = MapStore.centerOf(cellZ, scale) - size / 2.0;
                    double dx = Math.max(Math.max(left - playerX, playerX - (left + size)), 0.0);
                    double dz = Math.max(Math.max(top - playerZ, playerZ - (top + size)), 0.0);
                    double distance = dx * dx + dz * dz;
                    if (distance < (double) radius * radius) {
                        targets.add(new Target(scale, cellX, cellZ, distance));
                    }
                }
            }
        }

        targets.sort(Comparator.comparingDouble(Target::distanceSqr).thenComparingInt(Target::scale));
        return targets;
    }

    private static boolean isBlank(MapItemSavedData data) {
        for (byte color : data.colors) {
            if (color != 0) {
                return false;
            }
        }

        return true;
    }
}
