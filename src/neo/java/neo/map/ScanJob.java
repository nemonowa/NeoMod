package neo.map;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 広域スキャンの本体 (サーバー側)。仕組みは {@link WideMapScan} の説明のとおり。
 * シングル・LAN のホストはクライアントが内蔵サーバーで、マルチは {@link WideMapScanServer} がサーバーで動かす。
 * 色の計算はサーバースレッドで行い、升目ごとの色の写しを {@link #merges} で受け取る側へ渡す。
 * サーバー側からも呼ばれるので、クライアント専用のクラスを参照しない (P-63)。
 */
final class ScanJob {
    private static final Logger LOGGER = LoggerFactory.getLogger("Neo | WideMapScan");

    /** 1 tick あたりにサーバースレッドで色の計算に使う時間の上限 (ナノ秒)。ゲームが重くなりすぎないための上限。 */
    private static final long STEP_BUDGET_NANOS = 12_000_000L;
    /** 同時に読み込みを待つ地点の数 (多いほど生成は速いが、メモリを使う)。 */
    private static final int WINDOW = 4;
    private static final int FLUSH_EVERY_STEPS = 10;
    /** 作業中の升目の最大数 (これを超えると、古いものから地図ストアへ渡して手放す)。 */
    private static final int MAX_CELLS = 1536;
    /** 受け取る側が写しきれていない升目がこれを超えたら、新しい地点を始めない。 */
    static final int MAX_PENDING_MERGES = 3000;
    /** 1 回の地図の更新で更新される列の割合の分母 (バニラの MapItem#update は、1 回に 16 列に 1 列だけ更新する)。 */
    private static final int UPDATE_CALLS = 16;
    /** 地点のチャンクの読み込みを要求する最大の回数 (失敗したときの再試行を含む)。 */
    private static final int MAX_LOAD_ATTEMPTS = 3;

    static int updateRadiusOf(boolean hasCeiling) {
        return hasCeiling ? 64 : 128;
    }

    /** 地点の間隔: 縁を除いた内側 (最も粗いスケールの縁 2 ピクセルぶんを除く) の円に、どのピクセルも収まる値。 */
    static int spacingOf(int updateRadius) { // MapUpdater の外側の更新も同じ間隔を使う
        int inner = updateRadius - 2 * (1 << MapStore.MAX_SCALE);
        return Math.max(8, ((int) (Math.sqrt(2.0) * inner)) & ~7);
    }

    /** 地点ごとに保持するチャンクの半径 (チャンク)。 */
    static int footprintOf(int updateRadius) {
        return Mth.ceil((updateRadius + 2 * (1 << MapStore.MAX_SCALE)) / 16.0) + 1;
    }

    /** 中心から片側に並べる地点の数 (全体は 2n+1 の正方形の格子)。 */
    static int halfCount(int spanBlocks, int spacing) {
        return Mth.ceil(spanBlocks / 2.0 / spacing);
    }

    record Target(int scale, int cellX, int cellZ, double distanceSqr) {}

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

    /** 升目 (スケール・升目の番号) の鍵。 */
    private record CellKey(int scale, int cellX, int cellZ) {}

    /** 作業中の升目 1 枚ぶんの色 (サーバースレッドだけが触る)。 */
    private static final class Cell {
        final byte[] colors = new byte[MapStore.SIZE * MapStore.SIZE];
        boolean dirty;
    }

    /** 升目の色の写し (サーバースレッド → 受け取る側)。 */
    record CellMerge(ResourceKey<Level> dimension, int scale, int cellX, int cellZ, byte[] colors) {}

    /** 読み込みを待っている / 処理中の 1 地点。 */
    private static final class Pending {
        final int x;
        final int z;
        final ChunkPos chunk;
        @Nullable CompletableFuture<?> future;
        boolean ticketAdded;
        int attempts;
        @Nullable List<Target> targets;
        int targetIndex;

        Pending(int x, int z) {
            this.x = x;
            this.z = z;
            this.chunk = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        }
    }

    final MinecraftServer server;
    final ServerLevel level;
    final ResourceKey<Level> dimension;
    final Player viewer;
    /** 範囲の一辺 (ブロック)。 */
    final int spanBlocks;
    final long startNanos = System.nanoTime();
    /** バニラの地図の更新半径 (ブロック。どのスケールでも同じ。天井のある次元は半分)。 */
    final int updateRadius;
    /** 地点ごとに保持するチャンクの半径 (チャンク)。 */
    final int footprint;
    final int total;
    private final int[] pointX;
    private final int[] pointZ;

    // サーバースレッドだけ
    private int next;
    private final List<Pending> inFlight = new ArrayList<>();
    private final Map<CellKey, Cell> work;
    private int stepsSinceFlush;
    private final MapItem mapItem = (MapItem) Items.FILLED_MAP;

    // スレッドをまたぐ
    volatile boolean cancelled;
    volatile boolean finished;
    volatile boolean failed;
    volatile int done;
    private volatile boolean scheduled;
    final ConcurrentLinkedQueue<CellMerge> merges = new ConcurrentLinkedQueue<>();
    final AtomicInteger pendingMerges = new AtomicInteger();

    ScanJob(MinecraftServer server, ServerLevel level, GameProfile profile, int spanBlocks, int centerX, int centerZ) {
        this.server = server;
        this.level = level;
        this.dimension = level.dimension();
        // 視点用の仮のプレイヤー (ワールドに追加しない)。バニラの地図の更新は持ち主を HashMap に入れ、hashCode が getId を呼ぶので、
        // 本物のエンティティ (1 から数える) と重ならない負の ID を付けておく
        this.viewer = new Player(level, profile) {
            @Override
            public GameType gameMode() {
                return GameType.SPECTATOR;
            }
        };
        this.viewer.setId(-1);
        this.spanBlocks = spanBlocks;
        this.updateRadius = updateRadiusOf(level.dimensionType().hasCeiling());
        // 縁を除いた内側 (最も粗いスケール = 1 ピクセル 16 ブロックの、縁 2 ピクセルぶんを除く) の円に、どのピクセルも収まる間隔
        // (バニラの式そのままのシミュレーションで、スケール 2〜4・格子の位相の総当たりとランダム 360 通りで、取りこぼしが 0 であることを確認した)
        int spacing = spacingOf(updateRadius);
        // バニラの更新が読むチャンクを、すべて読み込んでおく: 更新半径 + 視点のピクセルの丸め (最大 1 ピクセル) + 1 つ上の行 (1 ピクセル)。
        // 最も粗いスケールで最大 2 ピクセル = 32 ブロック (地点があるチャンクの分で + 1)。足りないと、バニラの更新がサーバースレッドで同期して読み込む (遅くなる)
        this.footprint = footprintOf(updateRadius);

        int n = halfCount(spanBlocks, spacing);
        int side = 2 * n + 1;
        Integer[] order = new Integer[side * side];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }

        // 中心から外へ。同じ輪の中では角度順 (隣り合う地点が続くので、読み込み済みのチャンクを使い回せる)
        Comparator<Integer> byRing = Comparator.comparingInt(index -> Math.max(Math.abs(index % side - n), Math.abs(index / side - n)));
        java.util.Arrays.sort(order, byRing.thenComparingDouble(index -> Math.atan2(index / side - n, index % side - n)));
        this.pointX = new int[order.length];
        this.pointZ = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            pointX[i] = centerX + (order[i] % side - n) * spacing;
            pointZ[i] = centerZ + (order[i] / side - n) * spacing;
        }

        this.total = order.length;
        this.work = new LinkedHashMap<>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CellKey, Cell> eldest) {
                if (size() > MAX_CELLS) {
                    emit(eldest.getKey(), eldest.getValue());
                    return true;
                }

                return false;
            }
        };
    }

    /** やさしく止める: 作業中の分を渡してから終わる (後始末はサーバースレッド)。 */
    void cancel() {
        cancelled = true;
        if (server.isRunning()) {
            schedule();
        } else {
            finished = true;
        }
    }

    /** サーバースレッドで 1 回ぶんの作業を行わせる (前の作業が終わっていなければ何もしない)。 */
    void schedule() {
        if (scheduled) {
            return;
        }

        scheduled = true;
        server.execute(() -> {
            try {
                step();
            } finally {
                scheduled = false;
            }
        });
    }

    /** 1 回ぶんの作業 (サーバースレッド)。 */
    private void step() {
        try {
            if (cancelled) {
                cleanup();
                return;
            }

            long deadline = System.nanoTime() + STEP_BUDGET_NANOS;
            while (inFlight.size() < WINDOW && next < total && pendingMerges.get() < MAX_PENDING_MERGES) {
                request(next++);
            }

            for (Iterator<Pending> it = inFlight.iterator(); it.hasNext() && System.nanoTime() < deadline; ) {
                Pending pending = it.next();
                CompletableFuture<?> future = pending.future;
                if (future == null) {
                    if (pending.attempts < MAX_LOAD_ATTEMPTS) {
                        load(pending); // 要求に失敗していた: もう一度
                        continue;
                    }

                    LOGGER.warn("Wide map scan: skipped the point ({}, {})", pending.x, pending.z);
                } else if (!future.isDone()) {
                    continue;
                } else if (future.isCompletedExceptionally()) {
                    LOGGER.warn("Wide map scan: chunk loading failed around ({}, {})", pending.x, pending.z);
                } else if (!process(pending, deadline)) {
                    continue; // 時間切れ (続きは次の回)
                }

                release(pending);
                done++;
                it.remove();
            }

            if (++stepsSinceFlush >= FLUSH_EVERY_STEPS) {
                stepsSinceFlush = 0;
                flush();
            }

            if (next >= total && inFlight.isEmpty()) {
                flush();
                finished = true;
            }
        } catch (Throwable t) {
            LOGGER.error("Wide map scan failed", t);
            failed = true;
            cancelled = true;
            cleanup();
        }
    }

    private void request(int index) {
        Pending pending = new Pending(pointX[index], pointZ[index]);
        load(pending);
        inFlight.add(pending);
    }

    /** 地点の周りのチャンクを、読み込みのみのチケットで保持して読み込む (未生成なら生成する)。失敗したら future は null。 */
    private void load(Pending pending) {
        pending.attempts++;
        pending.ticketAdded = true; // 例外のときもチケットが付いているかもしれないので、解除の対象にする
        try {
            pending.future = level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PLAYER_LOADING, pending.chunk, footprint);
        } catch (RuntimeException e) {
            LOGGER.warn("Wide map scan: could not load chunks around ({}, {})", pending.x, pending.z, e);
            release(pending);
            pending.future = null;
        }
    }

    private void release(Pending pending) {
        if (pending.ticketAdded) {
            pending.ticketAdded = false;
            level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, pending.chunk, footprint);
        }
    }

    /** 1 地点ぶんの地図の更新。終わったら true、時間切れで途中なら false。 */
    private boolean process(Pending pending, long deadline) {
        List<Target> targets = pending.targets;
        if (targets == null) {
            targets = targetsInRange(pending.x, pending.z, updateRadius);
            pending.targets = targets;
        }

        while (pending.targetIndex < targets.size()) {
            Target target = targets.get(pending.targetIndex++);
            scanCell(pending, target);
            if (System.nanoTime() >= deadline) {
                break;
            }
        }

        return pending.targetIndex >= targets.size();
    }

    /**
     * 1 つの升目を、1 つの地点から更新する。白紙の地図に、バニラの更新を 16 回 (= すべての列が 1 回ずつ) かけて、
     * 縁 (ディザリングされる 2 ピクセル) の内側のピクセルだけを、作業中の升目へ写す。
     */
    private void scanCell(Pending pending, Target target) {
        MapItemSavedData scratch = freshData(dimension, target.scale(), target.cellX(), target.cellZ());
        viewer.setPos(pending.x, level.getMinY() + 1.0, pending.z);
        for (int i = 0; i < UPDATE_CALLS; i++) {
            mapItem.update(level, viewer, scratch);
        }

        Cell cell = work.computeIfAbsent(new CellKey(target.scale(), target.cellX(), target.cellZ()), key -> new Cell());

        // バニラの MapItem#update と同じ式で、視点のピクセルと更新半径 (ピクセル) を求める
        int pixelSize = 1 << target.scale();
        int viewerX = Mth.floor(pending.x - (double) scratch.centerX) / pixelSize + 64;
        int viewerZ = Mth.floor(pending.z - (double) scratch.centerZ) / pixelSize + 64;
        int radius = 128 / pixelSize;
        if (level.dimensionType().hasCeiling()) {
            radius /= 2;
        }

        int innerSquared = (radius - 2) * (radius - 2);
        byte[] from = scratch.colors;
        byte[] to = cell.colors;
        for (int z = 0; z < MapStore.SIZE; z++) {
            for (int x = 0; x < MapStore.SIZE; x++) {
                int dx = x - viewerX;
                int dz = z - viewerZ;
                int index = z * MapStore.SIZE + x;
                if (dx * dx + dz * dz <= innerSquared && from[index] != 0 && to[index] != from[index]) {
                    to[index] = from[index];
                    cell.dirty = true;
                }
            }
        }
    }

    /** 作業中の升目のうち、変わったものを、クライアントへ渡す。 */
    private void flush() {
        for (Map.Entry<CellKey, Cell> entry : work.entrySet()) {
            emit(entry.getKey(), entry.getValue());
        }
    }

    private void emit(CellKey key, Cell cell) {
        if (!cell.dirty) {
            return;
        }

        cell.dirty = false;
        merges.add(new CellMerge(dimension, key.scale(), key.cellX(), key.cellZ(), cell.colors.clone()));
        pendingMerges.incrementAndGet();
    }

    /** 止めるとき: チケットを外し、作業中の分を渡して、終わりにする (サーバースレッド)。 */
    private void cleanup() {
        for (Pending pending : inFlight) {
            release(pending);
        }

        inFlight.clear();
        flush();
        work.clear();
        finished = true;
    }
}
