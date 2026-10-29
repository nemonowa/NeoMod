package neo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import neo.menu.config.ModManagerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * クリックイベント (本・チャット・ダイアログ) の run_command で出る「Confirm Command Execution」を出さず、
 * 1.20.4 以前と同じくそのままコマンドを送信する (設定 skip_command_confirmation が ON のとき)。
 *
 * 26.3 の ClientPacketListener#sendUnattendedCommand は、コマンドを検証して結果ごとに分岐する。
 *   NO_ISSUES          : そのまま送信
 *   PARSE_ERRORS       : 確認ウィンドウ (openCommandSendConfirmationWindow)
 *   PERMISSIONS_REQUIRED: 同上。OP でも「権限が必要なコマンド」として毎回出る
 *   SIGNATURE_REQUIRED : 別の確認ウィンドウ (openSignedCommandSendConfirmationWindow) ← 触らない
 * ここでは前者2つの openCommandSendConfirmationWindow を、確認なしの送信に置き換える。
 * 署名が必要なコマンドは、本から「自分の名前でチャット発言」させられるのを防ぐ保護なので従来どおり確認を残す
 * (1.20.4 でも署名付き引数を含む場合は実行を拒否していた)。
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinUnattendedCommand {
    @WrapOperation(
        method = "sendUnattendedCommand(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;openCommandSendConfirmationWindow(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V"
        ),
        require = 2
    )
    private void neo$runWithoutConfirmation(ClientPacketListener self, String command, String messageKey, @Nullable Screen screenAfterCommand,
                                               Operation<Void> original) {
        if (!ModManagerConfig.SKIP_COMMAND_CONFIRMATION.getValue()) {
            original.call(self, command, messageKey, screenAfterCommand);
            return;
        }

        self.send(new ServerboundChatCommandPacket(command));
        Minecraft.getInstance().gui.setScreen(screenAfterCommand);
    }
}
