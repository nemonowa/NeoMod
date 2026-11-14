package neo.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.function.LongConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 配布ワールドの zip を取得し、saves/&lt;フォルダ名&gt; へ入れる。Java 標準だけで書き、Minecraft のクラスを使わない。
 * 既にあるフォルダは上書きしない。https だけ (試験用に localhost の http は可)。SHA-256 を照合し、zip の外へ書く項目を拒む。
 */
public final class WorldInstaller {
    private static final long MAX_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 8L * 1024 * 1024 * 1024;

    private WorldInstaller() {}

    /**
     * 取得して入れる。呼んだスレッドで最後まで行う (画面からは別のスレッドで呼ぶ)。
     * @param progress 取得したバイト数を知らせる
     * @param total 分かれば全体のバイト数を知らせる (分からなければ呼ばない)
     */
    public static void install(PresetWorlds.Entry entry, Path saves, LongConsumer total, LongConsumer progress)
            throws IOException, InterruptedException {
        URI uri = URI.create(entry.url());
        String host = uri.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !("http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
            throw new IOException("https ではない URL: " + entry.url());
        }
        Path target = saves.resolve(entry.folder()).normalize();
        if (!target.startsWith(saves.normalize()) || !target.getParent().equals(saves.normalize())) {
            throw new IOException("フォルダ名が不正: " + entry.folder());
        }
        if (Files.exists(target)) throw new IOException("既にある: " + target.getFileName());

        Files.createDirectories(saves);
        Path tmp = Files.createTempDirectory(saves, ".neo-download-");
        try {
            Path zip = tmp.resolve("world.zip");
            download(uri, zip, entry.sha256(), total, progress);
            Path extract = tmp.resolve("extract");
            Files.createDirectories(extract);
            unzip(zip, extract);
            Path root = findWorldRoot(extract);
            if (Files.exists(target)) throw new IOException("既にある: " + target.getFileName());
            Files.move(root, target);
        } finally {
            deleteRecursively(tmp);
        }
    }

    private static void download(URI uri, Path dest, String sha256, LongConsumer total, LongConsumer progress)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // GitHub の Releases は別のホストへ転送する
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(30))
            .header("User-Agent", "NeoMods")
            .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > MAX_DOWNLOAD_BYTES) throw new IOException("大きすぎる: " + declared);
            if (declared > 0) total.accept(declared);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            try (DigestInputStream in = new DigestInputStream(body, digest); OutputStream out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    count += n;
                    if (count > MAX_DOWNLOAD_BYTES) throw new IOException("大きすぎる");
                    out.write(buf, 0, n);
                    progress.accept(count);
                }
            }
            if (sha256 != null && !sha256.isBlank()) {
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(sha256.trim())) throw new IOException("SHA-256 が一致しない");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static void unzip(Path zipFile, Path destDir) throws IOException {
        Path root = destDir.toAbsolutePath().normalize();
        long count = 0;
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName().replace('\\', '/');
                if (name.endsWith("session.lock")) continue;
                Path out = root.resolve(name).normalize();
                if (!out.startsWith(root)) throw new IOException("zip の外を指す項目: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(e); OutputStream os = Files.newOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        count += n;
                        if (count > MAX_EXTRACTED_BYTES) throw new IOException("展開後が大きすぎる");
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    /** level.dat が zip の根にあるか、根の下のフォルダ1つの中にある物を受け付ける。 */
    static Path findWorldRoot(Path dir) throws IOException {
        if (Files.isRegularFile(dir.resolve("level.dat"))) return dir;
        try (Stream<Path> children = Files.list(dir)) {
            var found = children.filter(d -> Files.isRegularFile(d.resolve("level.dat"))).toList();
            if (found.size() == 1) return found.get(0);
        }
        throw new IOException("level.dat が無い (または複数ある)");
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || Files.notExists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
