package neo.advancements;

import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;

/**
 * 進捗画面とインベントリ画面の直接切り替え。
 *  - 進捗画面でインベントリのキー (既定 E) → インベントリを開く
 *  - インベントリ画面 (サバイバル / クリエイティブ) に限らず、チェスト・作業台・かまど・金床・取引などの
 *    コンテナ画面、本の閲覧 (教卓を含む)、ワールドマップで進捗のキー (既定 L) → 進捗画面を開く
 * バニラは、どちらの画面も「自分を開くキー」でしか閉じず、相手のキーは無視する (閉じてからもう一度押す必要がある)。
 * 新しいキーは作らず、バニラの既存のキー割り当て (keyInventory / keyAdvancements) をそのまま使う。
 * 設定 screen_switch (Mod Menu) が OFF の間は何もしない。Mixin 側 (neo.mixin.MixinAdvancementsScreenSwitch /
 * MixinAbstractContainerScreenSwitch / MixinBookViewScreenSwitch、neo.map.WorldMapScreen) はここのメソッドを呼ぶだけ。
 */
public final class ScreenSwitch {
    private ScreenSwitch() {}

    /** 進捗画面でキーが押されたとき。インベントリのキーならインベントリを開いて true。 */
    public static boolean fromAdvancements(KeyEvent event) {
        if (!ModManagerConfig.SCREEN_SWITCH.getValue()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null || !client.options.keyInventory.matches(event)) {
            return false;
        }

        // バニラ (Minecraft#handleKeyBinds) の「インベントリを開く」と同じ手順
        if (client.gameMode.isServerControlledInventory()) {
            client.player.sendOpenInventory();
        } else {
            client.getTutorial().onOpenInventory();
            client.gui.setScreen(new InventoryScreen(client.player));
        }

        return true;
    }

    /**
     * 進捗のキーを受け付ける画面でキーが押されたとき (入力欄などが先にキーを使わなかった場合だけ、呼び出し側が呼ぶ)。
     * 進捗のキーなら進捗画面を開いて true。進捗画面の「戻り先」は付けない (L / Esc でゲームに戻る。バニラでゲームから開いたときと同じ)。
     * 呼び出し側が「文字入力のない画面」だけを選ぶ (チャット・看板・本の編集・コマンドブロックなどは対象外)。
     */
    public static boolean toAdvancements(Screen screen, KeyEvent event) {
        if (!ModManagerConfig.SCREEN_SWITCH.getValue()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.options.keyAdvancements.matches(event)) {
            return false;
        }

        // メニューを持つ画面 (インベントリ・チェスト・教卓など) は、バニラの onClose と同じく
        // 「コンテナを閉じた」ことをサーバーへ伝える。伝えないと、サーバー側のコンテナが開いたままになる
        // (インベントリの 2x2 作業枠に置いたアイテムも、サーバーに残ってクライアントとずれる)。
        // closeContainer は内部で setScreen(null) を呼ぶので、画面の後始末 (removed) はここで 1 回だけ走る。
        if (screen instanceof MenuAccess<?>) {
            client.player.closeContainer();
        }

        client.gui.setScreen(new AdvancementsScreen(client.player.connection.getAdvancements()));
        return true;
    }
}
