package neo.fabric;

import neo.sound.SoundBrowser;
import neo.cinematic.AfkCinematic;
import neo.freelook.FreeLookState;
import neo.map.WorldMapClient;
import neo.spawnerinfo.SpawnerInfoClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Fabric エントリから呼ばれる初期化処理。 */
public final class NeoCore {
    private static final String LOG_FILE_NAME = "NeoMods.log";

    private NeoCore() {}

    /**
     * クライアント側の内蔵機能をここでまとめて初期化する。
     * - Mod Menu        : メニュー (neo.menu)
     * - Spawner Info    : スポナー情報 (neo.spawnerinfo)
     * - EnchantToolTip  : ツールチップ (neo.tooltip)。初期化不要 (neo.mixin.MixinItemStack から呼ばれる)
     */
    public static void initClient() {
        ModManagerFabric.init();
        SpawnerInfoClient.init();
        WorldMapClient.init();
        FreeLookState.init();
        SoundBrowser.init();
        AfkCinematic.init();
    }

    public static void init(String loaderName) {
        writeInitLog(loaderName);
    }

    /** mods/NeoMods.log に「最新の起動1回分」だけを上書きで書く (追記しないので肥大化しない)。 */
    private static void writeInitLog(String loaderName) {
        Path modsDir = Path.of(System.getProperty("user.dir"), "mods");
        String timestamp = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        try {
            Files.createDirectories(modsDir);
            // オプション無指定の writeString は CREATE + TRUNCATE_EXISTING + WRITE (= 上書き)
            Files.writeString(
                modsDir.resolve(LOG_FILE_NAME),
                "[" + timestamp + "] [NeoMods] Initialized successfully from " + loaderName + "!\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
