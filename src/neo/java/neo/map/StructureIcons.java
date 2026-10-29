package neo.map;

import org.jetbrains.annotations.Nullable;

/**
 * バニラの構造物の ID → バニラの地図の装飾 (アイコン) の対応。
 * バニラの地図に「アイコンがある」構造物だけを対象にする (探検家の地図の目的地と同じ顔ぶれ)。
 * 返す値は {@link PinIcons} のキー。
 */
final class StructureIcons {
    private StructureIcons() {}

    /** 構造物の ID (minecraft:&lt;path&gt; の path)。アイコンが無い構造物は null。 */
    static @Nullable String iconKeyOf(String structurePath) {
        return switch (structurePath) {
            case "mansion" -> "woodland_mansion";
            case "monument" -> "ocean_monument";
            case "village_plains" -> "plains_village";
            case "village_desert" -> "desert_village";
            case "village_savanna" -> "savanna_village";
            case "village_snowy" -> "snowy_village";
            case "village_taiga" -> "taiga_village";
            case "trial_chambers" -> "trial_chambers";
            case "ancient_city" -> "ancient_city";
            case "desert_pyramid" -> "desert_pyramid";
            case "jungle_pyramid" -> "jungle_temple";
            case "swamp_hut" -> "swamp_hut";
            case "ocean_ruin_warm" -> "warm_ocean_ruins";
            case "mineshaft", "mineshaft_mesa" -> "mineshaft";
            default -> structurePath.startsWith("abandoned_camp") ? "abandoned_camp" : null;
        };
    }
}
