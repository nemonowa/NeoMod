package neo.map;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import org.slf4j.Logger;

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
 *
 * 外側 (更新半径より遠く、クライアントにロード済みのチャンクの範囲まで): バニラの更新は「更新する人」の位置の周りしか見ないので、
 * 仮のプレイヤー (ワールドに追加しない) の位置をずらして、同じ MapItem#update を呼ぶ (色の計算は内側と同じバニラのもの)。
 * 地点は世界に固定した格子 (間隔は WideMapScan と同じ) で、プレイヤーからの距離でリングに分け、近いリングから 1 リングずつ進める
 * (JourneyMap のように、内側は毎回・外側は少しずつ)。バニラの更新は 1 回で 16 列に 1 列だけなので、1 つの地図に連続 16 回かけて全列を更新する。
 * ロード済みのチャンクが無い地点は飛ばす。状態は保存しない (地図は MapStore がバニラ形式の .dat に保存する)。
 */
final class MapUpdater {
    private static final int UPDATES_PER_TICK = 4;
    private static final int MAX_PENDING = 128;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 外側の更新に使う 1 tick あたりの時間 (ナノ秒。内側の UPDATES_PER_TICK とは別枠)。件数ではなく時間で決めるので、粗いスケールの重い更新でも 1 tick の負荷は変わらない。 */
    private static final long OUTER_BUDGET_NANOS = 2_000_000L;
    /** 時間を確かめる間隔 (update の呼び出し回数)。確認のたびに地図の変化の判定 (ハッシュ) が走るので、数回まとめる。 */
    private static final int OUTER_CALLS_PER_CHECK = 4;
    /** 前の tick からこれより長く空いたら (処理落ち)、その tick の外側の更新は休む。 */
    private static final long LAG_NANOS = 80_000_000L;
    /** スケールごとの更新の頻度: 巡回の何回に 1 回、そのスケールを更新するか (粗いスケールは 1 回の更新で読むブロックが 4 倍ずつ増えるので、間引く)。 */
    private static final int[] SCALE_INTERVAL = {1, 1, 1, 2, 4};
    /** バニラの更新は 1 回で 16 列に 1 列なので、全列を更新するのに必要な連続の呼び出し回数。 */
    private static final int FULL_PASS_CALLS = 16;
    /** 外側の更新の限界 (ブロック)。実際の範囲はロード済みのチャンクで決まる。 */
    private static final int MAX_OUTER_RANGE = 32 * 16;
    /** 外側の更新の対象が無いときに、次の巡回を始めるまで待つ tick。 */
    private static final int OUTER_IDLE_TICKS = 40;

    /** 外側の更新の地点 (ブロック座標)。 */
    private record Point(int x, int z) {}

    record Target(int scale, int cellX, int cellZ, double distanceSqr) {}

    /** 色がまだ付いていない地図 (登録前)。 */
    private final Map<String, MapItemSavedData> pending = new HashMap<>();
    private int turn;

    // 外側の更新の進み具合
    private ClientLevel viewerLevel;
    private RemotePlayer viewer;
    /** 今の巡回のリング (添字 = リングの番号 - 1)。null なら次の tick で組み直す。 */
    private List<List<Point>> rings;
    private int ringIndex;
    private int pointIndex;
    private List<Target> pointTargets;
    private int targetIndex;
    private int callsDone;
    private int outerIdle;
    private long lastTickNanos;
    /** 何巡目か (SCALE_INTERVAL の判定用)。 */
    private int passCount;
    // 1 巡ぶんの統計 (巡回が終わったときにログへ出して、実機で速さを確かめられるようにする)
    private int passPoints;
    private int passCalls;
    private int passTicks;
    private long passNanos;

    void reset() {
        pending.clear();
        turn = 0;
        clearOuter();
        viewerLevel = null;
        viewer = null;
    }

    private void clearOuter() {
        rings = null;
        ringIndex = 0;
        pointIndex = 0;
        pointTargets = null;
        targetIndex = 0;
        callsDone = 0;
        outerIdle = 0;
        lastTickNanos = 0;
        passCount = 0;
        resetPassStats();
    }

    private void resetPassStats() {
        passPoints = 0;
        passCalls = 0;
        passTicks = 0;
        passNanos = 0;
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
        if (!targets.isEmpty()) {
            int updates = Math.min(UPDATES_PER_TICK, targets.size());
            for (int i = 0; i < updates; i++) {
                Target target = targets.get(Math.floorMod(turn++, targets.size()));
                update(level, player, store, dimension, target, 1);
            }
        }

        tickOuter(level, player, store, dimension, radius);
    }

    // ---- 外側 (更新半径より遠く) ----

    private void tickOuter(ClientLevel level, LocalPlayer player, MapStore store, ResourceKey<Level> dimension, int radius) {
        if (viewerLevel != level) {
            // 別のレベル (ディメンション移動・再接続): 仮のプレイヤーも進み具合も作り直す
            clearOuter();
            viewer = new RemotePlayer(level, player.getGameProfile());
            viewer.setId(-1); // ワールドに追加しないので ID が 0 のまま。更新は持ち主を HashMap に入れ、hashCode が getId を呼ぶので、本物と重ならない負の ID を付ける (WideMapScan と同じ)
            viewerLevel = level;
        }

        if (outerIdle > 0) {
            outerIdle--;
            return;
        }

        long start = System.nanoTime();
        boolean lagging = lastTickNanos != 0 && start - lastTickNanos > LAG_NANOS;
        lastTickNanos = start;
        if (lagging) {
            return; // 処理落ちのときは、外側の更新を休んで負荷を増やさない
        }

        long deadline = start + OUTER_BUDGET_NANOS;
        boolean worked = false;
        do {
            if (pointTargets == null && !nextPoint(level, player, radius)) {
                break;
            }

            Point point = rings.get(ringIndex).get(pointIndex);
            Target target = pointTargets.get(targetIndex);
            int calls = Math.min(OUTER_CALLS_PER_CHECK, FULL_PASS_CALLS - callsDone);
            viewer.setPos(point.x(), player.getY(), point.z());
            update(level, viewer, store, dimension, target, calls);
            worked = true;
            passCalls += calls;
            callsDone += calls;
            if (callsDone >= FULL_PASS_CALLS) {
                callsDone = 0;
                if (++targetIndex >= pointTargets.size()) {
                    pointTargets = null;
                    pointIndex++;
                    passPoints++;
                }
            }
        } while (System.nanoTime() < deadline);

        if (worked) {
            passTicks++;
            passNanos += System.nanoTime() - start;
        }
    }

    /**
     * 次の地点へ進む (必要なら次のリング・次の巡回へ)。進めたら true。対象が無ければ、しばらく待つ (false)。
     * 巡回の始めに、そのときのプレイヤーの位置から、リングを組み直す。
     */
    private boolean nextPoint(ClientLevel level, LocalPlayer player, int radius) {
        if (rings == null) {
            rings = buildRings(level, player.getX(), player.getZ(), radius);
            ringIndex = 0;
            pointIndex = 0;
        }

        while (ringIndex < rings.size()) {
            List<Point> ring = rings.get(ringIndex);
            if (pointIndex < ring.size()) {
                Point point = ring.get(pointIndex);
                List<Target> targets = targetsInRange(point.x(), point.z(), radius);
                targets.removeIf(target -> passCount % SCALE_INTERVAL[target.scale()] != 0);
                if (!targets.isEmpty()) {
                    pointTargets = targets;
                    targetIndex = 0;
                    callsDone = 0;
                    return true;
                }

                pointIndex++;
                continue;
            }

            ringIndex++;
            pointIndex = 0;
        }

        // 巡回が終わった: 次の tick に組み直す。対象が少ないときに毎 tick 組み直さないよう、少し待つ
        if (passCalls > 0) {
            LOGGER.info("[NeoMap] 外側の更新 {} 巡目: 地点 {}、update {} 回、{} tick、合計 {} ms (1 tick 平均 {} ms)",
                passCount, passPoints, passCalls, passTicks, passNanos / 1_000_000, passTicks == 0 ? 0 : passNanos / passTicks / 1000 / 1000.0);
        }

        resetPassStats();
        passCount++;
        rings = null;
        outerIdle = OUTER_IDLE_TICKS;
        return false;
    }

    /**
     * 外側の地点を、プレイヤーからの距離でリングに分ける。リング n = 距離が spacing の n 倍以上 (n+1) 倍未満 (n は 1 から。
     * 0 は内側の更新が受け持つ)。格子は世界に固定 (プレイヤーが動いても同じ地点)。ロード済みのチャンクが近くに無い地点は入れない。
     */
    private static List<List<Point>> buildRings(ClientLevel level, double playerX, double playerZ, int radius) {
        int spacing = WideMapScan.spacingOf(radius);
        int ringCount = MAX_OUTER_RANGE / spacing;
        List<List<Point>> rings = new ArrayList<>();
        for (int i = 0; i < ringCount; i++) {
            rings.add(new ArrayList<>());
        }

        int span = (ringCount + 1) * spacing;
        int minGridX = Math.floorDiv((int) Math.floor(playerX) - span, spacing);
        int maxGridX = Math.floorDiv((int) Math.floor(playerX) + span, spacing);
        int minGridZ = Math.floorDiv((int) Math.floor(playerZ) - span, spacing);
        int maxGridZ = Math.floorDiv((int) Math.floor(playerZ) + span, spacing);
        for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
            for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
                int x = gridX * spacing;
                int z = gridZ * spacing;
                int ring = (int) (Math.hypot(x - playerX, z - playerZ) / spacing);
                if (ring >= 1 && ring <= ringCount && hasLoadedChunkNear(level, x, z, spacing)) {
                    rings.get(ring - 1).add(new Point(x, z));
                }
            }
        }

        for (List<Point> ring : rings) {
            ring.sort(Comparator.comparingDouble(point -> Math.hypot(point.x() - playerX, point.z() - playerZ)));
        }

        return rings;
    }

    /** 地点の周り (格子 1 つぶん) に、ロード済みのチャンクがあるか。中心と、周り 8 方向の半間隔先を調べる。 */
    private static boolean hasLoadedChunkNear(ClientLevel level, int x, int z, int spacing) {
        int half = spacing / 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.hasChunk(Mth.floor(x + dx * half) >> 4, Mth.floor(z + dz * half) >> 4)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** 1 つの升目を、who の位置から calls 回更新する (内側は本物のプレイヤーで 1 回、外側は仮のプレイヤーで連続の回数)。 */
    private void update(ClientLevel level, Player who, MapStore store, ResourceKey<Level> dimension, Target target, int calls) {
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

        MapItem mapItem = (MapItem) Items.FILLED_MAP;
        for (int i = 0; i < calls; i++) {
            mapItem.update(level, who, data);
        }

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
