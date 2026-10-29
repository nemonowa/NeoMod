package neo.map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import neo.event.NeoClientEvents;
import neo.event.NeoKeyMappings;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Path;
import java.util.Locale;

/**
 * ワールドマップ (M キー) のクライアント側の入口。
 * 設定 world_map が OFF の間は、更新も保存もせず、tick では設定を 1 回引いて戻るだけ。
 * 地図データは、バニラのワールドの data フォルダへ、バニラの地図と同じ階層・形式 (minecraft/maps/&lt;id&gt;.dat) で蓄積し、
 * 索引など Mod 固有のデータは data/neo/ の下に置く (保存の共通規約: {@link neo.data.NeoData})。ピンは保存しない。
 */
public final class WorldMapClient {
    private static final KeyMapping OPEN_KEY = NeoKeyMappings.register(new KeyMapping("key.neo.map", Type.KEYBOARD, InputConstants.KEY_M, NeoKeyMappings.MAIN));
    private static final int SAVE_INTERVAL_TICKS = 600;
    /** 高所へ移動してから、地面の高さが分かるまで待つ最大 tick 数。 */
    private static final int TELEPORT_WAIT_TICKS = 100;

    private static final MapUpdater UPDATER = new MapUpdater();
    private static MapStore store;
    private static MapPins pins;
    private static MapMarkers markers;
    private static String worldKey;
    private static int saveTimer;
    private static PendingTeleport pendingTeleport;

    private record PendingTeleport(int x, int z, int deadlineTick) {}

    private static int clientTicks;

    private WorldMapClient() {}

    /** neo のクライアント初期化 (NeoCore) から呼ばれる。 */
    public static void init() {
        NeoClientEvents.registerEndClientTick(WorldMapClient::onClientTick);
        // ワールドの途中でゲームを閉じても、直近の探索分を失わないようにする
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                MapStore currentStore = store;
                if (currentStore != null) {
                    currentStore.saveNow();
                }

                MapMarkers currentMarkers = markers;
                if (currentMarkers != null) {
                    currentMarkers.saveIfDirty();
                }

            } catch (RuntimeException ignored) {
                // 終了時の保存はベストエフォート
            }
        }, "NeoMod-Map-Shutdown"));
    }

    static boolean isOpenKey(KeyEvent event) {
        return OPEN_KEY.matches(event);
    }

    private static void onClientTick(Minecraft client) {
        if (!ModManagerConfig.WORLD_MAP.getValue() || client.level == null || client.player == null) {
            endSession();
            while (OPEN_KEY.consumeClick()) {
                // OFF のとき・ワールド外のときは押下を捨てる
            }

            return;
        }

        clientTicks++;
        ensureSession(client);
        UPDATER.tick(client, store);
        WideMapScan.tick(client, store);
        markers.tick(client);
        tickPendingTeleport(client);
        if (++saveTimer >= SAVE_INTERVAL_TICKS) {
            saveTimer = 0;
            store.saveAsync();
            markers.saveIfDirty();
        }

        while (OPEN_KEY.consumeClick()) {
            if (client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SPECTATOR) {
                client.player.sendOverlayMessage(Component.translatable("neo.map.no_spectator")); // バニラの手はスペクテイターでは描かれない
            } else {
                client.gui.setScreen(new WorldMapScreen(store, pins, markers, client.level.dimension()));
            }
        }
    }

    private static void ensureSession(Minecraft client) {
        String key = worldKey(client);
        if (store != null && key.equals(worldKey)) {
            return;
        }

        endSession();
        worldKey = key;
        Path dataFolder = dataFolder(client, key);
        store = new MapStore(dataFolder, client.getFixerUpper(), client.level.registryAccess());
        pins = new MapPins(); // ピンは保存しない (このワールドで遊んでいる間だけ)
        markers = new MapMarkers(dataFolder); // 発見した構造物・見つけた旗 (保存する)
        UPDATER.reset();
        saveTimer = 0;
        pendingTeleport = null;
    }

    /**
     * データの置き場 (NeoData の規約): バニラのワールドの data フォルダ。
     * シングルは <ワールド>/data (バニラの地図は minecraft/maps/、この Mod 固有の索引は neo/ の下)。
     * マルチはワールドのフォルダが無いので、クライアント側に同じ構造 (gameDir/neo/<サーバー>/data) を作る。
     */
    private static Path dataFolder(Minecraft client, String key) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.DATA);
        }

        return client.gameDirectory.toPath().resolve("neo").resolve(key).resolve("data");
    }

    private static void endSession() {
        pendingTeleport = null;
        if (store == null) {
            return;
        }

        WideMapScan.abort();
        store.close();
        markers.saveIfDirty();
        store = null;
        pins = null;
        markers = null;
        worldKey = null;
        UPDATER.reset();
    }

    private static String worldKey(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            return "sp_" + sanitize(server.getWorldData().getLevelName());
        }

        ServerData data = client.getCurrentServer();
        if (data != null) {
            return "mp_" + sanitize(data.ip);
        }

        return "unknown";
    }

    static String sanitize(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    // ---- 地面の高さ / テレポート ----

    /** そのブロック列の地面の上 (最初の空き Y)。チャンクが読み込まれていなければ null。 */
    static Integer loadedGround(ClientLevel level, int worldX, int worldZ) {
        if (level == null) {
            return null;
        }

        ChunkAccess access = level.getChunk(worldX >> 4, worldZ >> 4, ChunkStatus.FULL, false);
        if (access instanceof LevelChunk chunk && !chunk.isEmpty()) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
        }

        return null;
    }

    /**
     * (x, z) へ /tp で移動する。y が分かっていれば 1 回で移動。
     * 分からない (NO_Y) ときは、地面の高さを調べられるまで 2 段階:
     * ① チャンクが読み込まれていればその高さへ / ② 未読み込みなら最高高度へ → チャンクが来たら地面へ。
     * 天井のある次元 (ネザー) では高所が使えないので、y 不明のときは断る。
     * 権限が無ければサーバーが /tp を断る (確認はしない)。
     */
    static void teleport(int x, int y, int z) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            return;
        }

        pendingTeleport = null;
        if (y != MapPins.NO_Y) {
            sendTp(player, x, y, z);
            return;
        }

        Integer ground = loadedGround(level, x, z);
        if (ground != null) {
            sendTp(player, x, ground, z);
            return;
        }

        if (level.dimensionType().hasCeiling()) {
            player.sendOverlayMessage(Component.translatable("neo.map.unknown_height"));
            return;
        }

        sendTp(player, x, level.getMaxY(), z);
        pendingTeleport = new PendingTeleport(x, z, clientTicks + TELEPORT_WAIT_TICKS);
    }

    private static void tickPendingTeleport(Minecraft client) {
        PendingTeleport pending = pendingTeleport;
        if (pending == null || client.level == null || client.player == null) {
            return;
        }

        Integer ground = loadedGround(client.level, pending.x(), pending.z());
        if (ground != null) {
            pendingTeleport = null;
            sendTp(client.player, pending.x(), ground, pending.z());
        } else if (clientTicks >= pending.deadlineTick()) {
            pendingTeleport = null;
            client.player.sendOverlayMessage(Component.translatable("neo.map.teleport_timeout"));
        }
    }

    private static void sendTp(LocalPlayer player, int x, int y, int z) {
        player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.1f %d %.1f", x + 0.5, y, z + 0.5));
    }
}
