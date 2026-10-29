package neo.menu.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ModManagerScreenTexts {
    public static final Component CONFIGURE = Component.translatable("modmenu.configure");
    public static final Component DROP_CONFIRM = Component.translatable("modmenu.dropConfirm");
    public static final Component DROP_INFO_LINE_1 = Component.translatable("modmenu.dropInfo.line1");
    public static final Component DROP_INFO_LINE_2 = Component.translatable("modmenu.dropInfo.line2");
    public static final Component DROP_SUCCESSFUL_LINE_1 = Component.translatable("modmenu.dropSuccessful.line1");
    public static final Component DROP_SUCCESSFUL_LINE_2 = Component.translatable("modmenu.dropSuccessful.line2");
    public static final Component ISSUES = Component.translatable("modmenu.issues");
    public static final Component MODS_FOLDER = Component.translatable("modmenu.modsFolder");
    public static final Component SEARCH = Component.translatable("modmenu.search");
    public static final Component TITLE = Component.translatable("modmenu.title");
    /** Mod 一覧画面の左上のタイトル (「Mods」ボタンの文言 TITLE とは別)。 */
    public static final Component SCREEN_TITLE = Component.translatable("modmenu.screenTitle");
    /** タイトルにカーソルを重ねたときの説明。 */
    public static final Component SCREEN_TITLE_TOOLTIP = Component.translatable("modmenu.screenTitle.tooltip");
    public static final Component WEBSITE = Component.translatable("modmenu.website");

    private ModManagerScreenTexts() {
    }

    public static Component modIdTooltip(String modId) {
        return Component.translatable("modmenu.modIdToolTip", modId);
    }

    public static Component configureError(String modId, Throwable e) {
        return Component.translatable("modmenu.configure.error", modId, modId)
                .append(CommonComponents.NEW_LINE)
                .append(CommonComponents.NEW_LINE)
                .append(e.toString())
                .withStyle(ChatFormatting.RED);
    }
}
