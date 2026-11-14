package neo.entitytex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ランダムエンティティのルールの、名前以外の条件を読む (OptiFine の random_entities.properties と同じ書き方)。 */
final class Conditions {
    /** 「5」「3-8」「(-10)-5」「(-20)」の形。負の数は括弧で囲む。 */
    private static final Pattern RANGE = Pattern.compile("\\(?(-?\\d+(?:\\.\\d+)?)\\)?(?:-\\(?(-?\\d+(?:\\.\\d+)?)\\)?)?");

    private Conditions() {}

    static List<Predicate<EntityConditions>> parse(Properties p, int n) {
        List<Predicate<EntityConditions>> out = new ArrayList<>();
        String v;
        if ((v = p.getProperty("biomes." + n)) != null) {
            Set<String> ids = ids(v);
            out.add(s -> ids.contains(s.biome()));
        }
        if ((v = p.getProperty("heights." + n)) != null) {
            List<double[]> ranges = ranges(v);
            out.add(s -> inAny(ranges, s.y()));
        } else {
            String min = p.getProperty("minHeight." + n), max = p.getProperty("maxHeight." + n);
            if (min != null || max != null) {
                int lo = parseInt(min, Integer.MIN_VALUE), hi = parseInt(max, Integer.MAX_VALUE);
                out.add(s -> s.y() >= lo && s.y() <= hi);
            }
        }
        if ((v = p.getProperty("baby." + n)) != null) {
            boolean want = Boolean.parseBoolean(v.trim());
            out.add(s -> s.baby() != null && s.baby() == want);
        }
        if ((v = p.getProperty("health." + n)) != null) {
            List<Predicate<EntityConditions>> any = new ArrayList<>();
            for (String token : tokens(v)) {
                boolean percent = token.endsWith("%");
                List<double[]> r = ranges(percent ? token.substring(0, token.length() - 1) : token);
                any.add(s -> s.baby() != null && inAny(r, percent ? (s.maxHealth() > 0 ? s.health() * 100.0 / s.maxHealth() : 0) : s.health()));
            }
            out.add(s -> anyTrue(any, s));
        }
        if ((v = p.getProperty("moonPhase." + n)) != null) {
            List<double[]> ranges = ranges(v);
            out.add(s -> inAny(ranges, s.moonPhase()));
        }
        if ((v = p.getProperty("dayTime." + n)) != null) {
            List<double[]> ranges = ranges(v);
            out.add(s -> inAny(ranges, s.dayTime()));
        }
        if ((v = p.getProperty("weather." + n)) != null) {
            Set<String> names = new HashSet<>(tokens(v.toLowerCase(Locale.ROOT)));
            out.add(s -> names.contains(s.weather()));
        }
        if ((v = p.getProperty("sizes." + n)) != null) {
            List<double[]> ranges = ranges(v);
            out.add(s -> s.size() != null && inAny(ranges, s.size()));
        }
        if ((v = p.getProperty("professions." + n)) != null) {
            List<Predicate<EntityConditions>> any = new ArrayList<>();
            for (String token : tokens(v)) any.add(profession(token));
            out.add(s -> s.profession() != null && anyTrue(any, s));
        }
        if ((v = p.getProperty("colors." + n, p.getProperty("collarColors." + n))) != null) {
            Set<String> names = new HashSet<>(tokens(v.toLowerCase(Locale.ROOT)));
            out.add(s -> s.color() != null && names.contains(s.color()));
        }
        if ((v = p.getProperty("blocks." + n)) != null) {
            Set<String> ids = new HashSet<>();
            for (String token : tokens(v)) ids.add(blockId(token));
            out.add(s -> ids.contains(s.block()));
        }
        return out;
    }

    /** 「farmer」「librarian:1,3-4」「minecraft:fisherman:2」。レベルは 1 から。 */
    private static Predicate<EntityConditions> profession(String token) {
        String name = token, levels = null;
        int last = token.lastIndexOf(':');
        if (last > 0 && Character.isDigit(token.charAt(last + 1 < token.length() ? last + 1 : last))) {
            name = token.substring(0, last);
            levels = token.substring(last + 1);
        }
        String id = withNamespace(name);
        List<double[]> ranges = levels != null ? ranges(levels) : null;
        return s -> id.equals(s.profession()) && (ranges == null || inAny(ranges, s.professionLevel()));
    }

    /** ブロックの指定から ID だけを取る (状態の「:axis=y」などは見ない)。 */
    private static String blockId(String token) {
        String t = token;
        int eq = t.indexOf('=');
        if (eq >= 0) t = t.substring(0, t.lastIndexOf(':', eq));
        return withNamespace(t);
    }

    private static Set<String> ids(String v) {
        Set<String> out = new HashSet<>();
        for (String token : tokens(v)) out.add(withNamespace(token));
        return out;
    }

    static String withNamespace(String id) {
        String t = id.toLowerCase(Locale.ROOT);
        return t.indexOf(':') >= 0 ? t : "minecraft:" + t;
    }

    static List<String> tokens(String v) {
        List<String> out = new ArrayList<>();
        for (String t : v.trim().split("\\s+")) {
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static List<double[]> ranges(String v) {
        List<double[]> out = new ArrayList<>();
        for (String token : v.trim().split("[\\s,]+")) {
            Matcher m = RANGE.matcher(token);
            if (!m.matches()) continue;
            double a = Double.parseDouble(m.group(1));
            double b = m.group(2) != null ? Double.parseDouble(m.group(2)) : a;
            out.add(new double[] {Math.min(a, b), Math.max(a, b)});
        }
        return out;
    }

    private static boolean anyTrue(List<Predicate<EntityConditions>> list, EntityConditions s) {
        for (Predicate<EntityConditions> c : list) {
            if (c.test(s)) return true;
        }
        return false;
    }

    static boolean inAny(List<double[]> ranges, double value) {
        for (double[] r : ranges) {
            if (value >= r[0] && value <= r[1]) return true;
        }
        return false;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s != null ? Integer.parseInt(s.trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
