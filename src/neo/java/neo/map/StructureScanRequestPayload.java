package neo.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント → サーバー: マップを開いたので、周りの構造物を調べて送ってほしい (中身なし)。MOD の無いサーバーは読み捨てる。 */
public record StructureScanRequestPayload() implements CustomPacketPayload {
    public static final Type<StructureScanRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "structure_scan_request"));
    public static final StreamCodec<FriendlyByteBuf, StructureScanRequestPayload> CODEC =
        CustomPacketPayload.codec((payload, buf) -> {}, buf -> new StructureScanRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
