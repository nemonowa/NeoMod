package neo.mixin;

import neo.advancements.ScreenSwitch;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 進捗画面からインベントリへの直接切り替え (進捗画面には文字入力欄が無いので、キー処理の先頭で判定する)。 */
@Mixin(AdvancementsScreen.class)
public abstract class MixinAdvancementsScreenSwitch {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void neo$openInventory(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenSwitch.fromAdvancements(event)) {
            cir.setReturnValue(true);
        }
    }
}
