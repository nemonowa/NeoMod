package neo.map;

import java.util.UUID;
import neo.util.PermissionGate;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * マルチの広域スキャン (サーバー側)。権限 (ゲームマスター以上) のある参加者の頼みで {@link ScanJob} を動かし、
 * できた升目をその人へ送る。サーバー全体で同時に 1 つだけ (ほかの人には BUSY を返す)。
 * シングル・LAN のホスト本人はクライアントが内蔵サーバーで直接動かすので、ここは通らない。
 * サーバー側からも呼ばれるので、クライアント専用のクラスを参照しない (P-63)。
 */
public final class WideMapScanServer {
    /** 1 tick に送る升目の数 (1 枚 16KB。4 枚で毎秒 1.3MB ほど)。 */
    private static final int CELLS_PER_TICK = 4;
    private static final int PROGRESS_INTERVAL_TICKS = 20;
    /** 受け付ける範囲の一辺の上限 (ブロック)。マップ画面で選べる最大より大きい。 */
    private static final int MAX_SPAN = 16384;

    // サーバースレッドだけ
    private static ScanJob job;
    private static UUID ownerId;
    private static int ticks;

    private WideMapScanServer() {}

    /** 参加した人へ、このサーバーで使えることを知らせる (使えるかの権限はクライアントが自分で見る)。 */
    public static void sendHello(ServerPlayer player) {
        send(player, new WideScanStatusPayload(WideScanStatusPayload.HELLO, 0, 0, 0));
    }

    /** ネットワークのスレッドから呼ばれる。処理はサーバースレッドで行う。 */
    public static void onRequest(ServerPlayer player, WideScanRequestPayload request) {
        MinecraftServer server = player.level().getServer();
        server.execute(() -> handle(server, player, request));
    }

    private static void handle(MinecraftServer server, ServerPlayer player, WideScanRequestPayload request) {
        if (!PermissionGate.allowed(player)) {
            return;
        }

        if (!request.start()) {
            if (job != null && player.getUUID().equals(ownerId)) {
                job.cancel();
            }

            return;
        }

        if (job != null) {
            send(player, new WideScanStatusPayload(WideScanStatusPayload.BUSY, 0, 0, 0));
            return;
        }

        int span = Math.max(16, Math.min(MAX_SPAN, request.spanBlocks()));
        job = new ScanJob(server, player.level(), player.getGameProfile(), span, request.centerX(), request.centerZ());
        ownerId = player.getUUID();
        ticks = 0;
    }

    public static void onServerTick(MinecraftServer server) {
        ScanJob current = job;
        if (current == null) {
            return;
        }

        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.level() != current.level || !PermissionGate.allowed(owner)) {
            // 抜けた・次元を移った・権限が無くなった: 送り先が無いので、作業中の分も捨てる
            if (!current.cancelled) {
                current.cancel();
            }

            current.merges.clear();
            current.pendingMerges.set(0);
            if (current.finished) {
                job = null;
            }

            return;
        }

        ScanJob.CellMerge merge;
        for (int sent = 0; sent < CELLS_PER_TICK && (merge = current.merges.poll()) != null; sent++) {
            current.pendingMerges.decrementAndGet();
            send(owner, new WideScanCellPayload(merge));
        }

        if (current.finished && current.merges.isEmpty()) {
            byte kind = current.failed ? WideScanStatusPayload.FAILED : current.cancelled ? WideScanStatusPayload.STOPPED : WideScanStatusPayload.DONE;
            send(owner, new WideScanStatusPayload(kind, current.done, current.total, current.spanBlocks));
            job = null;
            return;
        }

        if (++ticks % PROGRESS_INTERVAL_TICKS == 0) {
            send(owner, new WideScanStatusPayload(WideScanStatusPayload.PROGRESS, current.done, current.total, current.spanBlocks));
        }

        if (!current.cancelled && !current.finished) {
            current.schedule();
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
}
