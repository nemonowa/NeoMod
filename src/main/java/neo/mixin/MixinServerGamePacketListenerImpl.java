package neo.mixin;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import neo.map.WideMapScanServer;
import neo.map.WideScanRequestPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerPlayNetworking.registerGlobalReceiver の代替。ネットワークのスレッドで呼ばれるので、処理はサーバースレッドへ渡す。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void neo$handlePayload(ServerboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof WideScanRequestPayload request) {
            WideMapScanServer.onRequest(player, request);
            ci.cancel();
        }
    }
}
