package neo.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * プレイヤーが近づいた構造物を見つける (サーバー側)。構造物の配置はサーバーにしか無いので、
 * シングルはクライアントが内蔵サーバーで、マルチはサーバーが参加者ごとに走査して {@link StructureMarkersPayload} で送る。
 * サーバー側からも呼ばれるので、クライアント専用のクラスを参照しない (P-63)。
 */
public final class StructureDiscovery {
    /** 構造物の範囲 (水平) にこの距離まで近づいたら発見済み (ブロック)。 */
    static final int DISCOVER_DISTANCE = 96;
    private static final int DISCOVER_CHUNK_RADIUS = DISCOVER_DISTANCE / 16 + 2;
    static final int SCAN_INTERVAL_TICKS = 40;
    private static int tickCounter;

    private StructureDiscovery() {}

    /** サーバーのスレッドで実行: プレイヤーの周りのロード済みチャンクにある構造物のうち、範囲に近づいたものを集める。 */
    static List<StructureMarkersPayload.Entry> scan(ServerLevel level, double playerX, double playerZ) {
        List<StructureMarkersPayload.Entry> out = new ArrayList<>();
        int playerChunkX = (int) Math.floor(playerX) >> 4;
        int playerChunkZ = (int) Math.floor(playerZ) >> 4;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (int dx = -DISCOVER_CHUNK_RADIUS; dx <= DISCOVER_CHUNK_RADIUS; dx++) {
            for (int dz = -DISCOVER_CHUNK_RADIUS; dz <= DISCOVER_CHUNK_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(playerChunkX + dx, playerChunkZ + dz);
                if (chunk == null) {
                    continue;
                }

                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    Identifier id = registry.getKey(entry.getKey());
                    String iconKey = id == null ? null : StructureIcons.iconKeyOf(id.getPath());
                    if (iconKey == null || !start.isValid() || distanceTo(start.getBoundingBox(), playerX, playerZ) > DISCOVER_DISTANCE) {
                        continue;
                    }

                    ChunkPos chunkPos = start.getChunkPos();
                    out.add(new StructureMarkersPayload.Entry(iconKey, chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ()));
                }
            }
        }
        return out;
    }

    /** 構造物の範囲 (水平) までの距離。範囲の中なら 0。 */
    private static double distanceTo(BoundingBox box, double x, double z) {
        double dx = Math.max(Math.max(box.minX() - x, x - box.maxX()), 0.0);
        double dz = Math.max(Math.max(box.minZ() - z, z - box.maxZ()), 0.0);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 参加者へ送る。シングル・LAN のホスト本人は自分の内蔵サーバーを直接走査するので送らない。MOD の無いクライアントは読み捨てる。 */
    public static void onServerTick(MinecraftServer server) {
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }

        tickCounter = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId())) {
                continue;
            }

            ServerLevel level = player.level();
            List<StructureMarkersPayload.Entry> found = scan(level, player.getX(), player.getZ());
            if (!found.isEmpty()) {
                String dimension = level.dimension().identifier().toString();
                player.connection.send(new ClientboundCustomPayloadPacket(new StructureMarkersPayload(dimension, found)));
            }
        }
    }
}
