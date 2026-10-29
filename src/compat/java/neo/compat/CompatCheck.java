package neo.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 古い環境 (古い Minecraft / Java / ローダー) でも、ゲームを止めずに「最低限起動する」(縮退起動) ための確認とログ。
 *
 * このパッケージ (neo.compat) は、Java 8 のバイトコード (--release 8) でビルドする。本体 (neo.*) は Java 25 のバイトコードで、
 * Minecraft 26.3 専用なので、古い Java では読み込むこともできない。そこで、ローダーが最初に呼ぶ入口 (FabricGate / ForgeGate) だけを
 * 古い Java でも読める形にして、環境が合うときだけ本体を呼び、合わないときは mods/NeoMods.log に理由を書いて何もしない。
 * 規則: ここは Minecraft・ローダー (Fabric / Forge) の型を import しない (古い環境にある型だけに頼らない)。必要なときはリフレクション (文字列) で呼ぶ。
 */
public final class CompatCheck {
    /** NeoMods が動く最低限の環境。本体は Java 25 のバイトコードで、Minecraft 26.3 専用。 */
    static final int MIN_JAVA = 25;
    static final int MIN_MINECRAFT_MAJOR = 26;
    static final int MIN_MINECRAFT_MINOR = 3;

    private static final String LOG_FILE_NAME = "NeoMods.log";

    private CompatCheck() {}

    /** 実行中の Java のメジャーバージョン ("1.8" → 8、"17" → 17)。読めなければ 0。 */
    static int javaMajor() {
        String version = System.getProperty("java.specification.version", "").trim();
        try {
            if (version.startsWith("1.")) {
                version = version.substring(2);
            }

            int dot = version.indexOf('.');
            if (dot >= 0) {
                version = version.substring(0, dot);
            }

            return Integer.parseInt(version);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Minecraft のバージョン文字列から [メジャー, マイナー] を取り出す ("26.3" "26.3.1" "26.3-rc1" "1.20.4")。読めなければ null。 */
    static int[] parseMinecraft(String version) {
        if (version == null) {
            return null;
        }

        String[] parts = version.trim().split("[^0-9]+");
        int[] numbers = new int[2];
        int count = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            try {
                numbers[count++] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }

            if (count == 2) {
                return numbers;
            }
        }

        return null;
    }

    static boolean minecraftSupported(int[] version) {
        return version[0] > MIN_MINECRAFT_MAJOR || (version[0] == MIN_MINECRAFT_MAJOR && version[1] >= MIN_MINECRAFT_MINOR);
    }

    /**
     * 本体を動かしてよい環境か。Java が 25 以上で、Minecraft が 26.3 以上のとき true。
     * Minecraft のバージョンが分からないとき (null) は、Java だけで判断する (これまでの「Java 25 以上」の条件は変えない)。
     */
    static boolean supported(String minecraftVersion) {
        if (javaMajor() < MIN_JAVA) {
            return false;
        }

        int[] version = parseMinecraft(minecraftVersion);
        return version == null || minecraftSupported(version);
    }

    /** mods/NeoMods.log に、無効にしたことを書く (最新の起動 1 回分を上書き)。書けなくてもゲームは止めない。 */
    static void writeDisabledLog(String loader, String minecraftVersion) {
        Path modsDir = Paths.get(System.getProperty("user.dir", "."), "mods");
        String timestamp = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String text = "[" + timestamp + "] [NeoMods] Disabled: this environment is not supported (loader: " + loader
            + ", Minecraft: " + (minecraftVersion == null ? "unknown" : minecraftVersion)
            + ", Java: " + System.getProperty("java.specification.version", "unknown") + ").\n"
            + "NeoMods needs Minecraft " + MIN_MINECRAFT_MAJOR + "." + MIN_MINECRAFT_MINOR + " or newer and Java " + MIN_JAVA
            + " or newer. The game was not stopped; NeoMods did nothing.\n";
        try {
            Files.createDirectories(modsDir);
            Files.write(modsDir.resolve(LOG_FILE_NAME), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // ログが書けなくても、ゲームは止めない
            System.err.println("[NeoMods] Disabled (could not write mods/" + LOG_FILE_NAME + "): " + e);
        }
    }

    /** 本体のクラスを名前で読み込み、public な引数なしの static メソッドを呼ぶ (本体の例外は、そのまま投げ直す)。 */
    static void invokeStatic(String className, String methodName) {
        try {
            Class.forName(className).getMethod(methodName).invoke(null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }

            if (cause instanceof Error) {
                throw (Error) cause;
            }

            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NeoMods: could not call " + className + "#" + methodName, e);
        }
    }
}
