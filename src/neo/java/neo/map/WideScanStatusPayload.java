package neo.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** サーバー → クライアント: 広域スキャンの状態 (使えること・進み具合・終わり方・ほかの人が実行中)。 */
public record WideScanStatusPayload(byte kind, int done, int total, int spanBlocks) implements CustomPacketPayload {
    public static final byte HELLO = 0;
    public static final byte PROGRESS = 1;
    public static final byte DONE = 2;
    public static final byte STOPPED = 3;
    public static final byte FAILED = 4;
    public static final byte BUSY = 5;

    public static final Type<WideScanStatusPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neo", "wide_scan_status"));
    public static final StreamCodec<FriendlyByteBuf, WideScanStatusPayload> CODEC = CustomPacketPayload.codec(WideScanStatusPayload::write, WideScanStatusPayload::read);

    private static WideScanStatusPayload read(FriendlyByteBuf buf) {
        return new WideScanStatusPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeVarInt(done);
        buf.writeVarInt(total);
        buf.writeVarInt(spanBlocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
