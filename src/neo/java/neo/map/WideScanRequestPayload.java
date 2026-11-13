package neo.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント → サーバー: 広域スキャンを始める (中心と一辺のブロック数) / 止める。 */
public record WideScanRequestPayload(boolean start, int centerX, int centerZ, int spanBlocks) implements CustomPacketPayload {
    public static final Type<WideScanRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "wide_scan_request"));
    public static final StreamCodec<FriendlyByteBuf, WideScanRequestPayload> CODEC = CustomPacketPayload.codec(WideScanRequestPayload::write, WideScanRequestPayload::read);

    private static WideScanRequestPayload read(FriendlyByteBuf buf) {
        return new WideScanRequestPayload(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(start);
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
        buf.writeVarInt(spanBlocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
