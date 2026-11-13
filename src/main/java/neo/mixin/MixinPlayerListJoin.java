package neo.mixin;

import neo.map.WideMapScanServer;
import neo.mount.LegacyRide;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 参加したプレイヤーへ、このサーバーの機能の状態を送る (旧版の騎乗位置の ON/OFF、広域スキャンが使えること)。 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerListJoin {
    @Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V", at = @At("RETURN"), require = 1)
    private void neo$onJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        LegacyRide.sendTo(player);
        WideMapScanServer.sendHello(player);
    }
}
