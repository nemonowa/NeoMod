package neo.bundle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 取得できる配布ワールドの一覧。jar の assets/neo/preset_worlds.json に固定で持つ。
 * <pre>{"worlds": [{"name": "表示名", "folder": "saves の中のフォルダ名", "url": "https://...zip", "sha256": "16進"}]}</pre>
 * sha256 の無い物・フォルダ名が不正な物は載せない。
 */
public final class PresetWorlds {
    private static final Logger LOGGER = LoggerFactory.getLogger("neo");
    private static final String FILE = "/assets/neo/preset_worlds.json";
    private static final Pattern SAFE_FOLDER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._ -]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    public record Entry(String name, String folder, String url, String sha256) {}

    private static List<Entry> entries;

    private PresetWorlds() {}

    public static synchronized List<Entry> all() {
        if (entries == null) entries = load();
        return entries;
    }

    private static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = PresetWorlds.class.getResourceAsStream(FILE)) {
            if (in == null) return List.of();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("worlds")) return List.of();
            for (JsonElement el : root.getAsJsonArray("worlds")) {
                JsonObject o = el.getAsJsonObject();
                Entry e = new Entry(text(o, "name"), text(o, "folder"), text(o, "url"), text(o, "sha256"));
                if (SAFE_FOLDER.matcher(e.folder()).matches() && SHA256.matcher(e.sha256()).matches() && e.url().startsWith("https://")) {
                    out.add(e);
                } else {
                    LOGGER.warn("[NeoMods] 配布ワールドの項目が不正なので載せない: {}", e.name());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[NeoMods] {} を読めない", FILE, e);
        }
        return List.copyOf(out);
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString().trim() : "";
    }
}
