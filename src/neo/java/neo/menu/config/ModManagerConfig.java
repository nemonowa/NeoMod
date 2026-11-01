package neo.menu.config;

import neo.menu.mod.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

public class ModManagerConfig {
    public static final EnumConfigOption<TitleMenuButtonStyle> MODS_BUTTON_STYLE = new EnumConfigOption<>("mods_button_style", TitleMenuButtonStyle.REPLACE_REALMS);

    /**
     * 前提 Mod (ライブラリ) の表示: 設定のある前提 Mod だけ表示する。固定値 (設定項目ではない)。
     * このほか、Mod 数に前提 Mod と子 Mod も数える (オン固定)・クイック設定ボタン (オン固定)・Java アイコンの背景色 (赤固定)・イースターエッグ・非表示 Mod を数える・Mod リンク/ライセンス/クレジット/タグを表示・Mod 数をタイトル画面と Mods ボタンの両方に出す、ゲーム内メニューの Mods ボタンはアイコン固定、一覧の大きさは標準固定 (GUI サイズに従う)、も固定。
     */
    public static final LibraryVisibility LIBRARY_VISIBILITY = LibraryVisibility.WITH_CONFIG;

    // ---- NeoMod の機能 (この Mod が内蔵する機能の ON/OFF) ----
    /** エンチャント効果の数値をツールチップに追加する (neo.tooltip)。 */
    public static final BooleanConfigOption ENCHANT_TOOLTIP = new BooleanConfigOption("enchant_tooltip", false);
    /** スポナー情報の機能全体 (neo.spawnerinfo)。以下 3 つ (すべて既定 OFF) はこれが ON のときだけ効く。 */
    public static final BooleanConfigOption SPAWNER_INFO = new BooleanConfigOption("spawner_info", false);
    public static final BooleanConfigOption SPAWNER_GLOW = new BooleanConfigOption("spawner_glow", false);
    public static final BooleanConfigOption SPAWNER_RANGE = new BooleanConfigOption("spawner_range", false);
    public static final BooleanConfigOption SPAWNER_OVERLAY = new BooleanConfigOption("spawner_overlay", false);
    /** 進捗画面を画面の約85%まで広げる (neo.advancements)。 */
    public static final BooleanConfigOption LARGE_ADVANCEMENTS = new BooleanConfigOption("large_advancements", true);
    /** 進捗画面と、インベントリ・コンテナ画面・本・ワールドマップを、閉じずに直接切り替える (neo.advancements.ScreenSwitch)。 */
    public static final BooleanConfigOption SCREEN_SWITCH = new BooleanConfigOption("screen_switch", true);
    /** F5 を押している間だけ自由視点にする (カメラだけ回し、プレイヤーの向きは変えない)。動作は Perspective Mod と同じ (neo.freelook)。 */
    public static final BooleanConfigOption FREE_LOOK = new BooleanConfigOption("free_look", true);
    /** ズーム (素手で右クリックを押している間)。動作は WI Zoom と同じ (neo.zoom)。 */
    public static final BooleanConfigOption ZOOM = new BooleanConfigOption("zoom", true);
    /** インベントリ (コンテナ画面) を開いても移動を止めない (neo.invmove)。 */
    public static final BooleanConfigOption INVENTORY_MOVE = new BooleanConfigOption("inventory_move", true);
    /** ワールドマップ (M キー)。周辺を地図化して蓄積し、ピンとテレポートができる (neo.map)。 */
    public static final BooleanConfigOption WORLD_MAP = new BooleanConfigOption("world_map", true);
    /** ワールドマップに、発見した構造物 (バニラのアイコンがあるもの。シングルのみ) と見つけた旗 (バナー) を自動で出す。 */
    public static final BooleanConfigOption MAP_AUTO_MARKERS = new BooleanConfigOption("map_auto_markers", true);
    /** 3D スキンレイヤー: 帽子・上着・袖・ズボンのスキンを立体にする (neo.skinlayers)。動作は 3D Skin Layers を参考にした独自実装。 */
    public static final BooleanConfigOption SKIN_LAYERS = new BooleanConfigOption("skin_layers", true);
    /** 本・チャットのクリックコマンドで出る「実行確認」を省き、そのまま実行する (neo.mixin.MixinUnattendedCommand)。 */
    public static final BooleanConfigOption SKIP_COMMAND_CONFIRMATION = new BooleanConfigOption("skip_command_confirmation", true);
    /** サウンドブラウザ: クリエイティブインベントリの右側面のタブから、読み込み済みの音を一覧・試聴・/playsound をコピー (neo.sound)。 */
    public static final BooleanConfigOption SOUND_BROWSER = new BooleanConfigOption("sound_browser", true);

    /** AFK シネマティック: 放置時間 (既定 3 分) の間操作しないと、カメラが映画のようにプレイヤーの周りを動く。何か操作すると止まる (neo.cinematic)。動作は Ji AFK Cinematic を参考にした独自実装 (音楽・キー割り当てなし)。 */
    public static final BooleanConfigOption AFK_CINEMATIC = new BooleanConfigOption("afk_cinematic", true);
    /** AFK シネマティックが始まるまでの放置時間。**10 秒単位**のスライダー (1〜60 = 10 秒〜10 分。既定 18 = 3 分)。options.txt の neo_afk_cinematic_idle 行の値も、この単位の整数。 */
    public static final IntConfigOption AFK_CINEMATIC_IDLE = new IntConfigOption("afk_cinematic_idle", 18, 1, 60, ModManagerConfig::afkIdleText);

    @FileOnlyConfig
    public static final BooleanConfigOption MODIFY_TITLE_SCREEN = new BooleanConfigOption("modify_title_screen", true);
    @FileOnlyConfig
    public static final BooleanConfigOption MODIFY_GAME_MENU = new BooleanConfigOption("modify_game_menu", true);
    @FileOnlyConfig
    public static final BooleanConfigOption HIDE_CONFIG_BUTTONS = new BooleanConfigOption("hide_config_buttons", false);
    @FileOnlyConfig
    public static final BooleanConfigOption CONFIG_MODE = new BooleanConfigOption("config_mode", false);
    @FileOnlyConfig
    public static final BooleanConfigOption DISABLE_DRAG_AND_DROP = new BooleanConfigOption("disable_drag_and_drop", false);
    @FileOnlyConfig
    public static final StringSetConfigOption HIDDEN_MODS = new StringSetConfigOption("hidden_mods", new HashSet<>());
    @FileOnlyConfig
    public static final StringSetConfigOption HIDDEN_CONFIGS = new StringSetConfigOption("hidden_configs", new HashSet<>());
    /** ワールドマップの全画面表示の状態 (地図を画面いっぱいに拡大し、左右の欄を隠す)。設定画面には出さず、options.txt の neo_map_fullscreen 行に覚える。 */
    @FileOnlyConfig
    public static final BooleanConfigOption MAP_FULLSCREEN = new BooleanConfigOption("map_fullscreen", false);
    /** サウンドブラウザのお気に入り (サウンドイベントの ID)。options.txt の neo_sound_favorites 行。 */
    @FileOnlyConfig
    public static final StringSetConfigOption SOUND_FAVORITES = new StringSetConfigOption("sound_favorites", new HashSet<>());

    public static OptionInstance<?>[] asOptions() {
        ArrayList<OptionInstance<?>> options = new ArrayList<>();
        for (Field field : ModManagerConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) &&
                    OptionConvertible.class.isAssignableFrom(field.getType()) &&
                    !field.isAnnotationPresent(FileOnlyConfig.class)) {
                try {
                    options.add(((OptionConvertible) field.get(null)).asOption());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        return options.toArray(OptionInstance[]::new);
    }

    /** AFK シネマティックの放置時間 (10 秒単位の値) を、「30秒」「3分」「3分30秒」のように見せる。 */
    private static Component afkIdleText(int tenSecondSteps) {
        int seconds = tenSecondSteps * 10;
        String base = "option.modmenu.afk_cinematic_idle.";
        if (seconds < 60) {
            return Component.translatable(base + "seconds", seconds);
        }

        if (seconds % 60 == 0) {
            return Component.translatable(base + "minutes", seconds / 60);
        }

        return Component.translatable(base + "minutes_seconds", seconds / 60, seconds % 60);
    }

    /** 設定画面に出る項目 (ファイル専用でないもの) をすべて既定値に戻す。 */
    public static void resetToDefaults() {
        for (Field field : ModManagerConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()) &&
                    OptionConvertible.class.isAssignableFrom(field.getType()) &&
                    !field.isAnnotationPresent(FileOnlyConfig.class)) {
                try {
                    ((OptionConvertible) field.get(null)).resetToDefault();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** 非表示 Mod (hidden_mods 設定) か。 */
    public static boolean isHidden(Mod mod) {
        return HIDDEN_MODS.getValue().contains(mod.getId());
    }

    public enum LibraryVisibility {
        FALSE,
        WITH_CONFIG,
        TRUE;

        public boolean hideMod(Mod mod, boolean hasConfig) {
            return switch (this) {
                case TRUE -> false;
                case FALSE -> mod.getBadges().contains(Mod.Badge.LIBRARY);
                case WITH_CONFIG -> mod.getBadges().contains(Mod.Badge.LIBRARY) && !hasConfig;
            };
        }
    }

    public enum TitleMenuButtonStyle {
        CLASSIC,
        REPLACE_REALMS,
        SHRINK,
        ICON
    }

}
