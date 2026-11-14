package neo.map;

import neo.menu.config.ModManagerConfig;
import neo.util.PermissionGate;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ワールドマップの「広域スキャン」: マップ画面に**いま表示している範囲** (ズームの縮尺に合わせた四角) の地図を一括で作る。
 * シングル・LAN のホストは、そのワールドのコマンドの使用 (チート) が ON のとき内蔵サーバーで動かす (ScanJob)。マルチは、MOD を入れたサーバーで
 * 自分の権限がゲームマスター以上のとき、サーバーに頼んで動かしてもらい、升目を受け取る (WideMapScanServer。サーバー全体で同時に 1 つだけ)。マップ画面のボタン 1 つで始める・止める。ボタンのツールチップに、範囲と予想時間を出す (neo.map.WorldMapScreen)。
 *
 * 仕組み (バニラの地図の仕組みをそのまま使う):
 *  - 内蔵サーバーで、地点ごとに周囲のチャンクを「読み込みのみ (ティックしない)」で保持し (ServerChunkCache#addTicketAndLoadWithRadius。
 *    未生成なら生成する)、読み込めた地点で、バニラの地図の更新 ({@link MapItem#update}) を、サーバーのレベルに対して実行する
 *  - 地点は格子状 (間隔は、更新円の縁を除いた内側に、格子のどの点も収まる値) で、中心から外へ向かって順に処理する。地図を作るのは、バニラと同じ全スケール (0〜4)
 *  - バニラの更新は、円の縁の 2 ピクセルを 1 つおき (市松模様) にしか更新しないので、各地点では白紙の地図に更新して、縁を除いた内側のピクセルだけを升目へ写す
 *  - 色の計算はサーバースレッド (時間の上限つき。1 tick あたり STEP_BUDGET_NANOS)、地図ストア (MapStore) への書き込みはクライアントスレッド
 *    (升目ごとの色の写しを、キューで渡す)
 *  - 地図ストアには、バニラの地図を歩いて埋めるときと同じ升目 (同じ ID の採番・同じ保存) で入るので、スキャン済みの範囲は保存され、次回も残る
 * 注意: サーバーが未生成のチャンクを生成するので、時間とディスク容量がかかる (特に 512 チャンク)。
 */
final class WideMapScan {
    private enum Result { NONE, DONE, STOPPED, FAILED, BUSY }

    /** 1 tick にクライアントスレッドで地図ストアへ写す時間の上限 (ナノ秒)。 */
    private static final long APPLY_BUDGET_NANOS = 4_000_000L;
    /** 時間の予想に使う、1 秒あたりに処理できる地点の数の初期値 (実測がまだ無いときの目安。1 地点 = 新しい 64 チャンクほど)。 */
    private static final double DEFAULT_POINTS_PER_SECOND = 1.0;
    /** 直近のスキャンで実測した、1 秒あたりの地点の数 (まだ測っていなければ 0。このゲームを起動している間だけ覚える)。 */
    private static volatile double measuredPointsPerSecond;

    /** シングル・LAN のホスト: 内蔵サーバーで動かしているジョブと、写す先の地図ストア。 */
    private static ScanJob job;
    private static MapStore jobStore;
    /** マルチ: サーバーに頼んで動かしているもの (クライアントスレッドだけ)。 */
    private static Remote remote;
    /** 広域スキャンを受け付けると知らせてきた接続 (MOD を入れたサーバー)。別の接続になれば使えない。 */
    private static volatile ClientPacketListener supportingConnection;
    private static final ConcurrentLinkedQueue<ScanJob.CellMerge> received = new ConcurrentLinkedQueue<>();

    private static Result lastResult = Result.NONE;
    private static int lastDone;
    private static int lastTotal;
    private static int lastSpan;

    private static final class Remote {
        final MapStore store;
        final int spanBlocks;
        final long startNanos = System.nanoTime();
        int done;
        int total;
        boolean stopRequested;

        Remote(MapStore store, int spanBlocks, int total) {
            this.store = store;
            this.spanBlocks = spanBlocks;
            this.total = total;
        }
    }

    private WideMapScan() {}

    // ---- 画面から ----

    static boolean running() {
        return job != null || remote != null;
    }

    private static int done() {
        return job != null ? job.done : remote != null ? remote.done : 0;
    }

    private static int total() {
        return job != null ? job.total : remote != null ? remote.total : 0;
    }

    private static long startNanos() {
        return job != null ? job.startNanos : remote != null ? remote.startNanos : System.nanoTime();
    }

    /**
     * 使える条件: ワールドマップ ON で、シングル・LAN のホストならそのワールドの「コマンドの使用 (チート)」が ON、
     * マルチなら MOD を入れたサーバーで自分の権限がゲームマスター以上。設定は無く、条件が合えば自動でマップ画面にボタンが出る。
     */
    static boolean available(Minecraft client) {
        if (!ModManagerConfig.WORLD_MAP.getValue() || client.player == null) {
            return false;
        }

        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            return server.getWorldData().isAllowCommands();
        }

        ClientPacketListener connection = client.getConnection();
        return connection != null && connection == supportingConnection && PermissionGate.allowed(client.player);
    }

    /** 画面に出す進み具合。何も実行していない / 実行したことがないときは null。 */
    static @Nullable Component status() {
        if (running()) {
            int done = done();
            int total = total();
            return Component.translatable("neo.map.scan.running", done, total, total == 0 ? 100 : done * 100 / total, formatDuration(remainingSeconds()));
        }

        return switch (lastResult) {
            case DONE -> Component.translatable("neo.map.scan.done", lastSpan);
            case STOPPED -> Component.translatable("neo.map.scan.stopped", lastDone, lastTotal);
            case FAILED -> Component.translatable("neo.map.scan.failed");
            case BUSY -> Component.translatable("neo.map.scan.busy");
            case NONE -> null;
        };
    }

    // ---- 範囲と予想時間 ----

    /** 範囲の見積もり: 地点の数・読み込むチャンクのおよその数・予想の秒数。measured は、実測した速さから出した予想か (false は初期値の目安)。 */
    record Estimate(int points, long chunks, long seconds, boolean measured) {}

    private static double currentRate() {
        double measured = measuredPointsPerSecond;
        return measured > 0.0 ? measured : DEFAULT_POINTS_PER_SECOND;
    }

    /** 表示中の範囲 (一辺 spanBlocks ブロックの正方形) をスキャンしたときの見積もり。 */
    static Estimate estimate(Minecraft client, int spanBlocks) {
        boolean ceiling = client.level != null && client.level.dimensionType().hasCeiling();
        int updateRadius = ScanJob.updateRadiusOf(ceiling);
        int spacing = ScanJob.spacingOf(updateRadius);
        int n = ScanJob.halfCount(spanBlocks, spacing);
        int side = 2 * n + 1;
        int points = side * side;
        long chunkSide = 2L * n * spacing / 16 + 2L * ScanJob.footprintOf(updateRadius) + 1;
        double rate = currentRate();
        return new Estimate(points, chunkSide * chunkSide, Math.max(1L, Math.round(points / rate)), measuredPointsPerSecond > 0.0);
    }

    /** 実行中のスキャンの残り時間 (秒)。始めたばかりで速さが分からないうちは、見積もりの速さを使う。 */
    static long remainingSeconds() {
        if (!running()) {
            return 0L;
        }

        int done = done();
        double elapsed = (System.nanoTime() - startNanos()) / 1.0e9;
        double rate = done >= 4 && elapsed > 1.0 ? done / elapsed : currentRate();
        return Math.max(0L, Math.round((total() - done) / rate));
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
        if (running() || clientLevel == null || player == null || !available(client)) {
            return;
        }

        lastResult = Result.NONE;
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            received.clear();
            remote = new Remote(store, spanBlocks, estimate(client, spanBlocks).points());
            client.getConnection().send(new ServerboundCustomPayloadPacket(new WideScanRequestPayload(true, centerX, centerZ, spanBlocks)));
            return;
        }

        ServerLevel level = server.getLevel(clientLevel.dimension());
        if (level != null) {
            job = new ScanJob(server, level, player.getGameProfile(), spanBlocks, centerX, centerZ);
            jobStore = store;
        }
    }

    /** やさしく止める: 作業中の分は地図ストアへ渡してから終わる。 */
    static void stop() {
        if (job != null) {
            if (!job.server.isRunning()) {
                job = null;
                return;
            }

            job.cancel();
        } else if (remote != null && !remote.stopRequested) {
            remote.stopRequested = true;
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundCustomPayloadPacket(new WideScanRequestPayload(false, 0, 0, 0)));
            }
        }
    }

    /** すぐ捨てる: ワールドを離れる・地図ストアを閉じるとき。 */
    static void abort() {
        ScanJob current = job;
        if (current != null) {
            job = null;
            current.cancel();
        }

        if (remote != null) {
            stop(); // サーバーにも止めてもらう (接続が無ければ、サーバーが抜けたことに気づいて止める)
            remote = null;
            received.clear();
        }
    }

    // ---- サーバーから (クライアントスレッド) ----

    static void onStatus(WideScanStatusPayload payload) {
        if (payload.kind() == WideScanStatusPayload.HELLO) {
            supportingConnection = Minecraft.getInstance().getConnection();
            return;
        }

        if (payload.kind() == WideScanStatusPayload.BUSY) {
            remote = null;
            lastResult = Result.BUSY;
            return;
        }

        Remote current = remote;
        if (current == null) {
            return;
        }

        current.done = payload.done();
        current.total = payload.total();
        Result result = switch (payload.kind()) {
            case WideScanStatusPayload.DONE -> Result.DONE;
            case WideScanStatusPayload.STOPPED -> Result.STOPPED;
            case WideScanStatusPayload.FAILED -> Result.FAILED;
            default -> null;
        };
        if (result != null) {
            drain(current.store, Long.MAX_VALUE); // 終わりの知らせより前に届いた升目を、すべて写す
            remote = null;
            finish(Minecraft.getInstance(), current.store, result, current.done, current.total, current.spanBlocks, current.startNanos);
        }
    }

    static void onCell(WideScanCellPayload payload) {
        if (remote != null) {
            received.add(payload.merge());
        }
    }

    // ---- 毎 tick (クライアントスレッド) ----

    static void tick(Minecraft client, MapStore store) {
        if (remote != null) {
            if (remote.store != store) {
                abort();
            } else {
                drain(store, System.nanoTime() + APPLY_BUDGET_NANOS);
            }

            return;
        }

        ScanJob current = job;
        if (current == null) {
            return;
        }

        ClientLevel level = client.level;
        if (jobStore != store || level == null || level.dimension() != current.dimension || !current.server.isRunning() || !available(client)) {
            if (!current.cancelled) {
                stop();
            }

            if (!current.server.isRunning() || jobStore != store) {
                job = null;
                return;
            }
        }

        long deadline = System.nanoTime() + APPLY_BUDGET_NANOS;
        ScanJob.CellMerge merge;
        while (System.nanoTime() < deadline && (merge = current.merges.poll()) != null) {
            current.pendingMerges.decrementAndGet();
            apply(store, merge);
        }

        if (current.finished && current.merges.isEmpty()) {
            job = null;
            Result result = current.failed ? Result.FAILED : current.cancelled ? Result.STOPPED : Result.DONE;
            finish(client, store, result, current.done, current.total, current.spanBlocks, current.startNanos);
            return;
        }

        // 止めた後も、後始末 (step の cleanup) が済むまで回す。cancel() が実行中の step と行き違うと、後始末の予約が落ちるため
        if (!current.finished) {
            current.schedule();
        }
    }

    private static void drain(MapStore store, long deadline) {
        ScanJob.CellMerge merge;
        while (System.nanoTime() < deadline && (merge = received.poll()) != null) {
            apply(store, merge);
        }
    }

    private static void finish(Minecraft client, MapStore store, Result result, int done, int total, int spanBlocks, long startNanos) {
        lastDone = done;
        lastTotal = total;
        lastSpan = spanBlocks;
        double elapsed = (System.nanoTime() - startNanos) / 1.0e9;
        if (done >= 8 && elapsed > 2.0) {
            measuredPointsPerSecond = done / elapsed; // 次の見積もりに使う
        }

        lastResult = result;
        if (result == Result.DONE && client.player != null) {
            client.player.sendOverlayMessage(Component.translatable("neo.map.scan.done", spanBlocks));
        }

        store.saveAsync();
    }

    /** 升目の色を、地図ストアへ写す。色が付いているピクセルだけ上書きする (写しは何度行っても同じ結果)。 */
    private static void apply(MapStore store, ScanJob.CellMerge merge) {
        MapStore.MapRef ref = store.find(merge.dimension(), merge.scale(), merge.cellX(), merge.cellZ());
        MapItemSavedData data = ref != null ? ref.data : ScanJob.freshData(merge.dimension(), merge.scale(), merge.cellX(), merge.cellZ());
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
}
