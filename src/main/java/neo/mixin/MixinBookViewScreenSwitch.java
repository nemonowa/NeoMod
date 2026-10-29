package neo.mixin;

import neo.advancements.ScreenSwitch;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 本の閲覧 (教卓の LecternScreen を含む) から進捗画面への直接切り替え。BookViewScreen は文字入力欄を持たない
 * (編集は BookEditScreen / BookSignScreen で別) ので、キーが使われなかった (false) ときに判定する。
 * keyPressed の戻り箇所は 2 つ (super が処理して true / 末尾) なので require = 2。
 */
@Mixin(BookViewScreen.class)
public abstract class MixinBookViewScreenSwitch {
    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 2)
    private void neo$openAdvancements(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && ScreenSwitch.toAdvancements((Screen) (Object) this, event)) {
            cir.setReturnValue(true);
        }
    }
}
