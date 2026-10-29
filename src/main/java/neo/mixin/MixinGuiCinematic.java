package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.cinematic.AfkCinematic;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * AFK シネマティック: F1 (GUI の切り替え) の処理を肩代わりする。
 * GUI を隠している状態でもう一度 F1 を押したら、GUI を出す代わりにシネマティックを始める。シネマティック中に F1 を押したら、シネマティックを終えて GUI を戻す。
 * それ以外 (設定 OFF のとき・GUI が出ているときなど) は、バニラの Hud#toggle をそのまま呼ぶ。
 */
@Mixin(Gui.class)
public abstract class MixinGuiCinematic {
    @WrapOperation(
        method = "handleKeybinds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;toggle()V"),
        require = 1
    )
    private void neo$cinematicOnToggleGui(Hud hud, Operation<Void> original) {
        if (!AfkCinematic.onToggleGui(hud)) {
            original.call(hud);
        }
    }
}
