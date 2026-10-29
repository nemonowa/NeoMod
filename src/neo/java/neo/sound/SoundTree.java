package neo.sound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;

/**
 * サウンドイベントの ID を「名前空間 → パスを '.' で区切った階層」の木にする (描画に依存しない純粋なロジック)。
 * 1 つの節が「音そのもの (id)」と「子の階層」の両方を持つこともある (例: ambient.cave と ambient.cave.x)。
 */
final class SoundTree {
    static final class Node {
        final String name;
        /** 開閉状態の保存に使う一意のキー (例: "minecraft:block.note_block")。 */
        final String key;
        final TreeMap<String, Node> children = new TreeMap<>();
        Identifier id;

        Node(String name, String key) {
            this.name = name;
            this.key = key;
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    /** 表示する 1 行。count は (絞り込み後の) 配下の音の数。 */
    record Row(Node node, int depth, boolean expanded, int count) {}

    private final TreeMap<String, Node> namespaces = new TreeMap<>();
    private final int total;

    SoundTree(Collection<Identifier> ids) {
        int n = 0;
        for (Identifier id : ids) {
            Node ns = namespaces.computeIfAbsent(id.getNamespace(), s -> new Node(s, s + ":"));
            Node cur = ns;
            StringBuilder prefix = new StringBuilder();
            for (String part : id.getPath().split("\\.")) {
                if (prefix.length() > 0) {
                    prefix.append('.');
                }
                prefix.append(part);
                String key = id.getNamespace() + ":" + prefix;
                cur = cur.children.computeIfAbsent(part, s -> new Node(s, key));
            }
            cur.id = id;
            n++;
        }
        total = n;
    }

    int total() {
        return total;
    }

    /**
     * 表示する行を作る。subtitles は ID → 字幕の文字列 (字幕の無い音は含まない)。絞り込み (検索・お気に入りのみ) があるときは、一致する音を含む階層だけを出し、すべて展開する。
     */
    List<Row> rows(Set<String> expanded, String query, boolean favoritesOnly, Set<String> favorites, Map<Identifier, String> subtitles) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean filtering = !q.isEmpty() || favoritesOnly;
        // ID か、字幕 (聴覚補助の「音声の説明」。現在の言語に翻訳済み) の部分一致
        Predicate<Identifier> match = id -> (q.isEmpty() || id.toString().contains(q)
                || subtitles.getOrDefault(id, "").toLowerCase(Locale.ROOT).contains(q))
                && (!favoritesOnly || favorites.contains(id.toString()));
        List<Row> out = new ArrayList<>();
        for (Node ns : namespaces.values()) {
            add(out, ns, 0, expanded, filtering, match);
        }
        return out;
    }

    private static int count(Node node, Predicate<Identifier> match) {
        int c = node.id != null && match.test(node.id) ? 1 : 0;
        for (Node child : node.children.values()) {
            c += count(child, match);
        }
        return c;
    }

    private static void add(List<Row> out, Node node, int depth, Set<String> expanded, boolean filtering, Predicate<Identifier> match) {
        int c = count(node, match);
        if (c == 0) {
            return;
        }
        boolean open = node.hasChildren() && (filtering || expanded.contains(node.key));
        out.add(new Row(node, depth, open, c));
        if (open) {
            for (Node child : node.children.values()) {
                add(out, child, depth + 1, expanded, filtering, match);
            }
        }
    }
}
