package neo.mixin;

import neo.advancements.ScreenSwitch;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * コンテナ画面 (インベントリ・チェスト・作業台・かまど・金床・取引など、AbstractContainerScreen の全サブクラス) から
 * 進捗画面への直接切り替え。末尾 (RETURN) で、キーが使われなかった (false) ときだけ判定するので、
 * 入力欄 (レシピブック・クリエイティブの検索欄・金床の名前欄) が先にキーを使ったときは切り替わらない。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreenSwitch {
    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 1)
    private void neo$openAdvancements(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && ScreenSwitch.toAdvancements((Screen) (Object) this, event)) {
            cir.setReturnValue(true);
        }
    }
}
