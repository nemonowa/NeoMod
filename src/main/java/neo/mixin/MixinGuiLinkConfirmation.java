package neo.mixin;

import com.mojang.blaze3d.Blaze3D;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * リンク (URL) を開くときの確認画面 (「リンクを開きますか? / 開く・クリップボードにコピー・いいえ」) を出さず、そのままブラウザで開く
 * (設定 skip_command_confirmation が ON のとき。クリックコマンドの確認の省略 (MixinUnattendedCommand) と同じ設定に統合している)。
 *
 * 確認画面は、チャットのリンクのクリック・バニラの各画面のリンクのボタン・この Mod の Mod 一覧や Discord のボタンなど、どれも
 * Gui#setScreen で出されるので、ここ 1 か所で ConfirmLinkScreen を見つけて、画面を切り替える代わりにリンクを開く。
 * 今の画面はそのまま残る (確認画面の「戻る」で親の画面が作り直されることもない)。
 * OFF のとき、ConfirmLinkScreen 以外の画面のときは何もしない (instanceof だけ)。
 */
@Mixin(Gui.class)
public abstract class MixinGuiLinkConfirmation {
    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void neo$openLinkWithoutConfirmation(@Nullable Screen screen, CallbackInfo ci) {
        if (screen instanceof ConfirmLinkScreen link && ModManagerConfig.SKIP_COMMAND_CONFIRMATION.getValue()) {
            Blaze3D.openUri(((MixinConfirmLinkScreenAccessor) link).neo$getUrl());
            ci.cancel();
        }
    }
}
