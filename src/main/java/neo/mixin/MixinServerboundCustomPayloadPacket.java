package neo.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import neo.map.StructureScanRequestPayload;
import neo.map.WideScanRequestPayload;
import neo.spawnerinfo.SpawnerSubscribePayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** PayloadTypeRegistry.serverboundPlay().register の代替。クライアント → サーバーの自作ペイロードを登録済みの一覧に足す。 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class MixinServerboundCustomPayloadPacket {
    @ModifyArg(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;",
            ordinal = 0
        ),
        index = 1
    , require = 1)
    private static List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> neo$registerPayloads(
        List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> original
    ) {
        List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> list = new ArrayList<>(original);
        list.add(new CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, WideScanRequestPayload>(WideScanRequestPayload.TYPE, WideScanRequestPayload.CODEC));
        list.add(new CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, StructureScanRequestPayload>(StructureScanRequestPayload.TYPE, StructureScanRequestPayload.CODEC));
        list.add(new CustomPacketPayload.TypeAndCodec<FriendlyByteBuf, SpawnerSubscribePayload>(SpawnerSubscribePayload.TYPE, SpawnerSubscribePayload.CODEC));
        return list;
    }
}
