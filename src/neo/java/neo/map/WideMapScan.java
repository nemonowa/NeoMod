package neo.map;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
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
 * ワールドマップの「広域スキャン」: マップ画面に**いま表示している範囲** (ズームの縮尺に合わせた四角) の地図を一括で作る。
 * シングルプレイで、そのワールドのコマンドの使用 (チート) が ON のときだけ (内蔵サーバーを使う。ゲームモードは問わない)。マップ画面のボタン 1 つで始める・止める。ボタンのツールチップに、範囲と予想時間を出す (neo.map.WorldMapScreen)。
 *
 * 仕組み (バニラの地図の仕組みをそのまま使う):
 *  - 内蔵サーバーで、地点ごとに周囲のチャンクを「読み込みのみ (ティックしない)」で保持し (ServerChunkCache#addTicketAndLoadWithRadius。
 *    未生成なら生成する)、読み込めた地点で、バニラの地図の更新 ({@link MapItem#update}) を、サーバーのレベルに対して実行する
 *  - 地点は格子状 (間隔は、更新円の縁を除いた内側に、格子のどの点も収まる値) で、中心から外へ向かって順に処理する。地図を作るのは、バニラと同じ全スケール (0〜4)
 *  - バニラの更新は、円の縁の 2 ピクセルを 1 つおき (市松模様) にしか更新しないので、各地点では白紙の地図に更新して、縁を除いた内側のピクセルだけを升目へ写す
 *  - 色の計算はサーバースレッド (時間の上限つき。1 tick あたり STEP_BUDGET_NANOS)、地図ストア (MapStore) への書き込みはクライアントスレッド
 *    (升目ごとの色の写しを、キューで渡す)
 *  - 地図ストアには、バニラの地図を歩いて埋めるときと同じ升目 (同じ ID の採番・同じ保存) で入るので、スキャン済みの範囲は保存され、次回も残る
 * 注意: 内蔵サーバーが未生成のチャンクを生成するので、時間とディスク容量がかかる (特に 512 チャンク)。マルチプレイでは使えない。
 */
final class WideMapScan {
    private static final Logger LOGGER = LoggerFactory.getLogger("Neo | WideMapScan");

    private enum Result { NONE, DONE, STOPPED, FAILED }

    /** 1 tick あたりにサーバースレッドで色の計算に使う時間の上限 (ナノ秒)。ゲームが重くなりすぎないための上限。 */
    private static final long STEP_BUDGET_NANOS = 12_000_000L;
    /** 1 tick にクライアントスレッドで地図ストアへ写す時間の上限 (ナノ秒)。 */
    private static final long APPLY_BUDGET_NANOS = 4_000_000L;
    /** 同時に読み込みを待つ地点の数 (多いほど生成は速いが、メモリを使う)。 */
    private static final int WINDOW = 4;
    private static final int FLUSH_EVERY_STEPS = 10;
    /** 作業中の升目の最大数 (これを超えると、古いものから地図ストアへ渡して手放す)。 */
    private static final int MAX_CELLS = 1536;
    /** クライアントが写しきれていない升目がこれを超えたら、新しい地点を始めない。 */
    private static final int MAX_PENDING_MERGES = 3000;
    /** 1 回の地図の更新で更新される列の割合の分母 (バニラの MapItem#update は、1 回に 16 列に 1 列だけ更新する)。 */
    private static final int UPDATE_CALLS = 16;
    /** 地点のチャンクの読み込みを要求する最大の回数 (失敗したときの再試行を含む)。 */
    private static final int MAX_LOAD_ATTEMPTS = 3;

    /** 時間の予想に使う、1 秒あたりに処理できる地点の数の初期値 (実測がまだ無いときの目安。1 地点 = 新しい 64 チャンクほど)。 */
    private static final double DEFAULT_POINTS_PER_SECOND = 1.0;
    /** 直近のスキャンで実測した、1 秒あたりの地点の数 (まだ測っていなければ 0。このゲームを起動している間だけ覚える)。 */
    private static volatile double measuredPointsPerSecond;
    private static Job job;
    private static Result lastResult = Result.NONE;
    private static int lastDone;
    private static int lastTotal;
    private static int lastSpan;

    private WideMapScan() {}

    // ---- 画面から ----

    static boolean running() {
        return job != null;
    }

    /**
     * 使える条件: ワールドマップ ON・シングルプレイ (内蔵サーバー)・そのワールドの「コマンドの使用 (チート)」が ON (内蔵サーバーを使うシングル専用なので、ワールドの設定で判定する)。
     * 設定は無く、条件が合えば自動でマップ画面にボタンが出る。
     */
    static boolean available(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        return ModManagerConfig.WORLD_MAP.getValue()
            && client.player != null
            && server != null
            && server.getWorldData().isAllowCommands();
    }

    /** 画面に出す進み具合。何も実行していない / 実行したことがないときは null。 */
    static @Nullable Component status() {
        Job current = job;
        if (current != null) {
            int done = current.done;
            return Component.translatable("neo.map.scan.running", done, current.total, current.total == 0 ? 100 : done * 100 / current.total, formatDuration(remainingSeconds()));
        }

        return switch (lastResult) {
            case DONE -> Component.translatable("neo.map.scan.done", lastSpan);
            case STOPPED -> Component.translatable("neo.map.scan.stopped", lastDone, lastTotal);
            case FAILED -> Component.translatable("neo.map.scan.failed");
            case NONE -> null;
        };
    }

    // ---- 範囲と予想時間 ----

    /** 範囲の見積もり: 地点の数・読み込むチャンクのおよその数・予想の秒数。measured は、実測した速さから出した予想か (false は初期値の目安)。 */
    record Estimate(int points, long chunks, long seconds, boolean measured) {}

    private static int updateRadiusOf(boolean hasCeiling) {
        return hasCeiling ? 64 : 128;
    }

    /** 地点の間隔: 縁を除いた内側 (最も粗いスケールの縁 2 ピクセルぶんを除く) の円に、どのピクセルも収まる値。 */
    private static int spacingOf(int updateRadius) {
        int inner = updateRadius - 2 * (1 << MapStore.MAX_SCALE);
        return Math.max(8, ((int) (Math.sqrt(2.0) * inner)) & ~7);
    }

    /** 地点ごとに保持するチャンクの半径 (チャンク)。 */
    private static int footprintOf(int updateRadius) {
        return Mth.ceil((updateRadius + 2 * (1 << MapStore.MAX_SCALE)) / 16.0) + 1;
    }

    /** 中心から片側に並べる地点の数 (全体は 2n+1 の正方形の格子)。 */
    private static int halfCount(int spanBlocks, int spacing) {
        return Mth.ceil(spanBlocks / 2.0 / spacing);
    }

    private static double currentRate() {
        double measured = measuredPointsPerSecond;
        return measured > 0.0 ? measured : DEFAULT_POINTS_PER_SECOND;
    }

    /** 表示中の範囲 (一辺 spanBlocks ブロックの正方形) をスキャンしたときの見積もり。 */
    static Estimate estimate(Minecraft client, int spanBlocks) {
        boolean ceiling = client.level != null && client.level.dimensionType().hasCeiling();
        int updateRadius = updateRadiusOf(ceiling);
        int spacing = spacingOf(updateRadius);
        int n = halfCount(spanBlocks, spacing);
        int side = 2 * n + 1;
        int points = side * side;
        long chunkSide = 2L * n * spacing / 16 + 2L * footprintOf(updateRadius) + 1;
        double rate = currentRate();
        return new Estimate(points, chunkSide * chunkSide, Math.max(1L, Math.round(points / rate)), measuredPointsPerSecond > 0.0);
    }

    /** 実行中のスキャンの残り時間 (秒)。始めたばかりで速さが分からないうちは、見積もりの速さを使う。 */
    static long remainingSeconds() {
        Job current = job;
        if (current == null) {
            return 0L;
        }

        double elapsed = (System.nanoTime() - current.startNanos) / 1.0e9;
        double rate = current.done >= 4 && elapsed > 1.0 ? current.done / elapsed : currentRate();
        return Math.max(0L, Math.round((current.total - current.done) / rate));
    }

    /** 「約 N 秒 / 分 / 時間」の文言。 */
    static Component formatDuration(long seconds) {
        if (seconds < 90L) {
            return Component.translatable("neo.map.scan.time.seconds", Math.max(1L, seconds));
        }

        long minutes = Math.round(seconds / 60.0);
        if (minutes < 90L) {
            return Component.translatable("neo.map.scan.time.minutes", minutes);
        }

        long hours = seconds / 3600L;
        return Component.translatable("neo.map.scan.time.hours", hours, (seconds % 3600L) / 60L);
    }

    /** 表示中の範囲 (中心 + 一辺のブロック数) で始める。 */
    static void start(Minecraft client, MapStore store, int centerX, int centerZ, int spanBlocks) {
        ClientLevel clientLevel = client.level;
        LocalPlayer player = client.player;
        if (job != null || clientLevel == null || player == null || !available(client)) {
            return;
        }

        MinecraftServer server = client.getSingleplayerServer();
        ServerLevel level = server == null ? null : server.getLevel(clientLevel.dimension());
        if (level == null) {
            return;
        }

        // 視点用の仮のプレイヤーはワールドに追加しないので ID が 0 (未割り当て) のまま。バニラの地図の更新は持ち主を HashMap に入れ、
        // hashCode が getId を呼ぶので、ID が無いと止まる。本物のエンティティ (1 から数える) と重ならない負の ID を付けておく
        RemotePlayer viewer = new RemotePlayer(clientLevel, player.getGameProfile());
        viewer.setId(-1);
        job = new Job(server, level, store, viewer, spanBlocks, centerX, centerZ);
        lastResult = Result.NONE;
    }

    /** やさしく止める: 作業中の分は地図ストアへ渡してから終わる。 */
    static void stop() {
        Job current = job;
        if (current == null) {
            return;
        }

        current.cancelled = true;
        if (!current.server.isRunning()) {
            job = null;
            return;
        }

        current.schedule(); // 後始末 (チケットの解除と、作業中の分の受け渡し) をサーバースレッドで行う
    }

    /** すぐ捨てる: ワールドを離れる・地図ストアを閉じるとき。 */
    static void abort() {
        Job current = job;
        if (current != null) {
            current.cancelled = true;
            job = null;
            if (current.server.isRunning()) {
                current.schedule();
            }
        }
    }

    // ---- 毎 tick (クライアントスレッド) ----

    static void tick(Minecraft client, MapStore store) {
        Job current = job;
        if (current == null) {
            return;
        }

        ClientLevel level = client.level;
        if (current.store != store || level == null || level.dimension() != current.dimension || !current.server.isRunning() || !available(client)) {
            if (!current.cancelled) {
                stop();
            }

            if (!current.server.isRunning() || current.store != store) {
                job = null;
                return;
            }
        }

        long deadline = System.nanoTime() + APPLY_BUDGET_NANOS;
        CellMerge merge;
        while (System.nanoTime() < deadline && (merge = current.merges.poll()) != null) {
            current.pendingMerges.decrementAndGet();
            apply(store, merge);
        }

        if (current.finished && current.merges.isEmpty()) {
            job = null;
            lastDone = current.done;
            lastTotal = current.total;
            lastSpan = current.spanBlocks;
            double elapsed = (System.nanoTime() - current.startNanos) / 1.0e9;
            if (current.done >= 8 && elapsed > 2.0) {
                measuredPointsPerSecond = current.done / elapsed; // 次の見積もりに使う
            }

            lastResult = current.failed ? Result.FAILED : current.cancelled ? Result.STOPPED : Result.DONE;
            if (lastResult == Result.DONE && client.player != null) {
                client.player.sendOverlayMessage(Component.translatable("neo.map.scan.done", current.spanBlocks));
            }

            store.saveAsync();
            return;
        }

        if (!current.cancelled && !current.finished) {
            current.schedule();
        }
    }

    /** 升目の色を、地図ストアへ写す。色が付いているピクセルだけ上書きする (写しは何度行っても同じ結果)。 */
    private static void apply(MapStore store, CellMerge merge) {
        MapStore.MapRef ref = store.find(merge.dimension(), merge.scale(), merge.cellX(), merge.cellZ());
        MapItemSavedData data = ref != null ? ref.data : MapUpdater.freshData(merge.dimension(), merge.scale(), merge.cellX(), merge.cellZ());
        byte[] colors = data.colors;
        boolean changed = false;
        for (int i = 0; i < colors.length; i++) {
            byte color = merge.colors()[i];
            if (color != 0 && colors[i] != color) {
                colors[i] = color;
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        data.setDirty();
        if (ref != null) {
            ref.refresh();
        } else {
            store.adopt(merge.dimension(), merge.cellX(), merge.cellZ(), data);
        }
    }

    // ---- 型 ----

    /** 升目 (スケール・升目の番号) の鍵。 */
    private record CellKey(int scale, int cellX, int cellZ) {}

    /** 作業中の升目 1 枚ぶんの色 (サーバースレッドだけが触る)。 */
    private static final class Cell {
        final byte[] colors = new byte[MapStore.SIZE * MapStore.SIZE];
        boolean dirty;
    }

    /** 升目の色の写し (サーバースレッド → クライアントスレッド)。 */
    private record CellMerge(ResourceKey<Level> dimension, int scale, int cellX, int cellZ, byte[] colors) {}

    /** 読み込みを待っている / 処理中の 1 地点。 */
    private static final class Pending {
        final int x;
        final int z;
        final ChunkPos chunk;
        @Nullable CompletableFuture<?> future;
        boolean ticketAdded;
        int attempts;
        @Nullable List<MapUpdater.Target> targets;
        int targetIndex;

        Pending(int x, int z) {
            this.x = x;
            this.z = z;
            this.chunk = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        }
    }

    // ---- ジョブ ----

    private static final class Job {
        final MinecraftServer server;
        final ServerLevel level;
        final MapStore store;
        final ResourceKey<Level> dimension;
        final RemotePlayer viewer;
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

        Job(MinecraftServer server, ServerLevel level, MapStore store, RemotePlayer viewer, int spanBlocks, int centerX, int centerZ) {
            this.server = server;
            this.level = level;
            this.store = store;
            this.dimension = level.dimension();
            this.viewer = viewer;
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
            List<MapUpdater.Target> targets = pending.targets;
            if (targets == null) {
                targets = MapUpdater.targetsInRange(pending.x, pending.z, updateRadius);
                pending.targets = targets;
            }

            while (pending.targetIndex < targets.size()) {
                MapUpdater.Target target = targets.get(pending.targetIndex++);
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
        private void scanCell(Pending pending, MapUpdater.Target target) {
            MapItemSavedData scratch = MapUpdater.freshData(dimension, target.scale(), target.cellX(), target.cellZ());
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
}
