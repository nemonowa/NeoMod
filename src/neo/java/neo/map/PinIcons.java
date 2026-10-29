package neo.map;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * ピンに使えるアイコン。バニラの地図の装飾 (マーカー・構造物の場所・旗) をそのまま使う。
 * 画像もバニラのもの (textures/map/decorations/&lt;名前&gt;.png) で、地図への描画はバニラの地図レンダラーが行う。
 */
final class PinIcons {
    record Icon(String key, Holder<MapDecorationType> type) {
        /** GUI (一覧・ボタン) に描く用の画像。8x8。 */
        Identifier texture() {
            Identifier asset = type.value().assetId();
            return Identifier.fromNamespaceAndPath(asset.getNamespace(), "textures/map/decorations/" + asset.getPath() + ".png");
        }
    }

    static final List<Icon> ALL = build();

    private PinIcons() {}

    static Icon byKey(String key) {
        for (Icon icon : ALL) {
            if (icon.key().equals(key)) {
                return icon;
            }
        }

        return ALL.get(0);
    }

    static Icon next(Icon current, int step) {
        int index = ALL.indexOf(current);
        return ALL.get(Math.floorMod(index + step, ALL.size()));
    }

    private static List<Icon> build() {
        List<Icon> icons = new ArrayList<>();
        add(icons, "red_marker", MapDecorationTypes.RED_MARKER);
        add(icons, "blue_marker", MapDecorationTypes.BLUE_MARKER);
        add(icons, "target_x", MapDecorationTypes.TARGET_X);
        add(icons, "target_point", MapDecorationTypes.TARGET_POINT);
        add(icons, "red_x", MapDecorationTypes.RED_X);
        // 場所 (構造物)
        add(icons, "plains_village", MapDecorationTypes.PLAINS_VILLAGE);
        add(icons, "desert_village", MapDecorationTypes.DESERT_VILLAGE);
        add(icons, "savanna_village", MapDecorationTypes.SAVANNA_VILLAGE);
        add(icons, "snowy_village", MapDecorationTypes.SNOWY_VILLAGE);
        add(icons, "taiga_village", MapDecorationTypes.TAIGA_VILLAGE);
        add(icons, "woodland_mansion", MapDecorationTypes.WOODLAND_MANSION);
        add(icons, "ocean_monument", MapDecorationTypes.OCEAN_MONUMENT);
        add(icons, "trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS);
        add(icons, "ancient_city", MapDecorationTypes.ANCIENT_CITY);
        add(icons, "desert_pyramid", MapDecorationTypes.DESERT_PYRAMID);
        add(icons, "jungle_temple", MapDecorationTypes.JUNGLE_TEMPLE);
        add(icons, "swamp_hut", MapDecorationTypes.SWAMP_HUT);
        add(icons, "warm_ocean_ruins", MapDecorationTypes.OCEAN_RUIN_WARM);
        add(icons, "mineshaft", MapDecorationTypes.MINESHAFT);
        add(icons, "abandoned_camp", MapDecorationTypes.ABANDONED_CAMP);
        // 旗 (16 色)
        add(icons, "white_banner", MapDecorationTypes.WHITE_BANNER);
        add(icons, "light_gray_banner", MapDecorationTypes.LIGHT_GRAY_BANNER);
        add(icons, "gray_banner", MapDecorationTypes.GRAY_BANNER);
        add(icons, "black_banner", MapDecorationTypes.BLACK_BANNER);
        add(icons, "brown_banner", MapDecorationTypes.BROWN_BANNER);
        add(icons, "red_banner", MapDecorationTypes.RED_BANNER);
        add(icons, "orange_banner", MapDecorationTypes.ORANGE_BANNER);
        add(icons, "yellow_banner", MapDecorationTypes.YELLOW_BANNER);
        add(icons, "lime_banner", MapDecorationTypes.LIME_BANNER);
        add(icons, "green_banner", MapDecorationTypes.GREEN_BANNER);
        add(icons, "cyan_banner", MapDecorationTypes.CYAN_BANNER);
        add(icons, "light_blue_banner", MapDecorationTypes.LIGHT_BLUE_BANNER);
        add(icons, "blue_banner", MapDecorationTypes.BLUE_BANNER);
        add(icons, "purple_banner", MapDecorationTypes.PURPLE_BANNER);
        add(icons, "magenta_banner", MapDecorationTypes.MAGENTA_BANNER);
        add(icons, "pink_banner", MapDecorationTypes.PINK_BANNER);
        return List.copyOf(icons);
    }

    private static void add(List<Icon> icons, String key, Holder<MapDecorationType> type) {
        icons.add(new Icon(key, type));
    }
}
