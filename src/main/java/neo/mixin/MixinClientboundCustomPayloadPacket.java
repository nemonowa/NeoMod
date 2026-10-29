package neo.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import neo.spawnerinfo.SpawnerDataPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * PayloadTypeRegistry.clientboundPlay().register の代替。
 * GAMEPLAY_STREAM_CODEC を作る CustomPacketPayload.codec(...) の1回目の呼び出し(<clinit> 内)で、
 * 登録済みペイロード一覧に自作ペイロードを足す。サーバー/クライアント両方で必要なので共通 mixin に置く。
 */
@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class MixinClientboundCustomPayloadPacket {
    @ModifyArg(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            ordinal = 0
        ),
        index = 1
    , require = 1)
    private static List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> neo$registerPlayPayloads(
        List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> original
    ) {
        List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> list = new ArrayList<>(original);
        list.add(new CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, SpawnerDataPayload>(SpawnerDataPayload.TYPE, SpawnerDataPayload.CODEC));
        return list;
    }
}
