package neo.invmove;

import com.mojang.blaze3d.platform.InputConstants;
import neo.menu.config.ModManagerConfig;
import neo.util.KeyMappingInternals;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * インベントリ移動: コンテナ画面 (インベントリ・チェストなど) を開いても、移動 (前後左右・ジャンプ・ダッシュ) を止めない。
 *
 * バニラが止める理由は 2 つ (26.3 の Gui#setScreen / KeyboardHandler#keyPress を読んで確認):
 *  1. 画面を開くとき KeyMapping.releaseAll() で全キーを「離した」状態にする
 *  2. 画面が開いている間は、キーを押しても KeyMapping.set(key, true) を呼ばない (離すほうは常に呼ぶ)
 * プレイヤー側 (KeyboardInput#tick) は画面の有無を見ず isDown() を読むだけなので、ここではキーの押下状態を保つだけ。
 *
 * 対象外: スニーク (Shift クリックで移動中にしゃがんでしまうため) / 文字入力のある画面 / 文字入力中の入力欄 /
 * ダッシュが「トグル」設定のとき (バニラの動作のまま。画面を閉じると元に戻る)。
 * 設定 inventory_move (Mod Menu) が OFF の間は何もしない。Mixin 側 (neo.mixin.MixinGuiInventoryMove /
 * MixinKeyboardHandlerInventoryMove) はここのメソッドを呼ぶだけ。
 */
public final class InventoryMove {
    private InventoryMove() {}

    /** 画面を開いた直後に呼ぶ (Gui#setScreen の末尾)。releaseAll で離された移動キーを、実際のキーの状態に戻す。 */
    public static void onScreenSet() {
        if (!ModManagerConfig.INVENTORY_MOVE.getValue()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!appliesTo(client.gui.screen())) {
            return;
        }

        for (KeyMapping mapping : movementKeys(client.options)) {
            InputConstants.Key key = ((KeyMappingInternals) mapping).neo$getKey();
            if (key.getType() == InputConstants.Type.KEYBOARD && key.getValue() != InputConstants.UNKNOWN.getValue()) {
                mapping.setDown(InputConstants.isKeyDown(key.getValue()));
            }
        }
    }

    /** 画面を開いている間にキーが押されたとき (KeyboardHandler#keyPress の先頭)。移動キーなら押した状態にする。 */
    public static void onKeyPress(int action, KeyEvent event) {
        if (action != 1 || !ModManagerConfig.INVENTORY_MOVE.getValue()) { // 1 = 押した瞬間 (長押しの繰り返しは不要)
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!appliesTo(client.gui.screen())) {
            return;
        }

        for (KeyMapping mapping : movementKeys(client.options)) {
            if (mapping.matches(event)) {
                mapping.setDown(true);
            }
        }
    }

    /** 前後左右・ジャンプ・ダッシュ。ダッシュがトグル設定のときは外す (トグルの状態管理はバニラに任せる)。 */
    private static KeyMapping[] movementKeys(Options options) {
        if (options.toggleSprint().get()) {
            return new KeyMapping[] {options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump};
        }

        return new KeyMapping[] {options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keySprint};
    }

    /** 移動を通す画面か: コンテナ画面で、文字入力中の入力欄が無いこと (クリエイティブの検索欄・レシピブックの検索欄・金床の名前欄など)。 */
    private static boolean appliesTo(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }

        GuiEventListener focused = screen.getFocused();
        while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
            focused = container.getFocused();
        }

        return !(focused instanceof EditBox box && box.canConsumeInput());
    }
}
