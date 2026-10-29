package neo.menu.service;

import neo.menu.config.ModManagerConfig;
import neo.menu.util.ModManagerScreenTexts;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** タイトル画面の「Mods」ボタンに表示するテキスト (設定により Mod 数を併記する。通常の表示は「7 Mods Loaded!!」)。ゲーム内メニューはアイコン固定。 */
public final class ModsButtonText {
    private ModsButtonText() {}

    public static Component create() {
        var titleStyle = ModManagerConfig.MODS_BUTTON_STYLE.getValue();
        var isIcon = titleStyle == ModManagerConfig.TitleMenuButtonStyle.ICON;
        var isShort = titleStyle == ModManagerConfig.TitleMenuButtonStyle.SHRINK;
        MutableComponent modsText = ModManagerScreenTexts.TITLE.copy();
        if (!isIcon) { // Mod 数は Mods ボタンにも出す (固定)
            String count = ModCount.getDisplayed();
            if (isShort) {
                modsText.append(Component.literal(" ")).append(Component.translatable("modmenu.loaded.short", count));
            } else {
                String specificKey = "modmenu.loaded." + count;
                String key = Language.getInstance().has(specificKey) ? specificKey : "modmenu.loaded";
                if (Language.getInstance().has(specificKey + ".secret")) {
                    key = specificKey + ".secret";
                }
                // 通常の表示は「7 Mods Loaded!!」(「Mods」の語も文言に含む。日本語・英語とも同じ形)
                return Component.translatable(key, count);
            }
        }
        return modsText;
    }
}
