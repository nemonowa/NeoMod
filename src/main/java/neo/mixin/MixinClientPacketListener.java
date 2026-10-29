package neo.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import neo.spawnerinfo.SpawnerDataPayload;
import neo.spawnerinfo.SpawnerInfoClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ClientPlayNetworking.registerGlobalReceiver の代替。メインスレッド確保後に呼ばれる handleCustomPayload の先頭で受け取る。 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void neo$handlePayload(CustomPacketPayload payload, CallbackInfo ci) {
        if (payload instanceof SpawnerDataPayload data) {
            SpawnerInfoClient.onPayload(data);
            ci.cancel();
        }
    }
}
