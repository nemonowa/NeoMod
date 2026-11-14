package neo.entitytex;

import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OptiFine のランダムエンティティ (名前の条件とランダム) と同じ書き方で、生き物の本体の絵を差し替える。
 * <pre>
 * 元の絵        textures/entity/creeper/creeper.png         (1 番)
 * 差し替える絵  optifine/random/entity/creeper/creeper2.png   (2 番、3 番…。名前が数字で終わる絵は creeper.2.png のように点で区切る)
 * ルール        optifine/random/entity/creeper/creeper.properties
 *   textures.1=2        name.1=Pinky          (上から順に見て、最初に合った物を使う。どれにも合わなければ元の絵)
 *   textures.2=3-5      weights.2=1 1 2       (複数あれば、個体の ID から決まった1つを選ぶ)
 * </pre>
 * .properties が無ければ、置いてある番号の絵 (1 番を含む) から、個体の ID で1つ選ぶ。
 * 名前以外の条件 (biomes・heights・baby・health・moonPhase・dayTime・weather・sizes・professions・colors・blocks) は、その個体を初めて見た時の状態 ({@link EntityConditions}) で比べる。
 * nbt を持つルールは対応していないので使わない。
 */
public final class RandomEntityTextures {
    private static final Logger LOGGER = LoggerFactory.getLogger("neo");
    private static final int MAX_TEXTURES = 64;
    /** 対応していない条件。これを持つルールは使わない。 */
    private static final String[] UNSUPPORTED = {"nbt"};

    /** 元の絵ごとの設定。リソースの再読み込みで捨てる。 */
    private static final Map<Identifier, Optional<Config>> CACHE = new ConcurrentHashMap<>();
    /** 個体ごとに選んだ絵。見なくなった個体の分は時間で捨てる。 */
    private static final com.google.common.cache.Cache<ChoiceKey, Choice> CHOICES = com.google.common.cache.CacheBuilder.newBuilder()
        .expireAfterAccess(java.time.Duration.ofMinutes(2)).maximumSize(4096).build();

    private RandomEntityTextures() {}

    /** リソースの再読み込みの始まり (neo.mixin.MixinReloadableResourceManager)。 */
    public static void clear() {
        CACHE.clear();
        CHOICES.invalidateAll();
        EntityConditions.clear();
    }

    /** 本体の絵を選ぶ (neo.mixin.MixinLivingEntityRenderer)。機能が OFF・差し替えが無い時は元の絵をそのまま返す。 */
    public static Identifier choose(Identifier base, Object renderState) {
        if (!ModManagerConfig.RANDOM_ENTITY_TEXTURES.getValue()) {
            if (!CACHE.isEmpty()) clear(); // OFF にしたら溜めた物を捨てる (P-42)
            return base;
        }
        if (!(renderState instanceof NamedRenderState state)) return base;
        UUID id = state.neo$entityId();
        if (id == null) return base;
        Optional<Config> config = CACHE.computeIfAbsent(base, RandomEntityTextures::load);
        if (config.isEmpty()) return base;
        // 名前以外の条件は初めて見た時の写しで固定なので、名前が同じ間は前の結果をそのまま使う (毎フレームのルールの照合を省く)
        String name = state.neo$customName();
        ChoiceKey key = new ChoiceKey(base, id);
        Choice previous = CHOICES.getIfPresent(key);
        if (previous != null && java.util.Objects.equals(previous.name(), name)) return previous.texture();
        Identifier chosen = config.get().choose(name, EntityConditions.get(id), id);
        Identifier texture = chosen != null ? chosen : base;
        CHOICES.put(key, new Choice(name, texture));
        return texture;
    }

    private record ChoiceKey(Identifier base, UUID id) {}

    private record Choice(String name, Identifier texture) {}

    private static Optional<Config> load(Identifier base) {
        String path = base.getPath();
        if (!path.startsWith("textures/") || !path.endsWith(".png")) return Optional.empty();
        String stem = "optifine/random/" + path.substring("textures/".length(), path.length() - ".png".length());
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        String separator = Character.isDigit(stem.charAt(stem.length() - 1)) ? "." : "";

        // 置いてある番号の絵 (2 番から、途切れるまで)
        List<Identifier> textures = new ArrayList<>();
        textures.add(base);
        for (int n = 2; n <= MAX_TEXTURES; n++) {
            Identifier alt = base.withPath(stem + separator + n + ".png");
            if (resources.getResource(alt).isEmpty()) break;
            textures.add(alt);
        }
        Optional<Resource> props = resources.getResource(base.withPath(stem + ".properties"));
        if (props.isEmpty()) {
            return textures.size() > 1 ? Optional.of(new Config(textures, List.of())) : Optional.empty();
        }
        try (InputStream in = props.get().open(); Reader reader = new InputStreamReader(in, StandardCharsets.ISO_8859_1)) {
            Properties p = new Properties();
            p.load(reader);
            List<Rule> rules = Rule.parseAll(p, textures.size(), base);
            return rules.isEmpty() ? Optional.empty() : Optional.of(new Config(textures, rules));
        } catch (Exception e) {
            LOGGER.warn("[NeoMods] {} を読めない", stem + ".properties", e);
            return Optional.empty();
        }
    }

    /** 個体の ID から決まる 0 以上 bound 未満の数 (同じ個体はいつも同じ絵になる)。 */
    static int pick(UUID id, int salt, int bound) {
        long h = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits() + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, (long) bound);
    }

    private record Config(List<Identifier> textures, List<Rule> rules) {
        Identifier choose(String name, EntityConditions conditions, UUID id) {
            if (rules.isEmpty()) return textures.get(pick(id, 0, textures.size()));
            for (Rule rule : rules) {
                if (!rule.matches(name, conditions)) continue;
                int index = rule.pickIndex(id);
                return index >= 1 && index <= textures.size() ? textures.get(index - 1) : null;
            }
            return null;
        }
    }

    private record Rule(int number, int[] indices, int[] weights, NameMatcher name, List<Predicate<EntityConditions>> conditions) {
        boolean matches(String customName, EntityConditions state) {
            if (name != null && !name.matches(customName)) return false;
            if (conditions.isEmpty()) return true;
            if (state == null) return false;
            for (Predicate<EntityConditions> c : conditions) {
                if (!c.test(state)) return false;
            }
            return true;
        }

        int pickIndex(UUID id) {
            if (indices.length == 1) return indices[0];
            if (weights == null) return indices[pick(id, number, indices.length)];
            int total = 0;
            for (int w : weights) total += w;
            int r = pick(id, number, total);
            for (int i = 0; i < indices.length; i++) {
                r -= weights[i];
                if (r < 0) return indices[i];
            }
            return indices[indices.length - 1];
        }

        static List<Rule> parseAll(Properties p, int available, Identifier base) {
            List<Rule> rules = new ArrayList<>();
            for (int n = 1; n <= 256; n++) {
                String textures = first(p, "textures." + n, "skins." + n);
                if (textures == null) continue;
                if (hasUnsupported(p, n)) {
                    LOGGER.info("[NeoMods] {} のルール {} は対応していない条件 (nbt) を持つので使わない", base, n);
                    continue;
                }
                int[] indices = parseIndices(textures);
                if (indices.length == 0) continue;
                int[] weights = null;
                String w = p.getProperty("weights." + n);
                if (w != null) {
                    int[] parsed = parseNumbers(w);
                    if (parsed.length == indices.length && java.util.Arrays.stream(parsed).sum() > 0) weights = parsed; // 合計 0 の重みは無視する (選べない)
                }
                String name = p.getProperty("name." + n);
                rules.add(new Rule(n, indices, weights, name != null ? NameMatcher.parse(name) : null, Conditions.parse(p, n)));
            }
            return rules;
        }

        private static boolean hasUnsupported(Properties p, int n) {
            for (String key : p.stringPropertyNames()) {
                for (String u : UNSUPPORTED) {
                    if (key.equals(u + "." + n) || key.startsWith(u + "." + n + ".")) return true;
                }
            }
            return false;
        }

        private static String first(Properties p, String a, String b) {
            String v = p.getProperty(a);
            return v != null ? v : p.getProperty(b);
        }

        /** 「2 3 5-7」の形。 */
        static int[] parseIndices(String s) {
            List<Integer> out = new ArrayList<>();
            for (String part : s.trim().split("[\\s,]+")) {
                if (part.isEmpty()) continue;
                try {
                    int dash = part.indexOf('-');
                    if (dash > 0) {
                        int a = Integer.parseInt(part.substring(0, dash)), b = Integer.parseInt(part.substring(dash + 1));
                        for (int i = a; i <= b && out.size() < MAX_TEXTURES; i++) out.add(i);
                    } else {
                        out.add(Integer.parseInt(part));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }

        private static int[] parseNumbers(String s) {
            List<Integer> out = new ArrayList<>();
            for (String part : s.trim().split("[\\s,]+")) {
                try {
                    out.add(Math.max(0, Integer.parseInt(part)));
                } catch (NumberFormatException ignored) {
                }
            }
            return out.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** OptiFine の名前の条件 (完全一致・pattern:・ipattern:・regex:・iregex:、先頭の ! で否定)。名前の無い個体は「空の名前」として比べる。 */
    record NameMatcher(Pattern pattern, String exact, boolean negate) {
        static NameMatcher parse(String raw) {
            String s = raw;
            boolean negate = s.startsWith("!");
            if (negate) s = s.substring(1);
            if (s.startsWith("pattern:")) return new NameMatcher(wildcard(s.substring(8), 0), null, negate);
            if (s.startsWith("ipattern:")) return new NameMatcher(wildcard(s.substring(9), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), null, negate);
            if (s.startsWith("regex:")) return new NameMatcher(Pattern.compile(s.substring(6)), null, negate);
            if (s.startsWith("iregex:")) return new NameMatcher(Pattern.compile(s.substring(7), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), null, negate);
            return new NameMatcher(null, s, negate);
        }

        boolean matches(String name) {
            String n = name != null ? name : "";
            boolean m = pattern != null ? pattern.matcher(n).matches() : exact.equals(n);
            return m != negate;
        }

        private static Pattern wildcard(String glob, int flags) {
            StringBuilder re = new StringBuilder();
            for (char c : glob.toCharArray()) {
                if (c == '*') re.append(".*");
                else if (c == '?') re.append('.');
                else re.append(Pattern.quote(String.valueOf(c)));
            }
            return Pattern.compile(re.toString(), flags);
        }
    }
}
