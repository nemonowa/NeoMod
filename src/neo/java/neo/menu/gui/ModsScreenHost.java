package neo.menu.gui;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * 一覧・説明ウィジェットが、親の Mods 画面に対して必要とする操作。
 * ウィジェット側が画面クラスそのものに依存しないようにするための窓口 (実装は ModsScreen)。
 */
public interface ModsScreenHost {
    /** 画面そのもの。クレジット画面や確認画面から「戻る」ときの戻り先に使う。 */
    Screen asScreen();

    /** 画面上でフォーカスを持っているのが child か。 */
    boolean isFocusOwner(GuiEventListener child);

    ModListEntry getSelectedEntry();

    void updateSelectedEntry(ModListEntry entry);

    String getSearchInput();

    /** 設定画面を開けるか (エラー履歴も考慮)。 */
    boolean getModHasConfigScreen(String modId);

    /** 直前に設定画面を開こうとして失敗したか。 */
    boolean hasConfigScreenError(String modId);

    void safelyOpenConfigScreen(String modId);

    /** 親 Mod の子 Mod 一覧を展開表示しているか。 */
    boolean isShowingChildren(String modId);

    void setShowingChildren(String modId, boolean showing);
}
